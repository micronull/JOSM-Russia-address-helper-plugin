package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import kotlinx.serialization.Serializable

/**
 * Тип Осм обьекта типа "место" - населенный пункт, либо площадной адрес - квартал, микрорайон
 */
@Serializable
data class PlaceType(
    val name: String,
    val osm: Patterns,
    val tags: MutableMap<String, List<String>>,
    val egrn: Patterns
) {
    companion object
    fun hasOSMMatch(osmName: String): Boolean {
        return osm.asRegExpList().find { it.find(osmName) != null } != null
    }

    fun hasEgrnMatch(address: String): Boolean {
        return egrn.asRegExpList().find { it.find(address) != null } != null
    }
}