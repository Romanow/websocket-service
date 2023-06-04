package ru.romanow.websocket.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericToStringSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfiguration {

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Long> {
        val redisTemplate = RedisTemplate<String, Long>()
        redisTemplate.connectionFactory = connectionFactory
        redisTemplate.defaultSerializer = StringRedisSerializer()
        redisTemplate.hashKeySerializer = GenericToStringSerializer(Long::class.java)
        return redisTemplate
    }
}