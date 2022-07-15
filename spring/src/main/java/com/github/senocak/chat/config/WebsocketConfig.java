package com.github.senocak.chat.config;

import com.github.senocak.chat.controller.WebsocketController;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebsocketConfig implements WebSocketConfigurer {
    private final WebsocketController websocketController;

    @Override
    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
        registry.addHandler(websocketController, "/websocket/{userId}")
                .setAllowedOrigins("*");
    }
}
