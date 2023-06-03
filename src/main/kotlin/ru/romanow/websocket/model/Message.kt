package ru.romanow.websocket.model

data class Message(
    val message: String,
    val user: String,
    val time: String
)