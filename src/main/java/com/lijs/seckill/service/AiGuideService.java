package com.lijs.seckill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lijs.seckill.redis.AiHistoryKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.vo.GoodsVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * AI 导购服务。
 * <p>支持：
 * <ul>
 *   <li>流式输出（SSE / 打字机效果）</li>
 *   <li>登录用户个性化推荐（注入历史购买记录）</li>
 *   <li>多语言回复（zh / en / ja）</li>
 * </ul>
 */
@Service
public class AiGuideService {

    private static final Logger logger = LoggerFactory.getLogger(AiGuideService.class);
    /** 每 session 最多保留的消息条数 */
    private static final int MAX_HISTORY = 20;
    /** 个性化推荐时查询的近期订单数 */
    private static final int ORDER_HISTORY_LIMIT = 5;

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${claude.api.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${claude.api.maxTokens:1024}")
    private int maxTokens;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private RedisService redisService;

    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 流式请求使用有界线程池，防止并发 AI 请求打爆 JVM */
    private final ExecutorService streamExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(), // corePoolSize = CPU 核数
            50,                                          // maxPoolSize
            60L, TimeUnit.SECONDS,                       // 空闲线程存活时间
            new LinkedBlockingQueue<>(200),              // 有界队列，最多积压 200 个请求
            new ThreadPoolExecutor.AbortPolicy()         // 队列满时拒绝，由调用方降级处理
    );

    /** 与流式路径 openConnection() 的超时对齐（连接 10s / 读取 120s），避免请求挂死。 */
    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    /** 应用停机时关闭线程池，避免非守护线程阻塞 JVM 退出。 */
    @PreDestroy
    public void shutdownStreamExecutor() {
        streamExecutor.shutdown();
    }

    // -------------------------------------------------------------------------
    // 普通（非流式）对话
    // -------------------------------------------------------------------------

    /**
     * 发送消息，同步返回完整回复文本。
     *
     * @param sessionId 会话 ID
     * @param userId    登录用户 ID（未登录传 null）
     * @param message   用户消息
     * @param lang      语言代码：zh / en / ja
     */
    public String chat(String sessionId, Long userId, String message, String lang) {
        appendMessage(sessionId, "user", message);
        List<Map<String, String>> history = getHistory(sessionId);

        try {
            String reply = callClaudeSync(history, userId, lang);
            appendMessage(sessionId, "assistant", reply);
            return reply;
        } catch (Exception e) {
            logger.error("调用 Claude API 失败: {}", e.getMessage(), e);
            rollbackLastMessage(sessionId); // 撤销刚追加的 user 消息
            return localizedError(lang);
        }
    }

    // -------------------------------------------------------------------------
    // 流式对话（打字机效果）
    // -------------------------------------------------------------------------

    /**
     * 流式对话：向 emitter 逐块推送 AI 回复，最终发送 done 事件。
     * 方法立即返回，真正的 HTTP 流在后台线程中执行。
     *
     * @param sessionId 会话 ID
     * @param userId    登录用户 ID（未登录传 null）
     * @param message   用户消息
     * @param lang      语言代码
     * @param emitter   Spring SseEmitter
     */
    public void streamChat(String sessionId, Long userId, String message, String lang, SseEmitter emitter) {
        appendMessage(sessionId, "user", message);
        List<Map<String, String>> history = getHistory(sessionId);

        try {
            streamExecutor.execute(() -> doStreamChat(sessionId, history, userId, lang, emitter));
        } catch (RejectedExecutionException e) {
            // 线程池已满，降级处理：回滚用户消息，通知前端 AI 繁忙
            rollbackLastMessage(sessionId);
            logger.warn("AI 导购线程池已满，请求被拒绝: {}", e.getMessage());
            sendSseError(emitter, localizedError(lang) + "（服务繁忙，请稍后重试）", e);
        }
    }

    /** 后台线程执行：向 Claude 发起 SSE 请求，逐块转发给前端，流结束后写入 assistant 历史。 */
    private void doStreamChat(String sessionId, List<Map<String, String>> history,
                              Long userId, String lang, SseEmitter emitter) {
        // 收集本次完整回复，流结束后存入历史
        StringBuilder fullReply = new StringBuilder();
        HttpURLConnection conn = null;
        try {
            conn = openConnection();
            writeRequestBody(conn, history, userId, lang);

            // 读取 Claude SSE 流
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;

                    String text = extractTextDelta(data);
                    if (text == null) continue;

                    fullReply.append(text);
                    // 序列化为 JSON 后推送给前端
                    emitter.send(SseEmitter.event()
                            .data(objectMapper.writeValueAsString(Collections.singletonMap("text", text))));
                }
            }

