package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.tools.Logging

class AddressParser : IParser<ParsedAddress> {
    override fun parse(address: String, requestCoordinate: EastNorth, editDataSet: DataSet): ParsedAddress {
        val streetParser = StreetParser()
        val houseNumberParser = HouseNumberParser()
        val placeParser = PlaceParser()

        //предварительная фильтрация - убираем одинарные кавычки, кавычки елочки, и "Россия" из конца адреса (такое тоже есть!)
        var filteredEgrnAddress = address.replace("''", "\"")
            .replace("«", "\"")
            .replace("»", "\"")
            .replace(" ", " ")
            .replace(",,", ",")
            .replace(Regex(""",\s*Россия\s*\.?$"""), "")
            .replace(" .", ". ") //борьба с опечатками

        //убираем все в круглых скобках - неединичные случаи дублирования номера дома прописью
        //Калужская область, Боровский район, деревня Кабицыно, улица А. Кабаевой, дом 25 (Двадцать пять)
        filteredEgrnAddress = filteredEgrnAddress.replace(Regex("""\(([А-Яа-я ])+\)"""), "")

        val placeParseResult = placeParser.parse(filteredEgrnAddress, requestCoordinate, editDataSet)
        val streetParseResult = streetParser.parse(filteredEgrnAddress, requestCoordinate, editDataSet)
        var houseNumberParse = houseNumberParser.parse(filteredEgrnAddress, requestCoordinate, editDataSet)

        //костыли-костылики... эту часть надо будет заменить полноценным тестом на взаимопроникновение частей адреса друг в друга
        if (streetParseResult.flags.contains(ParsingFlags.STREET_HAS_NUMBERED_NAME)
            && houseNumberParser.parse(
                streetParseResult.extractedName,
                requestCoordinate,
            editDataSet).houseNumber == houseNumberParse.houseNumber
        ) {
            Logging.warn("EGRN-PLUGIN Discard housenumber parsing result, because street name ${streetParseResult.extractedName} contains housenumber ${houseNumberParse.houseNumber}")
            houseNumberParse =
                ParsedHouseNumber("", "", null, houseNumberParse.flags.plus(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED))
        }

        val placeParseAddress = "${placeParseResult.extractedType?.name} ${placeParseResult.extractedName}"
        if (placeParseResult.flags.contains(ParsingFlags.PLACE_HAS_NUMBERED_NAME)
            && houseNumberParser.parse(placeParseAddress,
                requestCoordinate,
                editDataSet).houseNumber == houseNumberParse.houseNumber
        ) {
            Logging.warn("EGRN-PLUGIN Discard housenumber parsing result, because place name $placeParseAddress contains housenumber ${houseNumberParse.houseNumber}")
            houseNumberParse =
                ParsedHouseNumber("", "", null, houseNumberParse.flags.plus(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED))
        }

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

    private fun parsedPartsHasSomethingBetweenThem(
        address: String,
        place: ParsedPlace,
        street: ParsedStreet,
        houseNumber: ParsedHouseNumber
    ): Boolean {
        val addressWithoutHouseNumber = houseNumber.removeStartingAt(address)
        val addressWithoutStreet = street.removeEndingWith(addressWithoutHouseNumber)
        val addressWithoutPlace = place.removeEndingWith(addressWithoutHouseNumber)
        return (minOf(addressWithoutStreet.length, addressWithoutPlace.length) > 3)
    }

}