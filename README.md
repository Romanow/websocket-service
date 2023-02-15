# Multi-instance adapter between REST and WebSocket

[![Build project](https://github.com/Romanow/websocket-service/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/Romanow/websocket-service/actions/workflows/build.yml)

### Требования

1. Пользователь вводит адрес сервера и свое имя, нажимает `connect`.
2. На backend сделать специальный endpoint для отправки сообщения всем или выбранному пользователю.
3. Реализовать синхронизацию сессий между backend.
4. Реализовать переподключение клиента, если произошла ошибка или пропала связь.
5. На backend реализовать отключение сессий.
6. При подключении нового клиента получать все старые сообщения топика `all`.
7. Сделать обработку сообщений через STOMP:
    1. `/users` – обновление списка пользователей;
    2. `/all/message` – отправка сообщения всем активным пользователям;
    3. `/{user}/message` – отправка сообщений выбранному пользователю.