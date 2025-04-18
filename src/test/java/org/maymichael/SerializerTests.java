package org.maymichael;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.maymichael.config.RedisConfig;
import org.maymichael.data.BinaryData;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.util.StopWatch;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class SerializerTests {

    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
    }

    @Test
    void testBinaryDataSerialize() {
        var mapper = objectMapper();

        var bigData = BinaryData.builder().data(RandomStringUtils.insecure().nextAlphanumeric(10_000_000).getBytes(StandardCharsets.UTF_8)).build();
        var serializer = new Jackson2JsonRedisSerializer<>(mapper, BinaryData.class);

        StopWatch sw = new StopWatch();
        sw.start("serialize");
        var serialized = serializer.serialize(bigData);
        sw.stop();
        log.info("serialize done - duration_ns={}ns duration_ms={}ms", sw.lastTaskInfo().getTimeNanos(), sw.lastTaskInfo().getTimeMillis());
        sw.start("deserialize");
        var deserialized = serializer.deserialize(serialized);
        sw.stop();
        log.info("deserialize done - duration_ns={}ns duration_ms={}ms", sw.lastTaskInfo().getTimeNanos(), sw.lastTaskInfo().getTimeMillis());

        Assertions.assertNotNull(deserialized);
        Assertions.assertNotNull(deserialized.getData());
        Assertions.assertArrayEquals(bigData.getData(), deserialized.getData());
    }
}
