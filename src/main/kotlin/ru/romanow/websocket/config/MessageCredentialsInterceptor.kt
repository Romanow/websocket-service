package ru.romanow.websocket.config

import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service
import org.springframework.util.Base64Utils.decodeFromString

@Service
class MessageCredentialsInterceptor(
    private val authenticationManager: AuthenticationManager,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        if (accessor?.command == StompCommand.CONNECT) {
            val authorization: List<String>? = accessor.getNativeHeader("X-Authorization")
            if (authorization?.size == 1) {
                val credentials = String(decodeFromString(authorization[0])).split(":")
                if (credentials.size == 2) {
                    val user = UsernamePasswordAuthenticationToken(
                        credentials[0],
                        credentials[1],
                        listOf(SimpleGrantedAuthority("ROLE_USER"))
                    )
                    accessor.user = authenticationManager.authenticate(user)
                }
            }
        }
        return message
    }
}