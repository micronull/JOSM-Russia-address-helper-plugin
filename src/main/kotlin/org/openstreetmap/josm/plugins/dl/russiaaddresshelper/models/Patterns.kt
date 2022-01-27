package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

/**
 * Список regexp паттернов извлечения наименований улиц и номеров домов.
 */
@Serializable data class Patterns(val patterns: List<String>) {
    companion object {
        private val regexOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.UNIX_LINES)

        /**
         * Парсим yml файл и возвращаем экземпляр класса Patterns
         *
         * @param finePath путь до yml файла
         */
        fun byYml(finePath: String): Patterns {
            val rawStr = this::class.java.getResource(finePath)!!.readText()

            return Yaml.default.decodeFromString(serializer(), rawStr)
        }
    }

    fun asRegExpList(): Collection<Regex> {
        val patterns: MutableCollection<Regex> = mutableListOf()

        for (pattern in this.patterns) {
            patterns.add(pattern.toRegex(regexOptions))
        }

        return patterns.toList()
    }
}