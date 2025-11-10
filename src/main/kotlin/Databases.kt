package org.burgas

import io.ktor.server.application.*
import org.burgas.service.Authority
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction

object Identity : Table("identity") {
    val id = uuid("id").autoGenerate()
    val authority = enumerationByName("authority", 10, Authority::class)
    val username = varchar("username", 255).uniqueIndex()
    val password = varchar("password", 255)
    val email = varchar("email", 255).uniqueIndex()
    val biography = text("biography").nullable()
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(id)
}

object Category : Table("category") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255)
    val description = text("description").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Product : Table("product") {
    val id = uuid("id").autoGenerate()
    val categoryId = reference("category_id", Category.id,
        ReferenceOption.SET_NULL, ReferenceOption.CASCADE).nullable()
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val price = double("price").default(0.0)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object Store : Table("store") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255)
    val address = text("address")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object StoreProduct : Table("store_product") {
    val storeId = uuid("store_id")
        .references(Store.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val productId = uuid("product_id")
        .references(Product.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val amount = long("amount").default(0)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(storeId, productId))
}

@Suppress("UnusedReceiverParameter")
fun Application.configureDatabases() {
    val database = Database.connect(
        url = "jdbc:postgresql://localhost:6000/ktor_product_api_db",
        user = "postgres",
        driver = "org.postgresql.Driver",
        password = "postgres"
    )

    transaction(database) {
        SchemaUtils.create(Identity)
        SchemaUtils.create(Category)
        SchemaUtils.create(Product)
        SchemaUtils.create(Store)
        SchemaUtils.create(StoreProduct)
    }
}
