package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Patterns
import org.openstreetmap.josm.tools.Logging

class HouseNumberParser : IParser<ParsedHouseNumber> {
    private val patterns = Patterns.byYml("/references/house_patterns.yml").asRegExpList()

    override fun parse(address: String): ParsedHouseNumber {
        for (pattern in patterns) {
            val match = pattern.find(address)

            if (match != null) {
                var houseNumber =
                    match.groups["housenumber"]!!.value.filterNot { it == '"' || it == ' ' || it == '-' || it == '«' || it == '»' }
                        .trim().uppercase()
                val buildingNumber =
                    match.groups["building"]?.value?.filterNot { it == '"' || it == ' ' }?.trim()?.uppercase()
                val corpusNumber =
                    match.groups["corpus"]?.value?.filterNot { it == '"' || it == ' ' }?.trim()?.uppercase()

                if (buildingNumber != null) {
                    //наверное темплейт выхлопа тоже нужно вынести в конфигурацию
                    houseNumber = "$houseNumber с$buildingNumber"
                }

                if (corpusNumber != null) {
                    houseNumber = "$houseNumber к$corpusNumber"
                }

                val flatNumbers1 = match.groups["flat1"]?.value?.filterNot { it == '-' || it == ' ' }
                val flatNumbers2 = match.groups["flat2"]?.value
                val roomNumbers = match.groups["room"]?.value
                val parsedFlats = flatNumbers1 ?: flatNumbers2 ?: ""
                if (StringUtils.isNotBlank(flatNumbers1) || StringUtils.isNotBlank(flatNumbers2) || StringUtils.isNotBlank(roomNumbers)) {
                    Logging.info("EGRN-PLUGIN Parsed and removed flat numbers from address $address : $parsedFlats $roomNumbers ")
                }

                return ParsedHouseNumber(houseNumber, parsedFlats)
            }
            Logging.error("EGRN-PLUGIN Cant parse housenumber from address: $address")
        }

        return ParsedHouseNumber("", "")
    }
}