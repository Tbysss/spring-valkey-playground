package org.maymichael.util;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import org.maymichael.data.BinaryData;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Component
public class KryoRedisSerializer<T> implements RedisSerializer<T> {

    private static final Pool<Kryo> kryoPool = new Pool<>(true, false, 8) {
        protected Kryo create() {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(true);
            // add all classes we want to serialize here
            // with registration required, we have to declare what we want to serialize
            // but its faster
            kryo.register(BinaryData.class);
            kryo.register(byte[].class);
            return kryo;
        }
    };

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null)
            return null;

        var kryo = kryoPool.obtain();
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Output output = new Output(stream);
            kryo.writeClassAndObject(output, value);
            output.close();
            return stream.toByteArray();
        } finally {
            kryoPool.free(kryo);
        }
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null)
            return null;

        var kryo = kryoPool.obtain();
        try {
            try (var input = new Input(bytes)) {
                //noinspection unchecked
                return (T) kryo.readClassAndObject(input);
            }
        } finally {
            kryoPool.free(kryo);
        }
    }
}