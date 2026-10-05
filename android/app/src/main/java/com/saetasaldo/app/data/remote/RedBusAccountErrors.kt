package com.saetasaldo.app.data.remote

sealed class RedBusAccountException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RedBusNotConnectedException : RedBusAccountException(
    "No hay una cuenta de RedBus conectada."
)

class RedBusSessionExpiredException : RedBusAccountException(
    "La sesión de RedBus expiró. Volvé a iniciar sesión."
)

class RedBusHttpException(val statusCode: Int) : RedBusAccountException(
    "No se pudo conectar con RedBus (código $statusCode)."
)

class RedBusContractException(cause: Throwable? = null) : RedBusAccountException(
    "La respuesta de RedBus no tiene el formato esperado.",
    cause
)

class RedBusNetworkException(cause: Throwable) : RedBusAccountException(
    "Falló la conexión con RedBus. Reintentá en unos segundos.",
    cause
)
