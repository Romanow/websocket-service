package ru.romanow.websocket.web

import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class UserMessageHandler {

    @MessageMapping("/request")
    @SendTo("/queue/reply")
    fun send(message: String, principal: Principal): String {
        return "Echo: $message. Auth $principal"
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    fun handleException(exception: Throwable): String {
        return exception.message!!
    }
}