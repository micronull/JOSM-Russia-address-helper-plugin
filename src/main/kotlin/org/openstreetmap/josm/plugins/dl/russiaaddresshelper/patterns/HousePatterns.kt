package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.patterns

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

class HousePatterns {
    companion object {
        private const val PATTERNS_FILE_NAME = "/references/house_patterns.yml"

        fun makeList(): Collection<Regex> {
            @Serializable data class RawPatterns(
                val patterns: List<String>
            )

            val rawStr = this::class.java.getResource(PATTERNS_FILE_NAME)!!.readText()
            val rawPatterns = Yaml.default.decodeFromString(RawPatterns.serializer(), rawStr)
            val options = setOf(RegexOption.IGNORE_CASE, RegexOption.UNIX_LINES)
            val patterns: MutableCollection<Regex> = mutableListOf()

            for (pattern in rawPatterns.patterns) {
                patterns.add(pattern.toRegex(options))
            }

            return patterns.toList()
        }
    }
}