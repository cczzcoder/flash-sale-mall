package com.lijs.seckill.service;

import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.redis.AiHistoryKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.vo.GoodsVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGuideServiceTest {

    private static final String SESSION_ID = "session-1";

    @Mock
    private GoodsService goodsService;
    @Mock
    private OrderDao orderDao;
    @Mock
    private RedisService redisService;
    @Mock
    private RestTemplate restTemplate;

    private AiGuideService service;

    @BeforeEach
    void setUp() {
        service = new AiGuideService();
        ReflectionTestUtils.setField(service, "goodsService", goodsService);
        ReflectionTestUtils.setField(service, "orderDao", orderDao);
        ReflectionTestUtils.setField(service, "redisService", redisService);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "apiUrl", "http://localhost/claude");
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "maxTokens", 128);
    }

    @Test
    void chatReturnsReplyAndStoresAssistantHistoryOnSuccess() {
        when(redisService.listRange(AiHistoryKey.history, SESSION_ID))
                .thenReturn(Collections.singletonList("{\"role\":\"user\",\"content\":\"你好\"}"));
        when(goodsService.getGoodsVoList()).thenReturn(Collections.emptyList());
        stubClaudeReply("这是为您推荐的商品");

        String reply = service.chat(SESSION_ID, null, "推荐商品", "zh");

        assertEquals("这是为您推荐的商品", reply);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService, times(2))
                .listAppend(eq(AiHistoryKey.history), eq(SESSION_ID), jsonCaptor.capture(), anyInt(), anyInt());
        List<String> stored = jsonCaptor.getAllValues();
        assertTrue(stored.get(0).contains("推荐商品"));
        assertTrue(stored.get(1).contains("assistant"));
        assertTrue(stored.get(1).contains("这是为您推荐的商品"));
        verify(redisService, never()).listRpop(any(), anyString());
    }

    @Test
    void chatReturnsLocalizedErrorAndRollsBackWhenApiFails() {
        when(redisService.listRange(AiHistoryKey.history, SESSION_ID)).thenReturn(Collections.emptyList());
        when(goodsService.getGoodsVoList()).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("boom")).when(restTemplate)
                .exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class),
                        ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any());

        String reply = service.chat(SESSION_ID, null, "你好", "zh");

        assertEquals("抱歉，AI 导购暂时无法响应，请稍后再试 🙏", reply);
        verify(redisService).listRpop(AiHistoryKey.history, SESSION_ID);
    }

    @Test
    void buildSystemPromptInjectsGoodsOrderHistoryAndLangInstruction() {
        GoodsVo goods = new GoodsVo();
        goods.setGoodsName("iPhone 17");
        goods.setGoodsPrice(9999.0);
        goods.setSeckillPrice(4999.0);
        goods.setStockCount(5);
        goods.setStartDate(new Date());
        goods.setEndDate(new Date());
        when(goodsService.getGoodsVoList()).thenReturn(Collections.singletonList(goods));

        OrderInfo order = new OrderInfo();
        order.setGoodsName("AirPods");
        order.setGoodsPrice(1299.0);
        order.setCreateDate(new Date());
        when(orderDao.selectRecentByUserId(10001L, 5)).thenReturn(Collections.singletonList(order));

        String zhPrompt = ReflectionTestUtils.invokeMethod(service, "buildSystemPrompt", 10001L, "zh");
        assertTrue(zhPrompt.contains("iPhone 17"));
        assertTrue(zhPrompt.contains("AirPods"));
        assertTrue(zhPrompt.contains("请始终用中文回复。"));
        assertTrue(zhPrompt.contains("当前时间："));
        assertTrue(zhPrompt.contains("状态：已结束"));   // start == end 属非法窗口，按已结束处理
        assertTrue(zhPrompt.contains("只考虑状态为【进行中】的商品"));

        String enPrompt = ReflectionTestUtils.invokeMethod(service, "buildSystemPrompt", 10001L, "en");
        assertTrue(enPrompt.contains("English only"));

        String jaPrompt = ReflectionTestUtils.invokeMethod(service, "buildSystemPrompt", 10001L, "ja");
        assertTrue(jaPrompt.contains("日本語のみで返答"));
    }

    private void stubClaudeReply(String text) {
        Map<String, Object> content = new HashMap<>();
        content.put("text", text);
        Map<String, Object> body = new HashMap<>();
        body.put("content", Collections.singletonList(content));
        doReturn(ResponseEntity.ok(body)).when(restTemplate)
                .exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class),
                        ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any());
    }
}
