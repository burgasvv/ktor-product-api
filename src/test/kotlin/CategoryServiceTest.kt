import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.burgas.module
import org.junit.Test
import kotlin.test.assertEquals

class CategoryServiceTest {

    @Test
    fun findAll() = testApplication {
        application {
            module()
        }

        val httpResponse = client.get("http://localhost:80/api/v1/categories")
        assertEquals(HttpStatusCode.OK, httpResponse.status)
    }
}