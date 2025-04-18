package org.maymichael.data;

import lombok.*;
import org.springframework.data.annotation.Reference;
import org.springframework.data.annotation.Transient;
import org.springframework.data.redis.core.convert.RedisData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BinaryData implements Serializable {

    @ToString.Include
    @NonNull
    @EqualsAndHashCode.Include
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    // transient tells spring data redis to not look at this while indexing and other stuff
    // if we use a custom serializer, we can still serialize/deserialize this data to redis
    // not loosing it
    @Transient
    private byte[] data;


    @ToString.Include
    public Integer dataByteSize() {
        return Optional.ofNullable(data).map(s -> s.length).orElse(0)
                + id.length();
    }
}
