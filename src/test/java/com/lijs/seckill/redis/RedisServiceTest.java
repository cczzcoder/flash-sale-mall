package com.lijs.seckill.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

    private static final TestKey TTL_KEY = new TestKey(60, "t");
    private static final TestKey NO_TTL_KEY = new TestKey("n");

    @Mock
    private JedisPool jedisPool;
    @Mock
    private Jedis jedis;

    private RedisService redisService;

    @BeforeEach
    void setUp() {
        redisService = new RedisService();
        ReflectionTestUtils.setField(redisService, "jedisPool", jedisPool);
    }

    @Test
    void jedisIsClosedEvenWhenOperationThrows() {
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.get("TestKey:t1")).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class,
                () -> redisService.get(TTL_KEY, "1", String.class));

        verify(jedis).close();
    }

    @Test
    void setUsesSetexWhenPrefixHasTtl() {
        when(jedisPool.getResource()).thenReturn(jedis);

        assertTrue(redisService.set(TTL_KEY, "1", 20));

        verify(jedis).setex("TestKey:t1", 60, "20");
    }

    @Test
    void setUsesPlainSetWhenPrefixNeverExpires() {
        when(jedisPool.getResource()).thenReturn(jedis);

        assertTrue(redisService.set(NO_TTL_KEY, "1", "abc"));

        verify(jedis).set("TestKey:n1", "abc");
    }

    @Test
    void getDeserializesStoredValue() {
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.get("TestKey:t1")).thenReturn("5");

        assertEquals(5, redisService.get(TTL_KEY, "1", Integer.class).intValue());
    }

    @Test
    void setIfAbsentSetsExpireOnlyWhenCreated() {
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.setnx("TestKey:t1", "5")).thenReturn(1L);

        assertTrue(redisService.setIfAbsent(TTL_KEY, "1", 5));

        verify(jedis).expire("TestKey:t1", 60);
    }

    @Test
    void setIfAbsentSkipsExpireWhenKeyExists() {
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.setnx("TestKey:t1", "5")).thenReturn(0L);

        assertFalse(redisService.setIfAbsent(TTL_KEY, "1", 5));

        verify(jedis, never()).expire(anyString(), anyInt());
    }

    @Test
    void beanToStringAndStringToBeanRoundTrip() {
        assertEquals("5", RedisService.beanToString(5));
        assertEquals(Integer.valueOf(5), RedisService.stringToBean("5", Integer.class));
        assertEquals("abc", RedisService.beanToString("abc"));
        assertEquals("abc", RedisService.stringToBean("abc", String.class));

        TestBean bean = new TestBean();
        bean.setId(7);
        bean.setName("手机");
        String json = RedisService.beanToString(bean);
        TestBean restored = RedisService.stringToBean(json, TestBean.class);
        assertEquals(7, restored.getId());
        assertEquals("手机", restored.getName());
    }

    private static class TestKey extends BasePrefix {
        TestKey(int expireSeconds, String prefix) {
            super(expireSeconds, prefix);
        }

        TestKey(String prefix) {
            super(prefix);
        }
    }

    public static class TestBean {
        private int id;
        private String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
