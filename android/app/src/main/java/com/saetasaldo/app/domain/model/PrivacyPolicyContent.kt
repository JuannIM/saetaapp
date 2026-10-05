package com.saetasaldo.app.domain.model

data class PolicySection(
    val title: String,
    val content: String
)

object PrivacyPolicyContent {
    val sections: List<PolicySection> = listOf(
        PolicySection(
            title = "1. Privacidad y Almacenamiento Local",
            content = "SAETA Saldo es una aplicación de código abierto diseñada bajo la filosofía 'Local-First'. Todas tus tarjetas, alias y registros de saldo se almacenan exclusivamente en la memoria local de tu dispositivo mediante una base de datos SQLite segura. El desarrollador no dispone de servidores intermedios ni recopila datos en la nube. La conexión con tu cuenta de RedBus es completamente opcional: sin ella la app funciona igual en modo anónimo, y tus datos locales permanecen siempre en el dispositivo."
        ),
        PolicySection(
            title = "2. Consultas a MiRedBus",
            content = "La app ofrece dos formas de consultar tu saldo. En el modo anónimo, siempre disponible, se envía únicamente el número de tarjeta y el código de seguridad (captcha) al portal oficial de MiRedBus Salta mediante conexión cifrada HTTPS. Opcionalmente puedes conectar tu cuenta de RedBus: el inicio de sesión se realiza directamente en la página oficial de RedBus dentro de un WebView protegido, y SAETA Saldo nunca recibe, lee ni almacena tu contraseña. Las cookies de sesión del portal se guardan solo en el almacenamiento privado de la app y se usan exclusivamente para consultar tus tarjetas vinculadas sin captcha: se leen únicamente el número de tarjeta vinculada, el saldo principal, el tipo y el estado de la tarjeta. No se recopilan identificadores de dispositivo, ubicación ni datos personales."
        ),
        PolicySection(
            title = "3. Reconocimiento de Captcha en el Dispositivo",
            content = "La resolución automática de códigos captcha se procesa íntegramente de forma local mediante Google ML Kit Text Recognition en tu propio teléfono. Las imágenes nunca se suben ni se comparten con servicios externos."
        ),
        PolicySection(
            title = "4. Permisos Requeridos",
            content = "• NFC: Se utiliza únicamente para detectar y leer el identificador de tu tarjeta SAETA cuando la acercas al teléfono.\n• INTERNET: Se utiliza exclusivamente para consultar el saldo en el portal de RedBus.\n• ACCESS_NETWORK_STATE: Para verificar si el dispositivo cuenta con conexión antes de consultar."
        ),
        PolicySection(
            title = "5. Control y Eliminación de Datos",
            content = "Tú tienes el control total sobre tus datos. Puedes editar o eliminar cualquier tarjeta en cualquier momento; al eliminarla, se borra instantáneamente todo su historial asociado. Si conectaste tu cuenta de RedBus, la opción \"Desconectar\" elimina las cookies de sesión del portal del dispositivo; tus tarjetas e historial locales permanecen intactos. Al desinstalar la app, todos los datos se destruyen permanentemente del dispositivo."
        ),
        PolicySection(
            title = "6. Descargo de Responsabilidad",
            content = "Esta aplicación es una herramienta independiente y comunitaria de código abierto. No está afiliada, respaldada ni asociada oficialmente con SAETA S.A., MiRedBus ni con el Gobierno de la Provincia de Salta."
        )
    )

    val permissionsJustification: Map<String, String> = mapOf(
        "NFC" to "Lectura del chip contactless de la tarjeta física SAETA para vincularla rápidamente.",
        "INTERNET" to "Comunicación cifrada HTTPS con el portal oficial para consultar el saldo.",
        "ACCESS_NETWORK_STATE" to "Detección del estado de conectividad para evitar errores de red innecesarios."
    )
}
