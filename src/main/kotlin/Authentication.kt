package org.burgas

import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.burgas.service.Authority
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

fun Application.configureAuthentication() {
    authentication {

        basic("basic-auth-all") {
            validate { credentials -> transaction(readOnly = true, transactionIsolation = 2) {

                    val identity = Identity.select(Identity.fields)
                        .where { (Identity.email eq credentials.name) }
                        .singleOrNull()

                    if (identity != null) {

                        if (BCrypt.checkpw(credentials.password, identity[Identity.password])) {
                            UserPasswordCredential(credentials.name, credentials.password)

                        } else {
                            null
                        }

                    } else {
                        null
                    }
                }
            }
        }

        basic("basic-auth-admin") {
            validate { credentials -> transaction(readOnly = true, transactionIsolation = 2) {

                    val identity = Identity.select(Identity.fields)
                        .where { (Identity.email eq credentials.name) }
                        .singleOrNull()

                    if (identity != null && identity[Identity.authority] == Authority.ADMIN) {

                        if (BCrypt.checkpw(credentials.password, identity[Identity.password])) {
                            UserPasswordCredential(credentials.name, credentials.password)

                        } else {
                            null
                        }

                    } else {
                        null
                    }
                }
            }
        }
    }
}