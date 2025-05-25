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
        filters.forEach{ body ->
            if (prefixes.isNotEmpty()) {
                prefixes.forEach { pref ->
                    result.add(Regex("""^\s?(?<tag>$pref)\s?(?<body>$body)\s?$"""))
                }
            } else {
                result.add(Regex("""^\s?(?<body>$body)\s?$"""))
            }
        }

        filters.forEach{ body ->
            if ( postfixes.isNotEmpty()) {
                postfixes.forEach { post ->
                    result.add(Regex("""^\s?(?<body>$body)\s{1,2}(?<tag>$post)\s?$"""))
                }
            } else {
                result.add(Regex("""^\s?(?<body>$body)\s?$"""))
            }
        }

        return result.distinct()
    }


}