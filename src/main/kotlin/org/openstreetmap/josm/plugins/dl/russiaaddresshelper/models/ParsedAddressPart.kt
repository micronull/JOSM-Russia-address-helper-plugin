package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import kotlinx.serialization.Serializable

/**
 * Разобранная часть адреса
 */

data class ParsedAddressPart(
    val type: String,
    val value: String,
    val level: AddressPartLevel,
    val quality : Integer,
    val pattern: Regex,
    val boundary: IntRange,

) {
    companion object

/*    fun getAllRegexes(): List<Regex> {
        val result = mutableListOf<Regex>()
        prefixes.forEach{ pref ->

        }
    }*/
}