package org.maymichael.data;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.util.Set;
import java.util.UUID;

@RedisHash("transaction")
@Data
@Builder
public class Transaction {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

}
