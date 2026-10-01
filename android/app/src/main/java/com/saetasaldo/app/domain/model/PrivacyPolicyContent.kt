package com.saetasaldo.app.domain.model

data class PolicySection(
    val title: String,
    val content: String
)

object PrivacyPolicyContent {
    val sections: List<PolicySection> = listOf(
        PolicySection(
            title = "1. Privacidad y Almacenamiento Local",
            content = "SAETA Saldo es una aplicación de código abierto diseñada bajo la filosofía 'Local-First'. Todas tus tarjetas, alias y registros de saldo se almacenan exclusivamente en la memoria local de tu dispositivo mediante una base de datos SQLite segura. No existen servidores intermedios, cuentas de usuario ni recolección de datos en la nube."
        ),
        PolicySection(
            title = "2. Consultas a MiRedBus",
            content = "Para consultar el saldo disponible, la aplicación se comunica directamente mediante conexión cifrada HTTPS con el portal oficial de MiRedBus Salta. Únicamente se envía el número de tarjeta y el código de seguridad (captcha) para obtener la respuesta. No se recopilan identificadores de dispositivo, ubicación ni datos personales."
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
            content = "Tú tienes el control total sobre tus datos. Puedes editar o eliminar cualquier tarjeta en cualquier momento; al eliminarla, se borra instantáneamente todo su historial asociado. Al desinstalar la app, todos los datos se destruyen permanentemente del dispositivo."
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
