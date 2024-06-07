package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags

class ParsedHouseNumber(val houseNumber: String, val flats: String, val pattern: Regex?, val flags: List<ParsingFlags>) {

    fun removeStartingAt(address: String): String {
        val matchRange = getMatchRange(address)
        val indexOfMatch = matchRange.first ?: address.length
        return address.slice(0..indexOfMatch-2)
    }

    fun getMatchRange(address: String): IntRange {
        if (pattern == null) return  IntRange.EMPTY
        val match = pattern.find(address) ?: return IntRange.EMPTY
        val leftBoundary = match.groups["tag"]?.range?.first?: match.groups["housenumber"]!!.range.first
        return IntRange(leftBoundary, match.groups["housenumber"]!!.range.last)
    }
}