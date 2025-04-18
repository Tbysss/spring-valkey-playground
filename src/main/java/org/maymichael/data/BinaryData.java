package org.maymichael.data;

import lombok.*;
import org.springframework.data.annotation.Transient;

import java.io.Serializable;
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

    // transient tells spring to not look at this while indexing and other stuff
    // if we use a custom serializer, we can still serialize/deserialize this data to redis
    // not loosing it
    // Main Problem lies with @org.springframework.data.redis.core.convert.PathIndexResolver
    // if the data type is isCollectionLike(), then it will iterate through each byte in the array
    // this maes no sense, since no single byte is an entity (maybe a bug?)
    // by making this field @Transient, it will not get added to persistentPropertiesCache in @org.springframework.data.mapping.model.BasicPersistentEntity
    @Transient
    private byte[] data;

    @ToString.Include
    public Integer dataByteSize() {
        return Optional.ofNullable(data).map(s -> s.length).orElse(0)
                + id.length();
    }
}
