package org.burgas.service

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import org.burgas.Product
import org.burgas.Store
import org.burgas.StoreProduct
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun InsertStatement<Number>.toStoreProduct(storeId: UUID, productId: UUID, amount: Long) {
    this[StoreProduct.storeId] = storeId
    this[StoreProduct.productId] = productId
    this[StoreProduct.amount] = amount
}

class StoreProductService {

    suspend fun addProducts(storeId: UUID, productIds: List<UUID>, amount: Long) = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        val store = Store.select(Store.fields)
            .where { (Store.id eq storeId) }
            .singleOrNull() ?: throw NullPointerException("Store is null")

        productIds.forEach { productId ->
            val product = Product.select(Product.fields)
                .where { (Product.id eq productId) }
                .singleOrNull() ?: throw NullPointerException("Product is null")

            StoreProduct.insert { insertStatement ->
                insertStatement.toStoreProduct(
                    storeId = store[Store.id], productId = product[Product.id], amount = amount
                )
            }
        }
    }

    suspend fun removeProducts(storeId: UUID, productIds: List<UUID>) = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        StoreProduct.deleteWhere { (StoreProduct.storeId eq storeId) and (StoreProduct.productId inList productIds) }
    }
}

fun Application.configureStoreProductRouter() {

    val storeProductService = StoreProductService()

    routing {

        authenticate("basic-auth-admin") {
            route("/api/v1") {

                post("/store-products/add-products") {
                    val storeId = UUID.fromString(call.parameters["storeId"]
                        ?: throw NullPointerException("Store id is null"))
                    val productIds = call.parameters.getAll("productId")
                        ?.map { UUID.fromString(it) } ?: throw NullPointerException("Product ids is null")
                    val amount = call.parameters["amount"]?.toLong() ?: throw NullPointerException("Amount is null")
                    storeProductService.addProducts(storeId, productIds, amount)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/store-products/remove-products") {
                    val storeId = UUID.fromString(call.parameters["storeId"]
                        ?: throw NullPointerException("Store id is null"))
                    val productIds = call.parameters.getAll("productId")
                        ?.map { UUID.fromString(it) } ?: throw NullPointerException("Product ids is null")
                    storeProductService.removeProducts(storeId, productIds)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}