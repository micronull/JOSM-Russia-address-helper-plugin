package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import org.apache.commons.lang3.StringUtils

data class OSMAddress(val street: String, val housenumber: String, val flatnumber: String = "") {

    fun getTags(): Map<String, String> {
        val tags: Map<String, String> =
            mapOf("addr:street" to street, "addr:housenumber" to housenumber, "addr:flats" to flatnumber)
        return tags.filter { StringUtils.isNotBlank(it.value) }
    }

    fun getBaseAddressTags(): Map<String, String> {
        val tags: Map<String, String> =
            mapOf("addr:street" to street, "addr:housenumber" to housenumber)
        return tags.filter { StringUtils.isNotBlank(it.value) }
    }

    fun getInlineAddress(): String? {
        if (StringUtils.isNotBlank(street) && StringUtils.isNotBlank(housenumber)) {
            return "$street $housenumber $flatnumber"
        }
        return null
    }
}
