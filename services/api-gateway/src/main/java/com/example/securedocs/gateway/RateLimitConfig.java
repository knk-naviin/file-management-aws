package com.example.securedocs.gateway;

import java.net.InetSocketAddress;
import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
    @Bean
    KeyResolver clientAddressKeyResolver() {
        return exchange -> exchange.getPrincipal().map(Principal::getName)
                .filter(name -> !name.isBlank())
                .switchIfEmpty(Mono.fromSupplier(() -> {
            InetSocketAddress address = exchange.getRequest().getRemoteAddress();
            return address == null ? "unknown" : address.getAddress().getHostAddress();
        }));
    }
}
