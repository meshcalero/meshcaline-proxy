package org.meshcaline.proxy.controller;

import org.meshcaline.proxy.service.ProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

@RestController
public class ProxyController {

    @Autowired
    private ProxyService proxyService;

    @RequestMapping("/**")
    public Flux<DataBuffer> handleRequest(ServerWebExchange exchange) {
        return proxyService.handleRequest(exchange);
    }
}
