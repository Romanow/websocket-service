package ru.romanow.websocket

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.GsonMessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils.IS_OS_MAC_OSX
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils.OS_ARCH
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import org.testcontainers.shaded.org.hamcrest.Matchers.notNullValue
import ru.romanow.websocket.model.Message
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


typealias RedisContainer = GenericContainer<*>

@ExperimentalEncodingApi
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
internal class WebSocketApplicationTest {
    private val response = AtomicReference<Message>()
    private val msg = "Hello, world"
    private val user = "test1"

    @LocalServerPort
    private var port: Int? = 0

    @Test
    fun test() {
        val webSocketClient = StandardWebSocketClient()
        val stompClient = WebSocketStompClient(webSocketClient)
        stompClient.messageConverter = GsonMessageConverter()
        val headers = StompHeaders().also {
            it.set("X-Authorization", Base64.encode("$user:test".toByteArray()))
        }
        val handler = object : StompSessionHandlerAdapter() {}
        val future = stompClient.connectAsync("ws://localhost:$port/ws", WebSocketHttpHeaders(), headers, handler)

        val session = future.get(10, TimeUnit.SECONDS)
        session.subscribe("/queue/message", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type {
                return Message::class.java
            }

            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                logger.info("Received response $payload")
                response.set(payload as Message)
            }
        })

        session.send("/chat/message", msg)

        await().atMost(Duration.ofSeconds(30)).untilAtomic(response, notNullValue())
        val message = response.get()
        assertThat(message.user).isEqualTo("test1")
        assertThat(message.message).isEqualTo(msg)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WebSocketApplicationTest::class.java)

        private const val REDIS_IMAGE = "redis:7.4"
        private const val REDIS_PORT = 6379

        private const val ARTEMIS_IMAGE = "romanowalex/artemis:2.28.0"
        private const val ARTEMIS_PORT = 61616

        @JvmStatic
        @Container
        var redis: RedisContainer = RedisContainer(REDIS_IMAGE)
            .withExposedPorts(REDIS_PORT)
            .withLogConsumer(Slf4jLogConsumer(logger))

        @JvmStatic
        @Container
        var artemis: GenericContainer<*> = GenericContainer(getArtemisImage())
            .withEnv("ANONYMOUS_LOGIN", "true")
            .withExposedPorts(ARTEMIS_PORT)
            .withLogConsumer(Slf4jLogConsumer(logger))

        private fun getArtemisImage() =
            if (IS_OS_MAC_OSX && OS_ARCH.equals("aarch64")) "$ARTEMIS_IMAGE-arm" else ARTEMIS_IMAGE

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.redis.host") { "localhost" }
            registry.add("spring.redis.port") { redis.getMappedPort(REDIS_PORT) }
            registry.add("artemis.host") { "localhost" }
            registry.add("artemis.port") { artemis.getMappedPort(ARTEMIS_PORT) }
        }
    }
}
