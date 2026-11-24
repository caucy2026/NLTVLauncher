package com.lakefront.tvlauncher.logic

/**
 * Stable identity of a launchable activity. Stored as `package/class`.
 * Class names do not contain slashes; the first slash is the separator.
 */
data class ComponentKey(
    val packageName: String,
    val activityName: String,
) {
    fun flatten(): String = "$packageName/$activityName"

    companion object {
        fun parse(raw: String): ComponentKey? {
            val trimmed = raw.trim()
            val slash = trimmed.indexOf('/')
            if (slash <= 0 || slash >= trimmed.length - 1) return null
            val pkg = trimmed.substring(0, slash)
            val cls = trimmed.substring(slash + 1)
            if (pkg.isBlank() || cls.isBlank() || '/' in cls) return null
            return ComponentKey(pkg, cls)
        }
    }
}

object PinCodec {
    fun encode(list: List<ComponentKey>): String = list.joinToString("\n") { it.flatten() }

    fun decode(raw: String?): List<ComponentKey> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<ComponentKey>()
        raw.lineSequence().forEach { line ->
            val key = ComponentKey.parse(line) ?: return@forEach
            seen.add(key)
        }
        return seen.toList()
    }
}
