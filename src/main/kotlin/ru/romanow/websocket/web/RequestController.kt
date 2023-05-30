package ru.romanow.websocket.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/notify")
class RequestController(
    private val messageTemplate: SimpMessagingTemplate,
    private val userRegistry: SimpUserRegistry,
) {
    private val logger = LoggerFactory.getLogger(RequestController::class.java)

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun notify(@RequestBody notification: Notification) {
        if (notification.user != null) {
            if (userRegistry.getUser(notification.user) != null) {
                messageTemplate.convertAndSendToUser(notification.user, "/queue/reply", notification.message)
            } else {
                logger.warn("User ${notification.user} not found with active session")
            }
        } else {
            messageTemplate.convertAndSend("/queue/reply", notification.message)
        }
    }

    data class Notification(
        val message: String,
        val user: String? = null,
    )
}