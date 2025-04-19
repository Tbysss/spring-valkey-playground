package org.maymichael.config;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
        ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(60L)))
                .topologyRefreshOptions(ClusterTopologyRefreshOptions.builder()
                        .enablePeriodicRefresh(Duration.ofSeconds(60L)) // Refresh the topology periodically.
                        .enableAllAdaptiveRefreshTriggers() // Refresh the topology based on events.
                        .build())
                .build();

        RedisClusterConfiguration config = new RedisClusterConfiguration();
        redisProperties.getCluster().getNodes().forEach(s -> config.addClusterNode(RedisNode.fromString(s)));
        config.setMaxRedirects(redisProperties.getCluster().getMaxRedirects());
        config.setPassword(RedisPassword.of(redisProperties.getPassword()));

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(redisCommandTimeout)
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .clientOptions(clusterClientOptions)
                .build();
        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
    }

    private void configureRedisTemplate(RedisTemplate<?, ?> template, final RedisConnectionFactory redisConnectionFactory) {
        template.setConnectionFactory(redisConnectionFactory);
        var mapper = objectMapper();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.setDefaultSerializer(new GenericJackson2JsonRedisSerializer(mapper));
    }

    @Bean
    public RedisTemplate<?, ?> redisTemplate(final RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        configureRedisTemplate(template, redisConnectionFactory);
        return template;
    }


    @Bean
    public RedisCustomConversions redisCustomConversions() {
        return new RedisCustomConversions(Arrays.asList(
                new BinaryDataToBytesConverter(),
                new BytesToBinaryDataConverter()));
    }

    @WritingConverter
    public static class BinaryDataToBytesConverter implements Converter<BinaryData, byte[]> {

        private final KryoRedisSerializer<BinaryData> kryoRedisSerializer;

        public BinaryDataToBytesConverter() {
            kryoRedisSerializer = new KryoRedisSerializer<>();
        }

        @Override
        @SneakyThrows
        public byte[] convert(BinaryData value) {
            var sw = new StopWatch();
            var displaySize = FileUtils.byteCountToDisplaySize(value.getData() != null ? value.getData().length : 0);
            sw.start();
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
        private final KryoRedisSerializer<BinaryData> kryoRedisSerializer;

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
            var deserialized = kryoRedisSerializer.deserialize(value);
            if (deserialized == null) return null;
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
