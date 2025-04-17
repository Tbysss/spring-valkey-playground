package org.maymichael.services;

import lombok.extern.slf4j.Slf4j;
import org.maymichael.data.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentEntity;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
public class DataService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    TransactionValueRepository transactionValueRepository;
    @Autowired
    TransactionKeyRepository transactionKeyRepository;

    @Autowired
    private RedisKeyValueAdapter redisKeyValueAdapter;

    @Autowired
    private RedisMappingContext redisMappingContext;

    private String resolveKeySpace(Class<?> type) {
        return redisMappingContext.getRequiredPersistentEntity(type).getKeySpace();
    }

    private String buildRedisQuery(Class<?> clazz, String query) {
        return resolveKeySpace(clazz) + ":" + query;
    }

    public void testMassGet() {
        var t = Transaction.builder().build();
        transactionRepository.save(t);
        for (int i = 0; i < 10; i++) {
            var something = i % 2 == 0 ? "even" : "odd";
            var tv = TransactionValue.builder()
                    .tid(t.getId())
                    .something(something)
                    .build();
            transactionValueRepository.save(tv);
        }
        for(int i = 0; i < 5; i++){
            transactionKeyRepository.save(TransactionKey.builder()
                    .tid(t.getId())
                    .build());
        }

        var results = stringRedisTemplate.executePipelined((RedisCallback<?>) con -> {
            con.setCommands().sMembers(buildRedisQuery(TransactionValue.class, "tid:" + t.getId()).getBytes(StandardCharsets.UTF_8));
            con.setCommands().sMembers(buildRedisQuery(TransactionKey.class, "tid:" + t.getId()).getBytes(StandardCharsets.UTF_8));
            return null;
        });
        assert results.size() == 2;
        var valuesForTid = (Set<String>)results.get(0);
        var keysForTid = (Set<String>)results.get(1);

        log.info("results={} values={} keys={}", results.size(), valuesForTid.size(), keysForTid.size());

    }
}
