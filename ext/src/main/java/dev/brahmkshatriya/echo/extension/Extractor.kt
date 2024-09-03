package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Streamable

abstract class Extractor {
    abstract suspend fun extract(link: String): List<Streamable>
}