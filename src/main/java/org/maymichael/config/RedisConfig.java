package org.maymichael.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.Delay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.maymichael.util.BinaryDataToBytesConverter;
import org.maymichael.util.BytesToBinaryDataConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableRedisRepositories
@RequiredArgsConstructor
public class RedisConfig {

    @Value("${spring.data.redis.timeout}")
    private Duration redisCommandTimeout;

    private final RedisProperties redisProperties;

    static class DynamicClusterTimeout extends TimeoutOptions.TimeoutSource {
        private static final Set<ProtocolKeyword> META_COMMAND_TYPES = ImmutableSet.<ProtocolKeyword>builder()
                .add(CommandType.FLUSHDB)
                .add(CommandType.FLUSHALL)
                .add(CommandType.CLUSTER)
                .add(CommandType.INFO)
                .add(CommandType.KEYS)
                .build();

        private final Duration defaultCommandTimeout;
        private final Duration metaCommandTimeout;

        DynamicClusterTimeout(Duration defaultCommandTimeout, Duration metaCommandTimeout){
            this.defaultCommandTimeout = defaultCommandTimeout;
            this.metaCommandTimeout = metaCommandTimeout;
        }

        @Override
        public long getTimeout(RedisCommand<?, ?, ?> command) {
            if(META_COMMAND_TYPES.contains(command.getType()))
                return metaCommandTimeout.toMillis();
            return defaultCommandTimeout.toMillis();
        }
    }

    private static Duration min(Duration a, Duration b){
        return Duration.of(Math.min(a.toMillis(), b.toMillis()), ChronoUnit.MILLIS);
    }

    @Bean
    protected LettuceConnectionFactory redisConnectionFactory() {

        // socket connect timeout should be lower than command timeout for lettuce
        var socketOptions = SocketOptions.builder()
                .connectTimeout(min(redisCommandTimeout.dividedBy(2), Duration.ofMillis(500)))
                .build();

        // total time cluster will be unavailable in failover scenario:
        // cant really caluclate it, sometime it takes a few seconds, sometimes up to 30s
        // doesnt really matter what we set here
        ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
                // use dynamic timeout for cluster management commands
                .timeoutOptions(TimeoutOptions.builder()
                        .timeoutCommands(true)
                        .timeoutSource(new DynamicClusterTimeout(redisCommandTimeout, redisCommandTimeout.plus(Duration.ofSeconds(1))))
                        .build()
                )
                // timeout for cluster operations??? where is it used?
                .topologyRefreshOptions(ClusterTopologyRefreshOptions.builder()
                        // note: if a node disconnects (because the server died), we need to refresh the topology
                        // only then will the buffered commands be resend to the failed-over node
                        // either by the periodic refresh, or with adaptive triggers
                        .enablePeriodicRefresh(Duration.ofSeconds(60L)) // Refresh the topology periodically.
                        .enableAllAdaptiveRefreshTriggers() // Refresh the topology based on events.
                        // how often we do a refresh, during events
                        // this is kind of strange...since events may get lost?
                        // we always want to refresh on PERSISTENT_RECONNECTS
                        // but if this falls in between this timeout, will the refresh just get lost, until a new
                        // trigger arrives??
                        // just set it relatively low for now
                        .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(5L))
                        .refreshTriggersReconnectAttempts(0) // we want to change nodes immediately on redirects
                        .build())
                .socketOptions(socketOptions)
                .build();

        RedisClusterConfiguration config = new RedisClusterConfiguration();
        redisProperties.getCluster().getNodes().forEach(s -> config.addClusterNode(RedisNode.fromString(s)));
        config.setMaxRedirects(redisProperties.getCluster().getMaxRedirects());
        config.setPassword(RedisPassword.of(redisProperties.getPassword()));

        var clientResources = ClientResources.builder()
                // how often we try to reconnect when any node fails
                .reconnectDelay(Delay.exponential(0, 180, TimeUnit.SECONDS, 2))
                // may need to add a custom address resolver for TTL?
                // also in a docker swarm setup, this may be affected by its TTL, since by default
                // the DNSNameResolveBuilder respects the TTL the server sends
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                // this timeout affects all redis commands issued
                // this is mainly used to when retrieving results, as we wait this time max for the result to arrive
                .commandTimeout(redisCommandTimeout)
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .clientResources(clientResources)
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
