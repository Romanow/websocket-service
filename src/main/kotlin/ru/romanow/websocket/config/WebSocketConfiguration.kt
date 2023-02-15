package ru.romanow.websocket.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CopyOnWriteArrayList


@Configuration
@EnableWebSocket
class WebSocketConfiguration : WebSocketConfigurer {
    private val logger = LoggerFactory.getLogger(WebSocketConfiguration::class.java)

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(echoWebSocketHandler(), "/ws")
            .setAllowedOrigins("*")
    }

    fun echoWebSocketHandler(): WebSocketHandler {
        return object : TextWebSocketHandler() {
            private val sessions = CopyOnWriteArrayList<WebSocketSession>()

            override fun afterConnectionEstablished(session: WebSocketSession) {
                sessions.add(session)
                super.afterConnectionEstablished(session)
            }

            override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
                sessions.remove(session)
                super.afterConnectionClosed(session, status)
            }

            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                logger.info("Received message '{}'", message.payload)
                session.sendMessage(TextMessage("Echo: ${message.payload}"))
            }
        }
    }
}