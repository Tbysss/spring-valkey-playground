package org.maymichael.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.maymichael.data.BinaryData;
import org.maymichael.data.BinaryDataBase64;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Slf4j
@WritingConverter
public class BinaryDataBase64ToBytesConverter implements Converter<BinaryDataBase64, byte[]> {

    private final RedisSerializer<BinaryDataBase64> serializer;

    public BinaryDataBase64ToBytesConverter(final ObjectMapper objectMapper) {
        serializer = new Jackson2JsonRedisSerializer<>(objectMapper, BinaryDataBase64.class);
    }

    @Override
    @SneakyThrows
    public byte[] convert(@NonNull BinaryDataBase64 value) {
        return serializer.serialize(value);
    }
}
