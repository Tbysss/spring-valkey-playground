package org.maymichael.data;

import lombok.*;
import org.springframework.data.annotation.Transient;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BinaryDataBase64 {
    @ToString.Include
    @NonNull
    @EqualsAndHashCode.Include
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    // mark this as transient so we dont iterate over each byte
    // but use a custom converter to serialize it to json (base64)
    @Transient
    private byte[] data;
}
