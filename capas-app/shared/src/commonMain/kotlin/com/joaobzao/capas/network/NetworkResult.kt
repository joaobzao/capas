package com.joaobzao.capas.network

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed class NetworkResult<T>(
    val data: T? = null,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
    val exception: Throwable? = null
) {
    class Success<T>(data: T) : NetworkResult<T>(data)
    class Error<T>(code: Int, errorMessage: String?) : NetworkResult<T>(
        errorCode = code,
        errorMessage = errorMessage
    )
    class Exception<T>(exception: Throwable?) : NetworkResult<T>(
        exception = exception
    )
}

suspend fun <T : Any> apiCall(apiCall: suspend () -> T): NetworkResult<T> {
    return try {
        val data = apiCall.invoke()
        return NetworkResult.Success(data)
    } catch (exception: ClientRequestException) {
        val errorBody = exception.response.toString()
        val errorMessage = extractErrorMessage(errorBody) ?: "Status Code: ${exception.response.status.value}"
        NetworkResult.Error(
            code = exception.response.status.value,
            errorMessage = errorMessage
        )
    } catch (exception: HttpRequestTimeoutException) {
        NetworkResult.Error(
            code = 200,
            errorMessage = exception.message
        )
    } catch (exception: HttpExceptions) {
        val errorBody = exception.cachedResponseText
        val errorMessage = extractErrorMessage(errorBody) ?: exception.message
        NetworkResult.Error(
            code = exception.response.status.value,
            errorMessage = errorMessage
        )
    } catch (exception: SerializationException) {
        NetworkResult.Error(
            code = 200,
            errorMessage = "Failed to Serialize: ${exception.message}"
        )
    } catch (exception: Exception) {
        NetworkResult.Error(
            code = 200,
            errorMessage = "Generic network error: ${exception.message}"
        )
    } catch (exception: Throwable) {
        NetworkResult.Error(
            code = 200,
            errorMessage = "Generic network error: ${exception.message}"
        )
    } finally {
        Logger.i { "finnally called" }
    }
}

private fun extractErrorMessage(errorBody: String): String? {
    return try {
        val jsonElement = Json.parseToJsonElement(errorBody)
        val jsonObject = jsonElement as? JsonObject
        jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    } catch (e: Exception) {
        null // If parsing fails, return null
    }
}

class HttpExceptions(
    response: HttpResponse,
    failureReason: String?,
    val cachedResponseText: String
) : ResponseException(response, cachedResponseText) {
    override val message: String = "Status: ${response.status}." + " Failure: $failureReason"
}
