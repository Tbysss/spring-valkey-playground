package org.maymichael.data;

import lombok.*;

import java.io.Serializable;
import java.util.Optional;

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
    private String id;

    private String stringData;

    private byte[] data;

    @ToString.Include
    public Integer dataByteSize() {
        return Optional.ofNullable(data).map(d -> d.length).orElse(0)
                + id.length()
                + Optional.ofNullable(stringData).map(String::length).orElse(0);
    }
}
