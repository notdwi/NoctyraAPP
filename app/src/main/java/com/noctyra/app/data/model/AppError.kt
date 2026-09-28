package com.noctyra.app.data.model

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Erro com mensagem já pronta para o usuário. Nunca deve citar sites, hosts ou detalhes técnicos. */
class AppError(message: String) : Exception(message)

fun Throwable.userMessage(fallback: String = "Algo deu errado. Tente de novo."): String = when (this) {
    is AppError -> message ?: fallback
    is UnknownHostException, is ConnectException, is NoRouteToHostException ->
        "Sem conexão com a internet. Verifique o Wi‑Fi ou os dados móveis."
    is InterruptedIOException -> "A conexão está lenta. Tente de novo em instantes."
    is SSLException -> "Não foi possível criar uma conexão segura. Tente de novo."
    is SocketException, is IOException -> "Falha de conexão. Tente de novo."
    else -> fallback
}

fun Throwable.toAppError(fallback: String = "Algo deu errado. Tente de novo."): AppError =
    this as? AppError ?: AppError(userMessage(fallback))
