package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.burgas.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

@Serializable
data class ProductRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val categoryId: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null
)

@Serializable
data class ProductShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val category: CategoryShortResponse? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null
)

@Serializable
data class ProductFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val category: CategoryShortResponse? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val stores: List<StoreWithAmountResponse>? = null
)

@Serializable
data class ProductWithAmountResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val category: CategoryShortResponse? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val amount: Long? = null
)

fun ResultRow.toProductShortResponse(category: CategoryShortResponse): ProductShortResponse {
    return ProductShortResponse(
        id = this[Product.id],
        category = category,
        name = this[Product.name],
        description = this[Product.description],
        price = this[Product.price]
    )
}

fun ResultRow.toProductFullResponse(
    category: CategoryShortResponse,
    stores: List<StoreWithAmountResponse>
): ProductFullResponse {
    return ProductFullResponse(
        id = this[Product.id],
        category = category,
        name = this[Product.name],
        description = this[Product.description],
        price = this[Product.price],
        stores = stores
    )
}

fun ResultRow.toProductWithAmountResponse(category: CategoryShortResponse, amount: Long): ProductWithAmountResponse {
    return ProductWithAmountResponse(
        id = this[Product.id],
        category = category,
        name = this[Product.name],
        description = this[Product.description],
        price = this[Product.price],
        amount = amount
    )
}

fun InsertStatement<Number>.toProduct(productRequest: ProductRequest) {
    this[Product.categoryId] = productRequest.categoryId ?: throw NullPointerException("Category id is null")
    this[Product.name] = productRequest.name ?: throw NullPointerException("Name is null")
    this[Product.description] = productRequest.description ?: throw NullPointerException("Description is null")
    this[Product.price] = productRequest.price ?: throw NullPointerException("Price is null")
}

fun UpdateStatement.toProduct(productId: UUID, productRequest: ProductRequest) {
    val product = Product.select(Product.fields)
        .where { Product.id eq productId }
        .singleOrNull()
    if (product != null) {
        this[Product.id] = productId
        this[Product.categoryId] = productRequest.categoryId ?: product[Product.categoryId]
        this[Product.name] = productRequest.name ?: product[Product.name]
        this[Product.description] = productRequest.description ?: product[Product.description]
        this[Product.price] = productRequest.price ?: product[Product.price]

    } else {
        throw NullPointerException("Product is null")
    }
}

class ProductService {

    suspend fun findAll(): List<ProductShortResponse> = newSuspendedTransaction(
        readOnly = true, transactionIsolation = 2, context = Dispatchers.Default
    ) {
        Product
            .leftJoin(Category)
            .selectAll()
            .map { resultRow ->
                val categoryShortResponse = resultRow.toCategoryShortResponse()
                resultRow.toProductShortResponse(categoryShortResponse)
            }
            .toList()
    }

    suspend fun findById(productId: UUID): ProductFullResponse = newSuspendedTransaction(
        readOnly = true, transactionIsolation = 2, context = Dispatchers.Default
    ) {
        val stores = Product.leftJoin(StoreProduct).leftJoin(Store)
            .select(Product.fields + Store.fields + StoreProduct.amount)
            .where { Product.id eq productId }
            .map { resultRow -> resultRow.toStoreWithAmountResponse(resultRow[StoreProduct.amount]) }
            .toList()
        Product.leftJoin(Category)
            .select(Product.fields + Category.fields)
            .where { Product.id eq productId }
            .map { resultRow ->
                val category = resultRow.toCategoryShortResponse()
                resultRow.toProductFullResponse(category, stores)
            }
            .single()
    }

    suspend fun create(productRequest: ProductRequest) = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        Product.insert { insertStatement ->
            insertStatement.toProduct(productRequest)
        }
    }

    suspend fun update(productRequest: ProductRequest): Boolean = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        val productId = productRequest.id ?: throw NullPointerException("Product id is null")
        Product.update({ Product.id eq productId }) { updateStatement ->
            updateStatement.toProduct(productId, productRequest)
        } > 0
    }

    suspend fun delete(productId: UUID): Boolean = newSuspendedTransaction(
        transactionIsolation = 2, context = Dispatchers.Default
    ) {
        Product.deleteWhere { Product.id eq productId } > 0
    }
}

fun Application.configureProductRouter() {

    val productService = ProductService()

    routing {

        route("/api/v1") {

            get("/products") {
                call.respond(HttpStatusCode.OK, productService.findAll())
            }

            get("/products/by-id") {
                val productId = UUID.fromString(call.parameters["productId"])
                call.respond(HttpStatusCode.OK, productService.findById(productId))
            }

            authenticate("basic-auth-admin") {

                post("/products/create") {
                    val productRequest = call.receive(ProductRequest::class)
                    productService.create(productRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/products/update") {
                    val productRequest = call.receive(ProductRequest::class)
                    if (productService.update(productRequest)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/products/delete") {
                    val productId = UUID.fromString(call.parameters["productId"])
                    if (productService.delete(productId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}