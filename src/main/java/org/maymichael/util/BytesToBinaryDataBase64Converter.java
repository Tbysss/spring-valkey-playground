package org.maymichael.util;

import com.esotericsoftware.kryo.util.ObjectMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.maymichael.data.BinaryData;
import org.maymichael.data.BinaryDataBase64;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@ReadingConverter
@Slf4j
public class BytesToBinaryDataBase64Converter implements Converter<byte[], BinaryDataBase64> {

    private final RedisSerializer<BinaryDataBase64> serializer;

    public BytesToBinaryDataBase64Converter(final ObjectMapper objectMapper) {
        serializer = new Jackson2JsonRedisSerializer<>(objectMapper, BinaryDataBase64.class);
    }

    @Override
    @SneakyThrows
    public BinaryDataBase64 convert(byte @NonNull [] value) {
        return serializer.deserialize(value);
    }
}