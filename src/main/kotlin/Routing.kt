package org.burgas

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import java.util.*

fun Application.configureRouting() {
    install(StatusPages) {

        exception<RuntimeException> { call, cause ->
            call.respondText(cause.localizedMessage, ContentType.Text.Plain, HttpStatusCode.BadRequest)
        }
    }

    install(CSRF) {
        allowOrigin("http://localhost:9000")
        originMatchesHost()
        checkHeader("X-CSRF-Token")
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("MyCustomHeader")
        anyHost()
    }

    install(Sessions) {
        cookie<CsrfToken>("MY_SESSION")
    }

    routing {

        get("/api/v1/generate-csrf") {
            val csrfToken = UUID.randomUUID().toString()
            call.sessions.set(CsrfToken(csrfToken))
            call.respondText("CSRF Token: $csrfToken")
        }
    }
}

@Serializable
data class CsrfToken(val token: String)