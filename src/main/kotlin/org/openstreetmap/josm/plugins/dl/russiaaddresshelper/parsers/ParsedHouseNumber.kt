package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags

class ParsedHouseNumber(val houseNumber: String, val flats: String, val pattern: Regex?, val flags: List<ParsingFlags>) {

    fun removeStartingAt(address: String): String {
        if (pattern == null) return address
        val match = pattern.find(address) ?: return address
        val indexOfMatch = match.groups["housenumber"]?.range?.first ?: address.length
        return address.slice(0..indexOfMatch-2)
    }
}