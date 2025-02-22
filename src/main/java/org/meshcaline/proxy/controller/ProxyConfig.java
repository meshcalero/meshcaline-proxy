package org.meshcaline.proxy.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class ProxyConfig {
    /**
     * Ensure that Forwarded Headers are applied to the incoming request (and redirects)
     * so that the reverse proxy can operate correctly behind other reverse proxies.
     * @return The filter that operates the header handling
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}