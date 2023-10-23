package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Patterns
import org.openstreetmap.josm.tools.Logging

class HouseNumberParser : IParser<ParsedHouseNumber> {
    private val patterns = Patterns.byYml("/references/house_patterns.yml").asRegExpList()

    override fun parse(address: String, requestCoordinate: EastNorth, editDataSet: DataSet): ParsedHouseNumber {
        val parsingFlags = mutableListOf<ParsingFlags>()
        for (pattern in patterns) {
            val match = pattern.find(address)

            if (match != null) {

                var houseNumber =
                    match.groups["housenumber"]!!.value.filterNot { it == '"' || it == ' ' || it == '-' || it == '«' || it == '»' }
                        .trim().uppercase()
                if (houseNumber.matches(Regex("""\d{4,}"""))) {
                    //это пока не работает поскольку регекс для номера дома изменился, надо откат?
                    Logging.error("EGRN-PLUGIN Cant parse housenumber from address: $address, housenumber too big")
                    parsingFlags.add(ParsingFlags.HOUSENUMBER_TOO_BIG)
                    return ParsedHouseNumber("", "", null, parsingFlags)
                }
                val letter = match.groups["letter"]?.value?.trim()?:""
                houseNumber += letter
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
                    parsingFlags.add(ParsingFlags.HOUSENUMBER_HAS_FLATS)
                    Logging.info("EGRN-PLUGIN Parsed and removed flat numbers from address $address : $parsedFlats $roomNumbers ")
                }

                return ParsedHouseNumber(houseNumber, parsedFlats, pattern, parsingFlags)
            }
            Logging.error("EGRN-PLUGIN Cant parse housenumber from address: $address")
        }
        if (address.matches(Regex("""\d"""))) {
            Logging.error("EGRN-PLUGIN Cant parse housenumber from address: $address, though address contains some numbers")
            parsingFlags.add(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED_BUT_CONTAINS_NUMBERS)
        } else {
            parsingFlags.add(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED)
        }
        return ParsedHouseNumber("", "", null, parsingFlags)
    }
}