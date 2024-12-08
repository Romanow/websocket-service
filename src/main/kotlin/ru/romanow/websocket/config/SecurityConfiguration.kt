package ru.romanow.websocket.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager
import org.springframework.security.messaging.web.csrf.CsrfChannelInterceptor
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain


private const val USERNAME = "test"
private const val PASSWORD = "test"
private const val USER_ROLE = "USER"

@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/ws/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.ALWAYS) }
            .build()
    }

    @Bean
    fun userDetailsService(): UserDetailsService {
        val user = (1..10).map {
            User.withUsername(USERNAME + it)
                .passwordEncoder { p -> passwordEncoder().encode(p) }
                .password(PASSWORD)
                .roles(USER_ROLE)
                .build()
        }

        return InMemoryUserDetailsManager(user)
    }

    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager =
        authenticationConfiguration.authenticationManager
}

@Configuration
@EnableWebSocketSecurity
class WebSocketSecurityConfiguration {

    @Bean
    fun authorizationManager(messages: MessageMatcherDelegatingAuthorizationManager.Builder): AuthorizationManager<Message<*>> {
        return messages
            .simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()
            .anyMessage().hasRole(USER_ROLE)
            .build()
    }
}