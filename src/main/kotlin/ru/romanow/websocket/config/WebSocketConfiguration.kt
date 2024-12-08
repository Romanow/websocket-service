package ru.romanow.websocket.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.*
import ru.romanow.websocket.config.properties.ArtemisProperties
import kotlin.io.encoding.ExperimentalEncodingApi

@Configuration
@ExperimentalEncodingApi
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(ArtemisProperties::class)
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
class WebSocketConfiguration(
    private val messageCredentialsInterceptor: MessageCredentialsInterceptor,
    private val artemisProperties: ArtemisProperties
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
    }

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config
            .setApplicationDestinationPrefixes("/chat")
            .setUserDestinationPrefix("/user")
            .enableStompBrokerRelay("/queue")
            .setRelayHost(artemisProperties.host!!)
            .setRelayPort(artemisProperties.port!!)
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(messageCredentialsInterceptor)
    }
}
