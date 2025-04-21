package org.maymichael.util;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.maymichael.data.BinaryData;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@Slf4j
@WritingConverter
public class BinaryDataToBytesConverter implements Converter<BinaryData, byte[]> {

    private final KryoRedisSerializer<BinaryData> kryoRedisSerializer;

    public BinaryDataToBytesConverter() {
        kryoRedisSerializer = new KryoRedisSerializer<>();
    }

    @Override
    @SneakyThrows
    public byte[] convert(@NonNull BinaryData value) {
        return kryoRedisSerializer.serialize(value);
    }
}
