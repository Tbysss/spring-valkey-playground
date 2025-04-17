package org.maymichael.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.maymichael.data.BinaryData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.PipelinedRedisKeyValueAdapter;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.core.RedisKeyValueTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.convert.RedisCustomConversions;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Configuration
@EnableRedisRepositories
public class RedisConfig {

    private @Value("${demo.redis.commandTimeout:6000ms}") Duration redisCommandTimeout;

    private @Value("${demo.redis.socketTimeout:400ms}") Duration socketTimeout;

    @Bean
    RedisConfiguration redisConfiguration(final RedisProperties props) {
        var config = new RedisStandaloneConfiguration(props.getHost(), props.getPort());
        config.setPassword(RedisPassword.of(props.getPassword()));
        config.setDatabase(props.getDatabase());
        return config;
    }

    private LettuceConnectionFactory lettuceConnectionFactory(final RedisConfiguration serverConfig) {
        final SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(socketTimeout).build();
        final ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions).build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(redisCommandTimeout)
                .clientOptions(clientOptions)
                .build();

        final LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(serverConfig,
                clientConfig);
        lettuceConnectionFactory.setValidateConnection(true);
        lettuceConnectionFactory.setConvertPipelineAndTxResults(false);
        lettuceConnectionFactory.setEagerInitialization(true);
        lettuceConnectionFactory.setDatabase(serverConfig.getDatabaseOrElse(() -> 0));
        return lettuceConnectionFactory;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory(final RedisConfiguration serverConfig) {
        return lettuceConnectionFactory(serverConfig);
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
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
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

        public BinaryDataToBytesConverter() {
            serializer = new Jackson2JsonRedisSerializer<>(objectMapper(), BinaryData.class);
        }

        @Override
        @SneakyThrows
        public byte[] convert(BinaryData value) {
            if (value.getData() != null) {
                var serialized = value.getData();
                value.setStringData(Base64.getEncoder().encodeToString(serialized));
                value.setData(null);
            }
            return serializer.serialize(value);
        }
    }

    @ReadingConverter
    public class BytesToBinaryDataConverter implements Converter<byte[], BinaryData> {

        private final Jackson2JsonRedisSerializer<BinaryData> serializer;

        public BytesToBinaryDataConverter() {
            serializer = new Jackson2JsonRedisSerializer<>(objectMapper(), BinaryData.class);
        }

        @Override
        @SneakyThrows
        public BinaryData convert(byte[] value) {
            var img = (BinaryData) serializer.deserialize(value);
            if (img != null && img.getStringData() != null) {
                var uncompressed = Base64.getDecoder().decode(img.getStringData());
                img.setData(uncompressed);
                img.setStringData(null);
            }
            return img;
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
