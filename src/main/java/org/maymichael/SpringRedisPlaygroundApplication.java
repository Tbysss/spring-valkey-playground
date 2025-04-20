package org.maymichael;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringRedisPlaygroundApplication {
    static {
        // disable java DNS caching
        // don't really know, if it is needed, but valkey mainly works with IPs and DNS resolution
        // may not even get used by spring data?
        java.security.Security.setProperty("networkaddress.cache.ttl", "0");
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringRedisPlaygroundApplication.class, args);
    }
}