            // 流结束：写入 assistant 回复
            appendMessage(sessionId, "assistant", fullReply.toString());
            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();

        } catch (Exception e) {
            logger.error("流式调用 Claude API 失败: {}", e.getMessage(), e);
            rollbackLastMessage(sessionId); // 撤销刚追加的 user 消息
            sendSseError(emitter, localizedError(lang), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 发送 error 事件后关闭 emitter；发送失败则携带异常关闭。 */
    private void sendSseError(SseEmitter emitter, String message, Exception cause) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data("{\"msg\":\"" + message + "\"}"));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.completeWithError(cause);
        }
    }

    // -------------------------------------------------------------------------
    // Session 管理
    // -------------------------------------------------------------------------

    public void clearSession(String sessionId) {
        redisService.delete(AiHistoryKey.history, sessionId);
    }

    // -------------------------------------------------------------------------
    // 私有：HTTP 调用
    // -------------------------------------------------------------------------

    private String callClaudeSync(List<Map<String, String>> history, Long userId, String lang) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = buildRequestBody(history, userId, lang, false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        // URI 重载避免 @NonNull String 警告
        org.springframework.core.ParameterizedTypeReference<Map<String, Object>> typeRef =
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {};
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(Objects.requireNonNull(java.net.URI.create(apiUrl)), HttpMethod.POST, entity, typeRef);

        Map<String, Object> rb = response.getBody();
        if (rb == null) throw new IllegalStateException("Claude API 返回空响应体");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contentList = (List<Map<String, Object>>) rb.get("content");
        return (String) contentList.get(0).get("text");
    }

    private HttpURLConnection openConnection() throws Exception {
        URL url = java.net.URI.create(apiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setRequestProperty("Accept", "text/event-stream");
        return conn;
    }

    /**
     * 构建 Claude /v1/messages 请求体；stream=true 时要求 SSE 流式返回。
     * 同步与流式两条路径共用，避免字段漂移。
     */
    private Map<String, Object> buildRequestBody(List<Map<String, String>> history,
                                                 Long userId, String lang, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", buildSystemPrompt(userId, lang));
        body.put("messages", history);
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private void writeRequestBody(HttpURLConnection conn,
                                  List<Map<String, String>> history,
                                  Long userId, String lang) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(buildRequestBody(history, userId, lang, true));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
    }

    // -------------------------------------------------------------------------
    // 私有：系统提示词构建
    // -------------------------------------------------------------------------

    private String buildSystemPrompt(Long userId, String lang) {
        StringBuilder sb = new StringBuilder();

        // --- 角色定义 ---
        sb.append("你是一位专业、热情的 AI 导购助手，服务于一个高并发秒杀商城。\n");
        sb.append("职责：\n");
        sb.append("1. 根据用户需求和预算推荐合适的秒杀商品\n");
        sb.append("2. 解答商品规格、秒杀规则、下单流程等问题\n");
        sb.append("3. 提醒用户关注秒杀开抢时间\n");
        sb.append("4. 回复简洁友好，可适当使用 emoji\n\n");

        // --- 当前商品上下文 ---
        sb.append("【当前秒杀商品】\n");
        sb.append("当前时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("\n");
        try {
            List<GoodsVo> list = goodsService.getGoodsVoList();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
            for (GoodsVo g : list) {
                sb.append(String.format("- 【%s】原价 ¥%.2f，秒杀价 ¥%.2f，剩余 %d 件",
                        g.getGoodsName(), g.getGoodsPrice(), g.getSeckillPrice(), g.getStockCount()));
                if (g.getStartDate() != null && g.getEndDate() != null) {
                    sb.append(String.format("，秒杀时间 %s ~ %s",
                            sdf.format(g.getStartDate()), sdf.format(g.getEndDate())));
                }
                sb.append("，状态：").append(activityStatusText(g.getActivityStatus()));
                sb.append("\n");
            }
            sb.append("推荐时只考虑状态为【进行中】的商品；未开始或已结束的商品不要推荐，用户主动问起时如实说明状态。\n");
        } catch (Exception e) {
            logger.warn("获取商品列表失败", e);
            sb.append("（商品信息暂时不可用）\n");
        }

        // --- 个性化：注入用户购买历史 ---
        if (userId != null) {
            try {
                List<OrderInfo> orders = orderDao.selectRecentByUserId(userId, ORDER_HISTORY_LIMIT);
                if (orders != null && !orders.isEmpty()) {
                    sb.append("\n【该用户近期购买记录（用于个性化推荐）】\n");
                    SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");
                    for (OrderInfo o : orders) {
                        sb.append(String.format("- %s 购买了【%s】，成交价 ¥%.2f\n",
                                o.getCreateDate() != null ? sdf.format(o.getCreateDate()) : "近期",
                                o.getGoodsName(), o.getGoodsPrice()));
                    }
                    sb.append("请根据以上偏好，优先推荐同类或互补商品。\n");
                }
            } catch (Exception e) {
                logger.warn("查询用户订单历史失败 userId={}", userId, e);
            }
        }

        // --- 多语言指令 ---
        sb.append(buildLangInstruction(lang));
        return sb.toString();
    }

    /** GoodsVo.getActivityStatus() 的展示文案：0=未开始，1=进行中，2=已结束。 */
    private static String activityStatusText(int status) {
        if (status == 0) return "未开始";
        if (status == 1) return "进行中";
        return "已结束";
    }

    private static String buildLangInstruction(String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            return "\n\nIMPORTANT: You must reply in English only, regardless of what language the user writes in.";
        }
        if ("ja".equalsIgnoreCase(lang)) {
            return "\n\n重要：ユーザーがどの言語で書いても、必ず日本語のみで返答してください。";
        }
        // 默认中文
        return "\n\n请始终用中文回复。";
    }

    // -------------------------------------------------------------------------
    // 私有：工具方法
    // -------------------------------------------------------------------------

    /**
     * 从 Claude SSE data JSON 字符串中提取 text_delta 的文本。
     * 只关心 content_block_delta 事件。
     */
    private String extractTextDelta(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            if (!"content_block_delta".equals(root.path("type").asText())) return null;
            JsonNode delta = root.path("delta");
            if (!"text_delta".equals(delta.path("type").asText())) return null;
            return delta.path("text").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String localizedError(String lang) {
        if ("en".equalsIgnoreCase(lang)) return "Sorry, the AI assistant is temporarily unavailable. Please try again later.";
        if ("ja".equalsIgnoreCase(lang)) return "申し訳ありませんが、AIアシスタントは一時的に利用できません。後でもう一度お試しください。";
        return "抱歉，AI 导购暂时无法响应，请稍后再试 🙏";
    }

    // -------------------------------------------------------------------------
    // Session 管理（Redis List，支持多实例 + 自动 TTL 清理）
    // -------------------------------------------------------------------------

    /**
     * 从 Redis 读取对话历史，反序列化为消息列表（传给 Claude API）。
     */
    private List<Map<String, String>> getHistory(String sessionId) {
        List<String> rawList = redisService.listRange(AiHistoryKey.history, sessionId);
        List<Map<String, String>> history = new ArrayList<>(rawList.size());
        for (String raw : rawList) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> msg = objectMapper.readValue(raw, Map.class);
                history.add(msg);
            } catch (Exception e) {
                logger.warn("反序列化对话历史条目失败，跳过: {}", raw, e);
            }
        }
        return history;
    }

    /**
     * 序列化一条消息并追加到 Redis List，同时修剪超出 MAX_HISTORY 的旧条目，刷新 TTL。
     */
    private void appendMessage(String sessionId, String role, String content) {
        try {
            Map<String, String> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            String json = objectMapper.writeValueAsString(msg);
            redisService.listAppend(AiHistoryKey.history, sessionId, json,
                    MAX_HISTORY, AiHistoryKey.history.expireSeconds());
        } catch (Exception e) {
            logger.error("追加对话历史失败 sessionId={}", sessionId, e);
        }
    }

    /**
     * 回滚最近追加的一条消息（RPOP），用于 API 调用失败时撤销刚写入的 user 消息。
     */
    private void rollbackLastMessage(String sessionId) {
        redisService.listRpop(AiHistoryKey.history, sessionId);
    }
}
