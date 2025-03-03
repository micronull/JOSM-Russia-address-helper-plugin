package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model

import org.openstreetmap.josm.data.preferences.AbstractProperty

/**
 * A property containing a `Map` of `String, String` as value.
 */
class MapStringListProperty(key: String?, defaultValue: Map<String, List<String>>) :
    AbstractProperty<Map<String, List<String>>>(key, defaultValue) {
    init {
        if (getPreferences() != null) {
            get()
        }
    }

    override fun get(): Map<String, List<String>> {
        return getPreferences().getList(getKey(), mapToList(getDefaultValue()))
            .map { Pair(it.split("=")[0], if (it.split("=")[1].contains(",")) it.split("=")[1].split(",") else listOf( it.split("=")[1])) }.toMap()
    }

    override fun put(value: Map<String, List<String>>): Boolean {
        val filtered =
            value.filter { (key, value) -> !key.isBlank() && value.isNotEmpty() }
        return getPreferences().putList(getKey(), mapToList(filtered))
    }

    private fun mapToList(mapOfLists: Map<String, List<String>>): List<String> {
        return mapOfLists.map { (mapkey, mapvalue) -> "$mapkey=${if (mapvalue.size == 1) mapvalue[0] else mapvalue.joinToString (",")}" }
            .toList()
    }
}