package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import org.apache.commons.lang3.StringUtils

data class OSMAddress(val place: String, val street: String, val housenumber: String, val flatnumber: String = "") {

    fun getTags(source: String = "ЕГРН"): Map<String, String> {
        var tags = getBaseAddressTagsWithSource(source)
        tags = tags.plus(Pair("addr:flats", flatnumber))
        return tags.filter { StringUtils.isNotBlank(it.value) }
    }

    fun getBaseAddressTags(): Map<String, String> {
        var tags: Map<String, String> =
            mutableMapOf("addr:housenumber" to housenumber)
        tags = if (StringUtils.isNotBlank(street)) {
            tags.plus(Pair("addr:street", street))
        } else {
            tags.plus(Pair("addr:place", place))
        }
        return tags.filter { StringUtils.isNotBlank(it.value) }
    }

    fun getBaseAddressTagsWithSource(source: String = "ЕГРН"): Map<String, String> {
        var tags = getBaseAddressTags()
        tags = tags.plus(Pair("source:addr", source))
        return tags
    }

    fun getInlineAddress(separator: String=""): String? {
        if (isValid()) {
            return if (StringUtils.isNotBlank(street)) {
                "$street$separator $housenumber" + if (StringUtils.isNotBlank(flatnumber)) {"$separator $flatnumber"} else { "" }
            } else {
                "$place$separator $housenumber" + if (StringUtils.isNotBlank(flatnumber)) {"$separator $flatnumber"} else { "" }
            }
        }
        return null
    }

    fun isValid(): Boolean {
        return (StringUtils.isNotBlank(street) || StringUtils.isNotBlank(place)) && StringUtils.isNotBlank(housenumber)
    }

}
