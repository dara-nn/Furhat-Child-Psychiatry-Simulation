package furhatos.app.openaichat.setting

fun envOrProperty(name: String): String? {
    val env = System.getenv(name)?.trim()
    if (!env.isNullOrBlank()) {
        return env
    }
    val prop = System.getProperty(name)?.trim()
    if (!prop.isNullOrBlank()) {
        return prop
    }
    return null
}

fun envOrDefault(name: String, default: String): String {
    return envOrProperty(name) ?: default
}
