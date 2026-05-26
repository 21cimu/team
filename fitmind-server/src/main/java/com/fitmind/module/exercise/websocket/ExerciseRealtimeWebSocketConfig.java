package com.fitmind.module.exercise.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ExerciseRealtimeWebSocketConfig implements WebSocketConfigurer {

    private final ExerciseRealtimeWebSocketHandler exerciseRealtimeWebSocketHandler;
    private final ExerciseRealtimeAuthHandshakeInterceptor exerciseRealtimeAuthHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(exerciseRealtimeWebSocketHandler, "/ws/exercise/realtime")
                .addInterceptors(exerciseRealtimeAuthHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean websocketContainerCustomizer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(512 * 1024);
        container.setMaxBinaryMessageBufferSize(512 * 1024);
        container.setAsyncSendTimeout(30_000L);
        container.setMaxSessionIdleTimeout(60_000L);
        return container;
    }
}
