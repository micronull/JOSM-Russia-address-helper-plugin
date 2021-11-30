package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.patterns.HousePatterns

class HouseNumberParser : Parser {
    private val patterns = HousePatterns.makeList()

    override fun parse(egrnAddress: String): String {
        for (pattern in patterns) {
            val match = pattern.find(egrnAddress)

            if (match != null) {
                return match.groups["housenumber"]!!.value.trim().uppercase()
            }
        }

        return ""
    }
}