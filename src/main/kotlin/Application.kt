package org.burgas

import io.ktor.server.application.*
import org.burgas.service.configureCategoryRouter
import org.burgas.service.configureIdentityRouter
import org.burgas.service.configureProductRouter
import org.burgas.service.configureStoreProductRouter
import org.burgas.service.configureStoreRouter

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureDatabases()
    configureSerialization()
    configureAuthentication()
    configureRouting()

    configureIdentityRouter()
    configureCategoryRouter()
    configureProductRouter()
    configureStoreRouter()
    configureStoreProductRouter()
}
