package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.burgas.Identity
import org.burgas.UUIDSerializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val biography: String? = null,
    val active: Boolean? = null
)

@Serializable
data class IdentityResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val username: String? = null,
    val email: String? = null,
    val biography: String? = null
)

fun InsertStatement<Number>.toIdentity(identityRequest: IdentityRequest) {
    val password = identityRequest.password ?: throw NullPointerException("Password is null")
    this[Identity.authority] = identityRequest.authority ?: throw NullPointerException("Authority is null")
    this[Identity.username] = identityRequest.username ?: throw NullPointerException("Username is null")
    this[Identity.password] = BCrypt.hashpw(password, BCrypt.gensalt())
    this[Identity.email] = identityRequest.email ?: throw NullPointerException("Email is null")
    this[Identity.biography] = identityRequest.biography ?: throw NullPointerException("Biography is null")
    this[Identity.active] = identityRequest.active ?: throw NullPointerException("Active is null")
}

fun UpdateStatement.toIdentity(identityId: UUID, identityRequest: IdentityRequest) {
    val identity = Identity.select(Identity.fields)
        .where { Identity.id eq identityId }
        .singleOrNull()
    if (identity != null) {
        this[Identity.id] = identityId
        this[Identity.authority] = identityRequest.authority ?: identity[Identity.authority]
        this[Identity.username] = identityRequest.username ?: identity[Identity.username]
        this[Identity.password] = identity[Identity.password]
        this[Identity.email] = identityRequest.email ?: identity[Identity.email]
        this[Identity.biography] = identityRequest.biography ?: identity[Identity.biography]
        this[Identity.active] = identityRequest.active ?: identity[Identity.active]
    } else {
        throw NullPointerException("Identity is null")
    }
}

fun ResultRow.toIdentityResponse(): IdentityResponse {
    return IdentityResponse(
        id = this[Identity.id],
        authority = this[Identity.authority],
        username = this[Identity.username],
        email = this[Identity.email],
        biography = this[Identity.biography]
    )
}

class IdentityService {

    suspend fun findAll(): List<IdentityResponse> = newSuspendedTransaction(
        readOnly = true, transactionIsolation = 2, context = Dispatchers.Default
    ) {
        Identity.selectAll().map { it.toIdentityResponse() }
    }

    suspend fun findById(identityId: UUID): IdentityResponse = newSuspendedTransaction(
        readOnly = true, transactionIsolation = 2, context = Dispatchers.Default
    ) {
        Identity.select(Identity.fields)
            .where { Identity.id eq identityId }
            .map { it.toIdentityResponse() }
            .single()
    }

    suspend fun create(identityRequest: IdentityRequest) = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        Identity.insert { insertStatement ->
            insertStatement.toIdentity(identityRequest)
        }
    }

    suspend fun update(identityRequest: IdentityRequest): Boolean = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        val identityId = identityRequest.id ?: throw NullPointerException("Identity id is null")
        Identity.update({ Identity.id eq identityId }) { updateStatement ->
            updateStatement.toIdentity(identityId, identityRequest)
        } > 0
    }

    suspend fun delete(identityId: UUID): Boolean = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        Identity.deleteWhere { Identity.id eq identityId } > 0
    }
}

fun Application.configureIdentityRouter() {

    val identityService = IdentityService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (
                call.request.path().equals("/api/v1/identities/by-id", false) ||
                call.request.path().equals("/api/v1/identities/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()

                if (principal != null) {
                    newSuspendedTransaction(readOnly = true) {

                        val identity = Identity.select(Identity.fields)
                            .where { (Identity.email eq principal.name) }
                            .singleOrNull()

                        if (identity != null) {
                            val identityId = UUID.fromString(call.parameters["identityId"])

                            if (identity[Identity.id] == identityId) {
                                proceed()

                            } else {
                                throw IllegalArgumentException("Identity id not matched")
                            }

                        } else {
                            throw IllegalArgumentException("Identity not authorized")
                        }
                    }

                } else {
                    throw IllegalArgumentException("Identity not authenticated")
                }

            } else if (call.request.path().equals("/api/v1/identities/update", false)) {
                val principal = call.principal<UserPasswordCredential>()

                if (principal != null) {
                    newSuspendedTransaction(readOnly = true) {

                        val identity = Identity.select(Identity.fields)
                            .where { (Identity.email eq principal.name) }
                            .singleOrNull()

                        if (identity != null) {
                            val identityRequest = call.receive(IdentityRequest::class)
                            val identityId = identityRequest.id ?: throw NullPointerException("Identity id is null")

                            if (identity[Identity.id] == identityId) {
                                val identityRequestKey = AttributeKey<IdentityRequest>("identityRequest")
                                call.attributes.put(identityRequestKey, identityRequest)
                                proceed()

                            } else {
                                throw IllegalArgumentException("Identity id not matched")
                            }

                        } else {
                            throw IllegalArgumentException("Identity not authorized")
                        }
                    }

                } else {
                    throw IllegalArgumentException("Identity not authenticated")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1") {

            authenticate("basic-auth-admin") {

                get("/identities") {
                    call.respond(HttpStatusCode.OK, identityService.findAll())
                }
            }

            authenticate("basic-auth-all") {

                get("/identities/by-id") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    call.respond(HttpStatusCode.OK, identityService.findById(identityId))
                }

                put("/identities/update") {
                    val identityRequestKey = AttributeKey<IdentityRequest>("identityRequest")
                    val identityRequest = call.attributes[identityRequestKey]
                    if (identityService.update(identityRequest)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/identities/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    if (identityService.delete(identityId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            post("/identities/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.create(identityRequest)
                call.respond(HttpStatusCode.Created)
            }
        }
    }
}