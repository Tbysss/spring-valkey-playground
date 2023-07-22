package org.springframework.data.redis.core;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.convert.*;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.lang.Nullable;

public class PipelinedRedisKeyValueAdapter extends RedisKeyValueAdapter {

    private final RedisConverter converter;

    public PipelinedRedisKeyValueAdapter(RedisTemplate<?, ?> redisTemplate, RedisMappingContext mappingContext,
                                         @Nullable org.springframework.data.convert.CustomConversions customConversions) {
        super(redisTemplate, mappingContext, customConversions);

        MappingRedisConverter mappingConverter = new MappingRedisConverter(mappingContext,
                new PathIndexResolver(mappingContext), new ReferenceResolverImpl(redisTemplate));
        mappingConverter
                .setCustomConversions(customConversions == null ? new RedisCustomConversions() : customConversions);
        mappingConverter.afterPropertiesSet();
        this.converter = mappingConverter;
    }

    /**
     * @return {@literal true} if {@link RedisData#getTimeToLive()} has a positive
     *         value.
     *
     * @param data must not be {@literal null}.
     * @since 2.3.7
     */
    private boolean expires(RedisData data) {
        return data.getTimeToLive() != null && data.getTimeToLive() > 0;
    }

    public Object putOnConnection(RedisConnection connection, Object id, Object item) {

        RedisData rdo = item instanceof RedisData ? (RedisData) item : new RedisData();
        if (!(item instanceof RedisData)) {
            converter.write(item, rdo);
        }

        if (rdo.getId() == null) {
            rdo.setId(converter.getConversionService().convert(id, String.class));
        }

        byte[] key = toBytes(rdo.getId());
        byte[] objectKey = createKey(rdo.getKeyspace(), rdo.getId());

        connection.hashCommands().hMSet(objectKey, rdo.getBucket().rawMap());

        connection.setCommands().sAdd(toBytes(rdo.getKeyspace()), key);

        if (expires(rdo)) {
            connection.keyCommands().expire(objectKey, rdo.getTimeToLive());
        }
        IndexWriter indexWriter = new IndexWriter(connection, converter);
        indexWriter.createIndexes(key, rdo.getIndexedData());

        return item;
    }

}