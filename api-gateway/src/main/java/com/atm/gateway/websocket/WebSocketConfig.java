package com.atm.gateway.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AccountSocketHandler accountSocketHandler;

    public WebSocketConfig(AccountSocketHandler accountSocketHandler) {
        this.accountSocketHandler = accountSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(accountSocketHandler, "/ws").setAllowedOriginPatterns("*");
    }
}