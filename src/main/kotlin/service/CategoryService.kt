package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.burgas.Category
import org.burgas.Product
import org.burgas.UUIDSerializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*
import kotlin.coroutines.coroutineContext

@Serializable
data class CategoryRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class CategoryShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class CategoryFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val products: List<ProductShortResponse>? = null
)

fun ResultRow.toCategoryShortResponse(): CategoryShortResponse {
    return CategoryShortResponse(
        id = this[Category.id],
        name = this[Category.name],
        description = this[Category.description]
    )
}

fun ResultRow.toCategoryFullResponse(products: List<ProductShortResponse>): CategoryFullResponse {
    return CategoryFullResponse(
        id = this[Category.id],
        name = this[Category.name],
        description = this[Category.description],
        products = products
    )
}

fun InsertStatement<Number>.toCategory(categoryRequest: CategoryRequest) {
    this[Category.name] = categoryRequest.name ?: throw NullPointerException("Name is null")
    this[Category.description] = categoryRequest.description ?: throw NullPointerException("Description is null")
}

fun UpdateStatement.toCategory(categoryId: UUID, categoryRequest: CategoryRequest) {
    val resultRow = Category.select(Category.fields)
        .where { Category.id eq categoryId }
        .singleOrNull()
    if (resultRow != null) {
        this[Category.id] = categoryId
        this[Category.name] = categoryRequest.name ?: resultRow[Category.name]
        this[Category.description] = categoryRequest.description ?: resultRow[Category.description]

    } else {
        throw NullPointerException("Category is null")
    }
}

class CategoryService {

    suspend fun findAll(): List<CategoryShortResponse> = newSuspendedTransaction(
        readOnly = true, transactionIsolation = 2
    ) {
        Category.selectAll().map {
            it.toCategoryShortResponse()
        }
    }

    suspend fun findById(categoryId: UUID): List<CategoryFullResponse> = newSuspendedTransaction(
        readOnly = true, transactionIsolation = 2, context = Dispatchers.IO
    ) {
        val products = (Category leftJoin Product)
            .select(Product.fields + Category.fields)
            .where { Category.id eq categoryId }
            .map { resultRow ->
                val categoryShortResponse = resultRow.toCategoryShortResponse()
                resultRow.toProductShortResponse(categoryShortResponse)
            }
            .toList()
        Category.select(Category.fields)
            .where { Category.id eq categoryId }
            .map { it.toCategoryFullResponse(products) }
            .toList()
    }

    suspend fun create(categoryRequest: CategoryRequest) = newSuspendedTransaction(transactionIsolation = 2) {
        Category.insert { insertStatement ->
            insertStatement.toCategory(categoryRequest)
        }
    }

    suspend fun update(categoryRequest: CategoryRequest): Boolean = newSuspendedTransaction(transactionIsolation = 2) {
        val categoryId = categoryRequest.id ?: throw NullPointerException("Category id is null")
        Category.update({ Category.id eq categoryId }) { updateStatement ->
            updateStatement.toCategory(categoryId, categoryRequest)
        } > 0
    }

    suspend fun delete(categoryId: UUID): Boolean = newSuspendedTransaction(transactionIsolation = 2) {
        Category.deleteWhere { Category.id eq categoryId } > 0
    }
}

fun Application.configureCategoryRouter() {

    val categoryService = CategoryService()

    routing {

        route("/api/v1") {

            get("/categories") {
                call.respond(HttpStatusCode.OK, categoryService.findAll())
            }

            get("/categories/by-id") {
                val categoryId = UUID.fromString(call.parameters["categoryId"])
                call.respond(HttpStatusCode.OK, categoryService.findById(categoryId))
            }

            authenticate("basic-auth-admin") {

                post("/categories/create") {
                    val categoryRequest = call.receive(CategoryRequest::class)
                    categoryService.create(categoryRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/categories/update") {
                    val categoryRequest = call.receive(CategoryRequest::class)
                    if (categoryService.update(categoryRequest)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete("/categories/delete") {
                    val categoryId = UUID.fromString(call.parameters["categoryId"])
                    if (categoryService.delete(categoryId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}