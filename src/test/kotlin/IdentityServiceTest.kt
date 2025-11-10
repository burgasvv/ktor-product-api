import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.*
import org.burgas.module
import org.junit.Test
import java.util.Base64
import kotlin.test.assertEquals

class IdentityServiceTest {

    @Test
    fun findAll() = testApplication {
        application {
            module()
        }
        val httpResponse = client.get("http://localhost:80/api/v1/identities") {
            this.headers[HttpHeaders.Authorization] = "Basic " + Base64.getEncoder().encodeToString(
                "burgasvv@gmail.com:burgasvv".toByteArray(Charsets.UTF_8)
            )
        }
        assertEquals(HttpStatusCode.OK, httpResponse.status)
    }
}