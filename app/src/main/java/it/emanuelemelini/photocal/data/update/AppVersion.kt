package it.emanuelemelini.photocal.data.update

/**
 * Semantic version ("1.2.0", also as a tag "v1.2.0"). Pre-release and build suffixes
 * ("-beta", "+abc") are ignored; missing parts count as 0, so "1.2" == "1.2.0".
 */
class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val diff = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString(): String = parts.joinToString(".")

    companion object {
        /** null when the text isn't a version (e.g. a tag that isn't a release). */
        fun parse(text: String): AppVersion? {
            val core = text.trim().removePrefix("v").substringBefore('-').substringBefore('+')
            val parts = core.split('.').map { it.toIntOrNull()?.takeIf { part -> part >= 0 } ?: return null }
            return if (parts.isEmpty()) null else AppVersion(parts)
        }
    }
}
