package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.tools.Logging

class AddressParser : IParser<ParsedAddress> {
    override fun parse(address: String, requestCoordinate: EastNorth): ParsedAddress {
        val streetParser = StreetParser()
        val houseNumberParser = HouseNumberParser()
        val placeParser = PlaceParser()

        //предварительная фильтрация - убираем одинарные кавычки, кавычки елочки, и "Россия" из конца адреса (такое тоже есть!)
        var filteredEgrnAddress = address.replace("''", "\"")
            .replace("«", "\"")
            .replace("»", "\"")
            .replace(" ", " ")
            .replace(Regex(""",\s*Россия\s*\.?$"""), "")

        //убираем все в круглых скобках - неединичные случаи дублирования номера дома прописью
        //Калужская область, Боровский район, деревня Кабицыно, улица А. Кабаевой, дом 25 (Двадцать пять)
        filteredEgrnAddress = filteredEgrnAddress.replace(Regex("""\(([А-Яа-я ])+\)"""), "")

        val placeParseResult = placeParser.parse(filteredEgrnAddress, requestCoordinate)
        val streetParseResult = streetParser.parse(filteredEgrnAddress, requestCoordinate)
        var houseNumberParse = houseNumberParser.parse(filteredEgrnAddress, requestCoordinate)
        if (streetParseResult.flags.contains(ParsingFlags.STREET_HAS_NUMBERED_NAME)
            && houseNumberParser.parse(
                streetParseResult.extractedName,
                requestCoordinate
            ).housenumber == houseNumberParse.housenumber
        ) {
            Logging.warn("EGRN-PLUGIN Discard housenumber parsing result, because street name ${streetParseResult.extractedName} contains housenumber ${houseNumberParse.housenumber}")
            houseNumberParse =
                ParsedHouseNumber("", "", houseNumberParse.flags.plus(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED))
        }
        val parsedOsmAddress =
            OSMAddress(
                placeParseResult.name,
                streetParseResult.name,
                houseNumberParse.housenumber,
                houseNumberParse.flats
            )
        val allParsingFlags: MutableList<ParsingFlags> = mutableListOf()
        //возможно, тут стоит подавлять добавление флагов парсинга места, если улица успешно распозналась?
        allParsingFlags.addAll(placeParseResult.flags)
        allParsingFlags.addAll(streetParseResult.flags)
        allParsingFlags.addAll(houseNumberParse.flags)
        return ParsedAddress(
            placeParseResult,
            streetParseResult,
            houseNumberParse,
            filteredEgrnAddress,
            allParsingFlags
        )
    }
}