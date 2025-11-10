package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.burgas.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.*

@Serializable
data class StoreRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val address: String? = null
)

@Serializable
data class StoreShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val address: String? = null
)

@Serializable
data class StoreFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val address: String? = null,
    val products: List<ProductWithAmountResponse>? = null
)

@Serializable
data class StoreWithAmountResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val address: String? = null,
    val amount: Long? = null
)

fun ResultRow.toStoreShortResponse(): StoreShortResponse {
    return StoreShortResponse(
        id = this[Store.id],
        name = this[Store.name],
        address = this[Store.address]
    )
}

fun ResultRow.toStoreFullResponse(products: List<ProductWithAmountResponse>): StoreFullResponse {
    return StoreFullResponse(
        id = this[Store.id],
        name = this[Store.name],
        address = this[Store.address],
        products = products
    )
}

fun ResultRow.toStoreWithAmountResponse(amount: Long): StoreWithAmountResponse {
    return StoreWithAmountResponse(
        id = this[Store.id],
        name = this[Store.name],
        address = this[Store.address],
        amount = amount
    )
}

fun InsertStatement<Number>.toStore(storeRequest: StoreRequest) {
    this[Store.name] = storeRequest.name ?: throw NullPointerException("Store name is null")
    this[Store.address] = storeRequest.address ?: throw NullPointerException("Address is null")
}

fun UpdateStatement.toStore(storeId: UUID, storeRequest: StoreRequest) {
    val store = Store.select(Store.fields)
        .where { Store.id eq storeId }
        .singleOrNull()
    if (store != null) {
        this[Store.id] = storeId
        this[Store.name] = storeRequest.name ?: store[Store.name]
        this[Store.address] = storeRequest.address ?: store[Store.address]

    } else {
        throw NullPointerException("Store is null")
    }
}

class StoreService {

    suspend fun findAll(): List<StoreShortResponse> = newSuspendedTransaction {
        Store.selectAll()
            .map { resultRow -> resultRow.toStoreShortResponse() }
            .toList()
    }

    suspend fun findById(storeId: UUID): StoreFullResponse = newSuspendedTransaction {
        val products = Store
            .leftJoin(StoreProduct)
            .leftJoin(Product)
            .leftJoin(Category)
            .select(Store.fields + StoreProduct.fields + Product.fields + Category.fields)
            .where { Store.id eq storeId }
            .map { resultRow ->
                val category = resultRow.toCategoryShortResponse()
                val amount = resultRow[StoreProduct.amount]
                resultRow.toProductWithAmountResponse(category, amount)
            }
            .toList()
        Store.select(Store.fields)
            .where { Store.id eq storeId }
            .map { resultRow -> resultRow.toStoreFullResponse(products) }
            .single()
    }

    suspend fun create(storeRequest: StoreRequest) = newSuspendedTransaction {
        Store.insert { insertStatement ->
            insertStatement.toStore(storeRequest)
        }
    }

    suspend fun update(storeRequest: StoreRequest): Boolean = newSuspendedTransaction {
        val storeId = storeRequest.id ?: throw NullPointerException("Store id is null")
        Store.update({ Store.id eq storeId }) { updateStatement ->
            updateStatement.toStore(storeId, storeRequest)
        } > 0
    }

    suspend fun delete(storeId: UUID): Boolean = newSuspendedTransaction {
        Store.deleteWhere { Store.id eq storeId } > 0
    }
}

fun Application.configureStoreRouter() {

    val storeService = StoreService()

    routing {

        route("/api/v1") {

            get("/stores") {
                call.respond(HttpStatusCode.OK, storeService.findAll())
            }

            get("/stores/by-id") {
                val storeId = UUID.fromString(call.parameters["storeId"])
                call.respond(HttpStatusCode.OK, storeService.findById(storeId))
            }

            authenticate("basic-auth-admin") {

                post("/stores/create") {
                    val storeRequest = call.receive(StoreRequest::class)
                    storeService.create(storeRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/stores/update") {
                    val storeRequest = call.receive(StoreRequest::class)
                    if (storeService.update(storeRequest)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/stores/delete") {
                    val storeId = UUID.fromString(call.parameters["storeId"])
                    if (storeService.delete(storeId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}