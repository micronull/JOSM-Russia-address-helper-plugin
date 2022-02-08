package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Patterns
import org.openstreetmap.josm.tools.Logging

class HouseNumberParser : IParser<String> {
    private val patterns = Patterns.byYml("/references/house_patterns.yml").asRegExpList()

    override fun parse(address: String): String {
        for (pattern in patterns) {
            val match = pattern.find(address)

            if (match != null) {
                return match.groups["housenumber"]!!.value.filterNot { it =='"' || it ==' ' }.trim().uppercase()
            }
            Logging.info("Cant parse housenumber from address: $address")
        }

        return ""
    }
}