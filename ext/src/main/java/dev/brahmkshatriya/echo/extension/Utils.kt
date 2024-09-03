package dev.brahmkshatriya.echo.extension

import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

object Mapper : ResponseParser {

    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @OptIn(InternalSerializationApi::class)
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return json.decodeFromString(kClass.serializer(), text)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return runCatching {
            parse(text, kClass)
        }.getOrNull()
    }

    override fun writeValueAsString(obj: Any): String {
        return json.encodeToString(obj)
    }

    inline fun <reified T> parse(text: String): T {
        return json.decodeFromString(text)
    }
}

private val okHttpClient = OkHttpClient.Builder()
    .followRedirects(true)
    .followSslRedirects(true)
    .build()
private val defaultHeaders = mapOf(
    "User-Agent" to
            "Mozilla/5.0 (Linux; Android %s; %s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Mobile Safari/537.36"
                .format(12, 2)
)
val client = Requests(
    okHttpClient,
    defaultHeaders,
    defaultCacheTime = 6,
    defaultCacheTimeUnit = TimeUnit.HOURS,
    responseParser = Mapper
)

fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")
