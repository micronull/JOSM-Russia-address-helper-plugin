package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Patterns
import org.openstreetmap.josm.tools.Logging

class HouseNumberParser : IParser<String> {
    private val patterns = Patterns.byYml("/references/house_patterns.yml").asRegExpList()

    override fun parse(address: String): String {
        for (pattern in patterns) {
            val match = pattern.find(address)

            if (match != null) {
                var houseNumber =
                    match.groups["housenumber"]!!.value.filterNot { it == '"' || it == ' ' }.trim().uppercase()
                val buildingNumber =
                    match.groups["building"]?.value?.filterNot { it == '"' || it == ' ' }?.trim()?.uppercase()
                if (buildingNumber != null) {
                    //наверное темплейт выхлопа тоже нужно вынести в конфигурацию
                    houseNumber = "$houseNumber с$buildingNumber"
                }
                val flatNumbers1 = match.groups["flat1"]?.value?.filterNot { it == '-' }
                val flatNumbers2 = match.groups["flat2"]?.value
                val roomNumbers = match.groups["room"]?.value
                if (StringUtils.isNotBlank(flatNumbers1) || StringUtils.isNotBlank(flatNumbers2) || StringUtils.isNotBlank(roomNumbers)) {
                    Logging.info("EGRN-PLUGIN Parsed and removed flat numbers from address $address : ${flatNumbers1 ?: ""} ${flatNumbers2 ?: ""} $roomNumbers ")
                }

                return houseNumber
            }
            Logging.error("EGRN-PLUGIN Cant parse housenumber from address: $address")
        }

        return ""
    }
}