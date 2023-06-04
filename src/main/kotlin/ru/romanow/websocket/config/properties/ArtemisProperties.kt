package ru.romanow.websocket.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("artemis")
data class ArtemisProperties(
    var host: String? = null,
    var port: Int? = null
)