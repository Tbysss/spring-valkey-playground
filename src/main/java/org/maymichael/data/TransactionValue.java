package org.maymichael.data;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.util.UUID;

@Data
@Builder
@RedisHash("value")
public class TransactionValue {

    @Builder.Default
    @Id
    private String id = UUID.randomUUID().toString();

    @Indexed
    private String tid;

    @Indexed
    private String something;

    private BinaryData binaryData;
}
