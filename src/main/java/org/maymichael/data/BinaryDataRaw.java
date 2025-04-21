package org.maymichael.data;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BinaryDataRaw {

    @ToString.Include
    @NonNull
    @EqualsAndHashCode.Include
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    // use array as is -> will be VERY slow for larger data sizes, since
    // spring data redis will iterate over each byte
    private byte[] data;
}
