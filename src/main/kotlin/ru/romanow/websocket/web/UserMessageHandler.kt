package ru.romanow.websocket.web

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class UserMessageHandler {

    @MessageMapping("/users")
    @SendTo("/app/users")
    fun send(message: String, principal: Principal): String {
        return "Echo: $message. Auth $principal"
    }
}