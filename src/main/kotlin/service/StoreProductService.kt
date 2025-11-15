package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.burgas.Product
import org.burgas.Store
import org.burgas.StoreProduct
import org.burgas.UUIDSerializer
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.*

@Serializable
data class StoreProductRequest(
    @Serializable(with = UUIDSerializer::class)
    val storeId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val productId: UUID,
    val amount: Long? = 0
)

fun InsertStatement<Number>.toStoreProduct(storeId: UUID, productId: UUID, amount: Long?) {
    this[StoreProduct.storeId] = storeId
    this[StoreProduct.productId] = productId
    this[StoreProduct.amount] = amount ?: 0
}

class StoreProductService {

    suspend fun addProducts(storeProductRequests: List<StoreProductRequest>) = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        storeProductRequests.forEach { storeProductRequest ->
            val store = Store.select(Store.fields).where { (Store.id eq storeProductRequest.storeId) }
                .singleOrNull() ?: throw IllegalArgumentException("Store not found: ${storeProductRequest.storeId}")

            val product = Product.select(Product.fields).where { (Product.id eq storeProductRequest.productId) }
                .singleOrNull() ?: throw IllegalArgumentException("Product not found: ${storeProductRequest.productId}")

            StoreProduct.insert { insertStatement ->
                insertStatement.toStoreProduct(store[Store.id], product[Product.id], storeProductRequest.amount)
            }
        }
    }

    suspend fun removeProducts(storeProductRequests: List<StoreProductRequest>) = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        storeProductRequests.forEach { storeProductRequest ->
            StoreProduct.deleteWhere {
                (StoreProduct.storeId eq storeProductRequest.storeId) and (StoreProduct.productId eq storeProductRequest.productId)
            }
        }
    }

    suspend fun changeAmount(storeProductRequest: StoreProductRequest) = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        val whereCheck = (StoreProduct.storeId eq storeProductRequest.storeId) and
                (StoreProduct.productId eq storeProductRequest.productId)
        val storeProduct = StoreProduct.select(StoreProduct.fields).where { whereCheck }
            .singleOrNull() ?: throw IllegalArgumentException("Store product is null")
        StoreProduct.update({whereCheck}) {
            updateStatement -> updateStatement[StoreProduct.amount] = storeProductRequest.amount ?: storeProduct[StoreProduct.amount]
        }
    }
}

fun Application.configureStoreProductRouter() {

    val storeProductService = StoreProductService()

    routing {

        authenticate("basic-auth-admin") {
            route("/api/v1") {

                post("/store-products/add-products") {
                    val storeProductRequests = call.receive<List<StoreProductRequest>>()
                    storeProductService.addProducts(storeProductRequests)
                    call.respond(HttpStatusCode.OK)
                }

                put("/store-products/change-amount") {
                    val storeProductRequest = call.receive(StoreProductRequest::class)
                    storeProductService.changeAmount(storeProductRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/store-products/remove-products") {
                    val storeProductRequests = call.receive<List<StoreProductRequest>>()
                    storeProductService.removeProducts(storeProductRequests)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}