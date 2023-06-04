package ru.romanow.websocket.web

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.*
import ru.romanow.websocket.model.Message
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

@RestController
@RequestMapping("/api/v1/message")
class RequestController(
    private val messageTemplate: SimpMessagingTemplate,
) {

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun notify(@RequestBody notification: Notification) {
        messageTemplate.convertAndSend(
            "/queue/message", Message(notification.message, notification.user, ISO_DATE_TIME.format(now()))
        )
    }

    data class Notification(
        val message: String,
        val user: String,
    )
}