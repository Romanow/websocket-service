package ru.romanow.websocket.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.stereotype.Controller
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import ru.romanow.websocket.model.Message
import java.security.Principal
import java.time.LocalDateTime
import java.time.ZoneOffset.*
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

@Controller
class UserMessageHandler(
    private val simpMessagingTemplate: SimpMessagingTemplate,
    private val simpUserRegistry: SimpUserRegistry,
    private val objectMapper: ObjectMapper,
    redisTemplate: RedisTemplate<String, Long>,
) {
    private val logger = LoggerFactory.getLogger(UserMessageHandler::class.java)
    private val hashOperations = redisTemplate.boundHashOps<Long, String>("all")

    @MessageMapping("/message")
    @SendTo("/queue/message")
    fun reply(msg: String, principal: Principal): Message {
        logger.info("Received new message [$msg] from ${principal.name}")
        val now = LocalDateTime.now()
        val message = Message(
            message = msg,
            user = principal.name,
            time = ISO_DATE_TIME.format(now)
        )
        hashOperations.put(now.toEpochSecond(UTC), toJson(message))
        return message
    }

    @MessageMapping("/init/users")
    @SendToUser("/queue/init/users")
    fun users(): String {
        return toJson(simpUserRegistry.users.map { it.name })
    }

    @MessageMapping("/init/messages")
    @SendToUser("/queue/init/messages")
    fun messages(): String {
        val keys = hashOperations
            .keys()
            ?.sortedDescending()
            ?.take(10)
            .orEmpty()
        val messages = hashOperations
            .multiGet(keys)
            ?.map { fromJson<Message>(it) }
            ?.sortedBy { it.time }
            .orEmpty()
        return toJson(messages)
    }

    @EventListener
    fun connectedEventHandler(event: SessionConnectedEvent) {
        logger.info("User ${event.user?.name} connected")
        simpMessagingTemplate.convertAndSend("/queue/users", toJson(simpUserRegistry.users.map { it.name }))
    }

    @EventListener
    fun disconnectedEventHandler(event: SessionDisconnectEvent) {
        logger.info("User ${event.user?.name} disconnected")
        simpMessagingTemplate.convertAndSend("/queue/users", toJson(simpUserRegistry.users.map { it.name }))
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    fun handleException(exception: Throwable): String {
        return exception.message!!
    }

    private fun toJson(obj: Any) = objectMapper.writeValueAsString(obj)
    private inline fun <reified T> fromJson(json: String): T = objectMapper.readValue(json, T::class.java)
}