# Multi-instance adapter between REST and WebSocket

[![Build project](https://github.com/Romanow/websocket-service/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/Romanow/websocket-service/actions/workflows/build.yml)

## Поставнока задачи

Нужно реализовать схему асинхронной нотификации от backend (`calm-gateway`) до UI (`calm-ui`) с
помощью [WebSocket](https://learn.javascript.ru/websocket).

Для кодирования данных / команд используем протокол [`STOMP`](https://stomp.github.io/stomp-specification-1.2.html).
SockJS для обратной совместимости использовать не требуется, т.к. заказчики являются внутрненней структурой и используют
последние версии браузеров.

## Backend

Т.к.т WebSocket является сессионным протоколом, то для трансляции событий через него нужно выделить специальный сервис,
т.к. frontend будет держать сессию именно с этим сервисом. Основная задача этого сервиса – трансляция сообщений из
REST / очереди в сообщения для всех / конкретного пользователя по WebSocket. Т.е. на сервис приходит запрос _"расчет
перешел на новый шаг"_ и он отправляет эту информацию по всем открытым соединениям. Если страница не подписана на эти
сообщения, то она просто игнорирует его (например, на странице НСИ нет необходимости получать нотификации об изменении
статуса расчета).

В роли такого сервиса есть два кандидата: `calm-notification-service`, `calm-gateway`.

### Подписки на события (`STOMP`)

Для разделения потоков данных в `STOMP` используется понятие подписки:

### Авторизация

`STOMP` over WebSocket имеет свои заголовки и нельзя их передать как `basic` или `bearer` атворизацию, а только из тела
сообщения, поэтому нужно вручную
через [`MessageCredentialsInterceptor`](src/main/kotlin/ru/romanow/websocket/config/MessageCredentialsInterceptor.kt)
вытащить пользователя из заголовкох самого сообщения (не HTTP заголовков). В примере используется формат basic
авторизации, передаваемый в теле `STOMP` в заголовке `X-Authorization`. Проверка пользователя выполняется там же в
`MessageCredentialsInterceptor` через стандартный `AuthenticationManager`.

#### Disconnect

События `DISCONNECT` не имеет смысла закрывать безопасностью, поэтому они описаны как `permitAll()`.

### Несколько instance

Т.к. WebSocket сессионный протокол, то сообщение отправляется в _постоянно открытое_ соединение. Это TCP соединение на
уровне ОС, следовательно делать shared-сессию бесполезно. Вместо этого используется следующий подход: каждый instance
подключен к очереди (которая поддерживает `STOMP` (`RabbitMQ`, `ArtemisMQ`)) и помимо отправки сообщения всем своим
соединениям, он кидает это сообщение в очередь и это же сообщение получают все другие instance и рассылают своим
подписчикам.

Аналогично работает и с отправкой пользователю, за исключением того, что instance проверяет, есть ли у него такой
активный пользователь.

## Frontend

Используем `STOMP`, поэтому
реализации [sockjs-client](https://github.com/sockjs/sockjs-client), [react-use-websocket](https://www.npmjs.com/package/react-use-websocket)
не подходят, т.к. там нельзя кодировать сообщения WebSocket через `STOMP`.

Для [демо (websocket-frontend)](https://github.com/Romanow/websocket-frontend) для реализации
использовался [stompjs](https://stomp-js.github.io/guide/stompjs/using-stompjs-v5.html) обернутый в React Hooks.

### Переподключение

В случае потери соединения используем автоматические переподключение через короткий timeout.

### Stale сессия

Для контроля протухания сессии Spring WebSocket использует heart-beat сообщения.

## Реализация

Подключаем в [build.gradle](build.gradle) зависимости:

```groovy
implementation "org.springframework.boot:spring-boot-starter-websocket"
implementation "org.springframework.boot:spring-boot-starter-security"
implementation "org.springframework.security:spring-security-messaging"
```

Включаем поддержку
WebSocket + `STOMP` ([`WebSocketConfiguration`](src/main/kotlin/ru/romanow/websocket/config/WebSocketConfiguration.kt):

```kotlin
@Configuration
@EnableWebSocketMessageBroker           // включаем поддержку STOMP
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // конфигурация должна примениться раньше security
class WebSocketConfiguration : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws")              // context-path 
            .setAllowedOriginPatterns("*")   // выключаем CORS (в production нужно указать конкретные origin)
    }

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config
            .setApplicationDestinationPrefixes("/chat") // входные сообщения /chat/**
            .setUserDestinationPrefix("/user")          // исходящие сообщения /user/queue/**
            .enableStompBrokerRelay("/queue")           // общие исходящие сообщения (на WebSocket и очердь) /queue/**
            .setRelayHost(artemisProperties.host!!)     // адрес очереди
            .setRelayPort(artemisProperties.port!!)
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        // STOMP over WebSocket имеет свои заголовки и нельзя их передать как basic или bearer атворизацию через HTTP.
        // Поэтому они передаются в заголовках STOMP сообщения, а MessageInterceptor их достает и проверяет авторизацию.
        registration.interceptors(messageCredentialsInterceptor)
    }
}
```

Т.к. из клиента мы не можем передать HTTP авторизацию, то в HTTP конфигурации Spring Security мы
указываем `permitAll()`, а в messaging задает пользователя с ролью `USER` для свех
подписок ([`SecurityConfiguration`](src/main/kotlin/ru/romanow/websocket/config/SecurityConfiguration.kt)):

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .antMatcher("/ws/**")
            .authorizeHttpRequests {
                it.anyRequest().permitAll()                            // для /ws/** не проверяем HTTP авторизацию
            }                         
            .sessionManagement { 
                it.sessionCreationPolicy(SessionCreationPolicy.ALWAYS) // создаем сессию для всех соединенй
            }
            .build()
    }

}

@Configuration
class WebSocketSecurityConfiguration : AbstractSecurityWebSocketMessageBrokerConfigurer() {

    override fun configureInbound(messages: MessageSecurityMetadataSourceRegistry) {
        messages
            .simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll() // для disconnect не нужна авторизация
            .anyMessage().hasRole("USER")                             // все остальные сообщения с ролью USER
    }

    override fun sameOriginDisabled() = true                          // выключаем CORS
}
```

Запрос проходит по цепочке фильтров `securityFilterChain`, заходит
в [`MessageCredentialsInterceptor`](src/main/kotlin/ru/romanow/websocket/config/MessageCredentialsInterceptor.kt), там
из заголовка `X-Authorization` берется пользователь и через `AuthenticationManager` выполняется проверка по
цепочке `configureInbound`.

## Тестирование

Для тестирования отказоустойчивости конфигурации поднимается два instance `websocker-service` (8081, 8082) и nginx
(8080), который работает как reverse proxy и ArtemisMQ ([docker-compose.yml](docker-compose.yml)). Через nginx _только_
устанавливается соединение до конечного instance, с которым и идет общение по WebSocket. Общение между instance идет
через очередь ArtemisMQ.

Для UI используется [websocket-frontend](https://github.com/Romanow/websocket-frontend).

В случае падения одного instance, UI просто переподключается к другой ноде. Если на instance пришло сообщение, то он
отправляет его по своим соединениям и так же передает в очередь, чтобы остальные instance отпарвили его своим
подключениям.