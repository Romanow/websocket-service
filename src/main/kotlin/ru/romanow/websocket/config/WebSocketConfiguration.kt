package ru.romanow.websocket.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericToStringSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.session.Session
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession
import org.springframework.session.web.socket.config.annotation.AbstractSessionWebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry


@Configuration
@EnableRedisHttpSession
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
class WebSocketConfiguration(
    private val messageCredentialsInterceptor: MessageCredentialsInterceptor,
) : AbstractSessionWebSocketMessageBrokerConfigurer<Session>() {

    override fun configureStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
    }

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config
            .setApplicationDestinationPrefixes("/chat")
            .setUserDestinationPrefix("/user")
            .enableSimpleBroker("/queue")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(messageCredentialsInterceptor)
    }

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Long> {
        val template = RedisTemplate<String, Long>()
        template.connectionFactory = connectionFactory
        template.defaultSerializer = StringRedisSerializer()
        template.hashKeySerializer = GenericToStringSerializer(Long::class.java)
        return template
    }
}