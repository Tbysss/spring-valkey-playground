package org.maymichael.config;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ReadFrom;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.maymichael.data.BinaryData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.PipelinedRedisKeyValueAdapter;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.core.RedisKeyValueTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.convert.RedisCustomConversions;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.*;
import org.springframework.util.StopWatch;
import org.xerial.snappy.Snappy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Configuration
@EnableRedisRepositories
@RequiredArgsConstructor
public class RedisConfig {

    @Value("${spring.data.redis.timeout}")
    private Duration redisCommandTimeout;


    private final RedisProperties redisProperties;

    @Bean
    protected LettuceConnectionFactory redisConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration()
                .master(redisProperties.getSentinel().getMaster());
        redisProperties.getSentinel().getNodes().forEach(s -> sentinelConfig.sentinel(s.split(":")[0], Integer.valueOf(s.split(":")[1])));
        sentinelConfig.setPassword(RedisPassword.of(redisProperties.getPassword()));

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(redisCommandTimeout).readFrom(ReadFrom.MASTER_PREFERRED).build();
        return new LettuceConnectionFactory(sentinelConfig, clientConfig);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
    }


    @Bean
    RedisTemplate<?, ?> redisTemplate(final RedisConnectionFactory redisConnectionFactory) {

        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        var mapper = objectMapper();

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.setDefaultSerializer(new GenericJackson2JsonRedisSerializer(mapper));

        return template;
    }

    @Bean
    public RedisCustomConversions redisCustomConversions() {
        return new RedisCustomConversions(Arrays.asList(
                new BinaryDataToBytesConverter(),
                new BytesToBinaryDataConverter()));
    }

    @WritingConverter
    public class BinaryDataToBytesConverter implements Converter<BinaryData, byte[]> {

        private final Jackson2JsonRedisSerializer<BinaryData> serializer;
        private final ObjectMapper objectMapper;
        private KryoRedisSerializer<BinaryData> kryoRedisSerializer;

        public BinaryDataToBytesConverter() {
            objectMapper = objectMapper();
            serializer = new Jackson2JsonRedisSerializer<>(objectMapper, BinaryData.class);
            kryoRedisSerializer = new KryoRedisSerializer<>();
        }

        @Override
        @SneakyThrows
        public byte[] convert(BinaryData value) {
            var sw = new StopWatch();
            var displaySize = FileUtils.byteCountToDisplaySize(value.getData() != null ? value.getData().length : 0);
            sw.start();
//            if (value.getData() != null) {
//                value.setDataWrapper(List.of(value.getData()));
//                value.setStringData(Base64.getEncoder().encodeToString(value.getData()));
//                value.setData(null);
//            }
//            var serialized = serializer.serialize(value);
//            var serialized = JacksonObjectWriter.create().write(objectMapper, value);
            var serialized = kryoRedisSerializer.serialize(value);
            sw.stop();
            log.trace("serialize: duration={}ms id={} dataSize=\"{}\"",
                    sw.getTotalTimeMillis(),
                    value.getId(),
                    displaySize);
            return serialized;
        }
    }

    static class KryoRedisSerializer<T> implements RedisSerializer<T> {

        private static final Pool<Kryo> kryoPool = new Pool<Kryo>(true, false, 8) {
            protected Kryo create() {
                Kryo kryo = new Kryo();
                // Configure the Kryo instance.
                kryo.setRegistrationRequired(true);
                kryo.register(BinaryData.class);
                kryo.register(byte[].class);
                kryo.register(String.class);
                return kryo;
            }
        };

        @Override
        public byte[] serialize(T value) throws SerializationException {
            if (value == null) {
                return null;
            }
            var kryo = kryoPool.obtain();
            try {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                Output output = new Output(stream);
                kryo.writeClassAndObject(output, value);
                output.close();
                return stream.toByteArray();
            } finally {
                kryoPool.free(kryo);
            }
        }

        @Override
        public T deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null)
                return null;

            var kryo = kryoPool.obtain();
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                try (var input = new Input(bais)) {
                    //noinspection unchecked
                    return (T) kryo.readClassAndObject(input);
                }
            } finally {
                kryoPool.free(kryo);
            }
        }
    }

    @ReadingConverter
    public class BytesToBinaryDataConverter implements Converter<byte[], BinaryData> {

        private final Jackson2JsonRedisSerializer<BinaryData> serializer;
        private final JavaType dataType;
        private final ObjectMapper objectMapper;
        private KryoRedisSerializer<BinaryData> kryoRedisSerializer;

        public BytesToBinaryDataConverter() {
            objectMapper = objectMapper();
            serializer = new Jackson2JsonRedisSerializer<>(objectMapper, BinaryData.class);
            dataType = objectMapper.constructType(BinaryData.class);
            kryoRedisSerializer = new KryoRedisSerializer<>();
        }

        @Override
        @SneakyThrows
        public BinaryData convert(byte @NonNull [] value) {
            var sw = new StopWatch();
            sw.start();
//            var deserialized = (BinaryData) JacksonObjectReader.create().read(objectMapper, value, dataType);
//            var deserialized = serializer.deserialize(value);
            var deserialized = kryoRedisSerializer.deserialize(value);
            if (deserialized == null) return null;
////            if (deserialized.getDataWrapper() != null && deserialized.getDataWrapper().size() == 1) {
////                deserialized.setData(deserialized.getDataWrapper().getFirst());
////            }
////            if (deserialized.getStringData() != null) {
////                deserialized.setData(Base64.getDecoder().decode(deserialized.getStringData()));
////                deserialized.setStringData(null);
////            }
            sw.stop();
            log.trace("deserialize: duration={}ms id={} dataSize=\"{}\"",
                    sw.getTotalTimeMillis(),
                    deserialized.getId(),
                    FileUtils.byteCountToDisplaySize(deserialized.getData() != null ? deserialized.getData().length : 0));
            return deserialized;
        }
    }

    @Bean
    public RedisKeyValueAdapter redisKeyValueAdapter(
            final RedisTemplate<?, ?> redisTemplate,
            final RedisMappingContext mappingContext) {
        return new PipelinedRedisKeyValueAdapter(redisTemplate, mappingContext, redisCustomConversions());
    }

    @Bean
    public RedisKeyValueTemplate redisKeyValueTemplate(
            final RedisTemplate<?, ?> redisTemplate, final RedisMappingContext mappingContext) {
        var adapter = redisKeyValueAdapter(redisTemplate, mappingContext);
        return new RedisKeyValueTemplate(adapter, mappingContext);
    }

}
