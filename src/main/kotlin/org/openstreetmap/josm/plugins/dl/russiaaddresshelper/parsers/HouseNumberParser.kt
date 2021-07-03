package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

class HouseNumberParser: Parser {
    override fun parse(egrnAddress: String): String {
        for (pattern in regexList) {
            val match = pattern.find(egrnAddress)

            if (match != null) {
                return match.groups["housenumber"]!!.value.uppercase()
            }
        }

        return ""
    }
}