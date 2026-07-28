package io.tolgee.intellij.api

class TolgeeApiException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
