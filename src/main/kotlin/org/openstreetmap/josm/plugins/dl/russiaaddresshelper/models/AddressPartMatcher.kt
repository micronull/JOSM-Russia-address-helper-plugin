package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import kotlinx.serialization.Serializable

/**
 * Описание части адреса
 */
@Serializable
data class AddressPartMatcher(
    val name: String,
    val level: AddressPartLevel,
    //val osm: Patterns,
    //val tags: MutableMap<String, List<String>>,
    val prefixes: List<String>,
    val filters: List<String>,
    val postfixes: List<String>,
    val numerable: Boolean,
    val namedBy: Boolean
) {
    companion object

    fun getAllRegexes(): List<Regex> {
        val templatePrefix = """^\s?(?<tag>)\s?(?<body>)\s$"""
        val templatePostfix = """^\s?<body>\s<tag>\s$"""
        val result = mutableListOf<Regex>()
        prefixes.forEach{ pref ->
            filters.forEach { body ->
                result.add(Regex("""^\s?(?<tag>$pref)\s?(?<body>$body)\s?$"""))
            }
        }
        postfixes.forEach{ post ->
            filters.forEach { body ->
                result.add(Regex("""^\s?(?<body>$body)\s{1,2}(?<tag>$post)\s?$"""))
            }
        }
        return result.distinct()
    }


}