package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.*

@kotlinx.serialization.Serializable
data class EGRNResponse(val total: Int, val results: List<EGRNFeature>) {
    fun parseAddresses(requestCoordinate: EastNorth): ParsedAddressInfo {
        val streetParser = StreetParser()
        val houseNumberParser = HouseNumberParser()
        val placeParser = PlaceParser()
        val addresses: MutableList<ParsedAddress> = mutableListOf()
        val existingAddresses: MutableList<String> = mutableListOf()
        this.results.forEach { res ->
            val egrnAddress = res.attrs?.address ?: return@forEach
            //предварительная фильтрация - убираем одинарные кавычки, кавычки елочки, и "Россия" из конца адреса (такое тоже есть!)
            var filteredEgrnAddress =  egrnAddress.replace("''","\"")
                .replace("«","\"")
                .replace("»", "\"")
                .replace(" ", " ")
                .replace(Regex(""",\s*Россия\s*\.?$"""),"")

            //убираем все в круглых скобках - неединичные случаи дублирования номера дома прописью
            //Калужская область, Боровский район, деревня Кабицыно, улица А. Кабаевой, дом 25 (Двадцать пять)
            filteredEgrnAddress = filteredEgrnAddress.replace(Regex("""\(([А-Яа-я ])+\)"""),"")

            val placeParseResult = placeParser.parse(filteredEgrnAddress, requestCoordinate)
            val streetParseResult = streetParser.parse(filteredEgrnAddress, requestCoordinate)
            val houseNumberParse = houseNumberParser.parse(filteredEgrnAddress, requestCoordinate)
            //TO DO : проверки на "взаимопроникновение" частей адреса друг в друга
            val parsedOsmAddress =
                OSMAddress(placeParseResult.name, streetParseResult.name, houseNumberParse.housenumber, houseNumberParse.flats)
            val allParsingFlags: MutableList<ParsingFlags> = mutableListOf()
            //возможно, тут стоит подавлять добавление флагов парсинга места, если улица успешно распозналась?
            allParsingFlags.addAll(placeParseResult.flags)
            allParsingFlags.addAll(streetParseResult.flags)
            allParsingFlags.addAll(houseNumberParse.flags)
            if (res.type == EGRNFeatureType.BUILDING.type) {
                allParsingFlags.add(ParsingFlags.IS_BUILDING)
            }

            val key = parsedOsmAddress.getInlineAddress() ?: "${placeParseResult.extractedName} ${placeParseResult.extractedType} ${streetParseResult.extractedName} ${streetParseResult.extractedType}"
            if (!existingAddresses.contains(key)) {
                addresses.add(ParsedAddress(placeParseResult, streetParseResult, houseNumberParse, filteredEgrnAddress, allParsingFlags))
                existingAddresses.add(key)
            }

           /* //for testing purposes
            val fakeHouseNum = ParsedHouseNumber(houseNumberParse.housenumber+"Ю","", listOf())
            val fakeHouseNum2 = ParsedHouseNumber(houseNumberParse.housenumber+"Ь","", listOf())
            addresses.add(ParsedAddress(placeParseResult, streetParseResult, fakeHouseNum, filteredEgrnAddress+" Ю", allParsingFlags.plus(ParsingFlags.IS_BUILDING)))
            addresses.add(ParsedAddress(placeParseResult, streetParseResult, fakeHouseNum2, filteredEgrnAddress+" Ь", allParsingFlags.plus(ParsingFlags.IS_BUILDING)))*/
        }
        return ParsedAddressInfo(addresses)
    }
}