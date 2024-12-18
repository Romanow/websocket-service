[![Build project](https://github.com/Romanow/websocket-service/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/Romanow/websocket-service/actions/workflows/build.yml)
[![pre-commit](https://img.shields.io/badge/pre--commit-enabled-brightgreen?logo=pre-commit)](https://github.com/pre-commit/pre-commit)

# Multi-instance adapter between REST and WebSocket

## Поставно ка задачи

Нужно реализовать схему асинхронной нотификации от backend (`calm-gateway`) до UI (`calm-ui`) с
помощью [WebSocket](https://learn.javascript.ru/websocket).

Для кодирования данных / команд используем протокол [`STOMP`](https://stomp.github.io/stomp-specification-1.2.html).
SockJS для обратной совместимости использовать не требуется, т.к. заказчики являются внутренней структурой и используют
последние версии браузеров.

## Backend

Т.к. WebSocket является сессионным протоколом, то для трансляции событий через него нужно выделить специальный сервис,
т.к. frontend будет держать сессию именно с этим сервисом. Его основная задача – трансляция сообщений из REST / очереди
в сообщениях для всех / конкретного пользователя по WebSocket. Т.е. на сервис приходит запрос _"состояние расчета
изменилось"_ и он отправляет эту информацию по всем открытым соединениям. Если страница не подписана на эти сообщения,
то она просто игнорирует его.

### Подписки на события (`STOMP`)

Для разделения потоков данных в `STOMP` используются подписки, аналогичные топикам в очередях.

### Авторизация

`STOMP` over WebSocket имеет свои заголовки и нельзя их передать как `basic` или `bearer` авторизацию, а только из тела
сообщения, поэтому нужно вручную
через [`MessageCredentialsInterceptor`](src/main/kotlin/ru/romanow/websocket/config/MessageCredentialsInterceptor.kt)
вытащить пользователя из заголовка самого сообщения (не HTTP заголовков). В примере используется формат basic
авторизации, передаваемый в теле `STOMP` в заголовке `X-Authorization`. Проверка пользователя выполняется там же в
`MessageCredentialsInterceptor` через стандартный `AuthenticationManager`.

Аналогично можно реализовать token-based авторизацию, передавая в заголовке сообщения `X-Authorization` bearer токен.

#### Disconnect

События `DISCONNECT` не имеет смысла закрывать безопасностью, поэтому они описаны как `permitAll()`.

### Несколько instance

Т.к. WebSocket сессионный протокол, то сообщение отправляется в _постоянно открытое_ соединение. Это TCP соединение на
уровне ОС, следовательно, делать shared-сессию бесполезно. Вместо этого используется следующий подход: каждый instance
подключен к очереди (которая поддерживает `STOMP` (`RabbitMQ`, `ArtemisMQ`)) и помимо отправки сообщения всем своим
соединениям, он кидает это сообщение в очередь и его получают все другие instance и рассылают своим подписчикам.

Аналогично работает и с отправкой пользователю, за исключением того, что instance проверяет, есть ли у него такой
активный пользователь (по связке пользователь (из авторизации) – сессия).

## Frontend

Используем `STOMP`, поэтому
реализации [sockjs-client](https://github.com/sockjs/sockjs-client), [react-use-websocket](https://www.npmjs.com/package/react-use-websocket)
не подходят, т.к. там нельзя кодировать сообщения WebSocket через `STOMP`.

В [демо (websocket-frontend)](https://github.com/Romanow/websocket-frontend) для реализации
использовался [stomp-js](https://stomp-js.github.io/guide/stompjs/using-stompjs-v5.html) обернутый в React Hooks.

### Переподключение

В случае потери соединения используем автоматические переподключение через короткий timeout.

### Stale сессия

Для контроля протухания сессии Spring WebSocket использует heart-beat сообщения (`ping`).

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
            .enableStompBrokerRelay("/queue")           // общие исходящие сообщения (на WebSocket и очередь) /queue/**
            .setRelayHost(artemisProperties.host!!)     // адрес очереди
            .setRelayPort(artemisProperties.port!!)
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        // STOMP over WebSocket имеет свои заголовки и нельзя их передать как basic или bearer авторизацию через HTTP.
        // Поэтому они передаются в заголовках STOMP сообщения, а MessageInterceptor их достает и проверяет авторизацию.
        registration.interceptors(messageCredentialsInterceptor)
    }
}
```

Т.к. из клиента мы не можем передать HTTP авторизацию, то в HTTP конфигурации Spring Security мы
указываем `permitAll()`, а в messaging задает пользователя с ролью `USER` для всех
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
                it.sessionCreationPolicy(SessionCreationPolicy.ALWAYS) // создаем сессию для всех соединений
            }
            .build()
    }
}

@Configuration
@EnableWebSocketSecurity
class WebSocketSecurityConfiguration {

    @Bean
    fun authorizationManager(messages: MessageMatcherDelegatingAuthorizationManager.Builder): AuthorizationManager<Message<*>> {
        return messages
            .simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()  // для disconnect не нужна авторизация
            .anyMessage().hasRole(USER_ROLE)                           // все остальные сообщения с ролью USER
            .build()
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun csrfChannelInterceptor(): ChannelInterceptor {
        return object : ChannelInterceptor {}                          // выключаем CSRF
    }
}
```

Запрос проходит по цепочке фильтров `securityFilterChain` (`permitAll`), после заходит
в [`MessageCredentialsInterceptor`](src/main/kotlin/ru/romanow/websocket/config/MessageCredentialsInterceptor.kt), там
из заголовка `X-Authorization` берется пользователь и _вручную_ через `AuthenticationManager` выполняется проверка по
цепочке `configureInbound`.

## Тестирование

Для тестирования отказоустойчивости конфигурации поднимается два instance `websocker-service` (8081, 8082) и nginx
(8080), который работает как reverse proxy и ArtemisMQ ([docker-compose.yml](docker-compose.yml)). Через nginx _только_
устанавливается соединение до конечного instance, с которым и идет общение по WebSocket. Общение между instance идет
через очередь ArtemisMQ.

Для UI используется [websocket-frontend](https://github.com/Romanow/websocket-frontend).

В случае падения одного instance, UI просто подключается к другой ноде. Если на instance пришло сообщение, то он
отправляет его по своим соединениям и так же передает в очередь, чтобы остальные instance отправили его своим
подключениям.
