package com.lijs.seckill.controller;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.service.AiGuideService;
import com.lijs.seckill.vo.AiChatRequest;
import com.lijs.seckill.vo.AiChatResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpSession;

/**
 * AI 导购控制器。
 * <ul>
 *   <li>GET  /ai/guide        — 聊天页面</li>
 *   <li>POST /ai/stream       — 流式对话（SSE，打字机效果）</li>
 *   <li>POST /ai/chat         — 非流式对话（降级兜底）</li>
 *   <li>POST /ai/clear        — 重置对话历史</li>
 * </ul>
 */
@Controller
@RequestMapping("/ai")
public class AiGuideController {

    private static final Logger logger = LoggerFactory.getLogger(AiGuideController.class);

    @Autowired
    private AiGuideService aiGuideService;

    /** AI 导购聊天页面 */
    @GetMapping("/guide")
    public String guidePage() {
        return "ai_guide";
    }

    /**
     * 流式对话：返回 Server-Sent Events 流，前端逐字追加实现打字机效果。
     * SeckillUser 由 UserArgumentResolver 自动注入（未登录时为 null）。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter streamChat(@RequestBody AiChatRequest request,
                                 SeckillUser user,
                                 HttpSession session) {
        String message = request.getMessage();
        if (StringUtils.isBlank(message)) {
            // 空消息直接返回已完成的 emitter
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event().name("done").data("{}"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        String sessionId = session.getId();
        Long userId = user != null ? user.getId() : null;
        String lang = StringUtils.defaultIfBlank(request.getLang(), "zh");

        logger.info("AI导购[stream] session={} userId={} lang={} msg={}", sessionId, userId, lang, message);

        // 2 分钟超时，足够 Claude 完成长回复
        SseEmitter emitter = new SseEmitter(120_000L);
        aiGuideService.streamChat(sessionId, userId, message, lang, emitter);
        return emitter;
    }

    /**
     * 非流式对话（作为降级兜底，浏览器不支持 fetch streaming 时使用）。
     */
    @PostMapping("/chat")
    @ResponseBody
    public Result<AiChatResponse> chat(@RequestBody AiChatRequest request,
                                       SeckillUser user,
                                       HttpSession session) {
        String message = request.getMessage();
        if (StringUtils.isBlank(message)) {
            return Result.success(new AiChatResponse("请输入您想了解的内容 😊"));
        }

        String sessionId = session.getId();
        Long userId = user != null ? user.getId() : null;
        String lang = StringUtils.defaultIfBlank(request.getLang(), "zh");

        logger.info("AI导购[sync] session={} userId={} lang={}", sessionId, userId, lang);
        String reply = aiGuideService.chat(sessionId, userId, message, lang);
        return Result.success(new AiChatResponse(reply));
    }

    /** 清除当前 session 的对话历史 */
    @PostMapping("/clear")
    @ResponseBody
    public Result<String> clearSession(HttpSession session) {
        aiGuideService.clearSession(session.getId());
        return Result.success("ok");
    }
}
