package org.maymichael.util;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.maymichael.data.BinaryData;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
@Slf4j
public class BytesToBinaryDataConverter implements Converter<byte[], BinaryData> {

    private final KryoRedisSerializer<BinaryData> kryoRedisSerializer;

    public BytesToBinaryDataConverter() {
        kryoRedisSerializer = new KryoRedisSerializer<>();
    }

    @Override
    @SneakyThrows
    public BinaryData convert(byte @NonNull [] value) {
        return kryoRedisSerializer.deserialize(value);
    }
}