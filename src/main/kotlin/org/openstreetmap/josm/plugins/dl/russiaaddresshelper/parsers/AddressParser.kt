package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.AddressPart
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.AddressParts
import org.openstreetmap.josm.tools.Logging

private val addressParts: AddressParts = AddressParts.byYml("/references/address_parts.yml")

class AddressParser : IParser<ParsedAddress> {
    override fun parse(address: String, requestCoordinate: EastNorth, editDataSet: DataSet): ParsedAddress {
        val streetParser = StreetParser()
        val houseNumberParser = HouseNumberParser()
        val placeParser = PlaceParser()

        //предварительная фильтрация - убираем одинарные кавычки, кавычки елочки, и "Россия" из конца адреса (такое тоже есть!)
        //TO DO сделать список предварительной фильтрации конфигурируемым
        var filteredEgrnAddress = address.replace("''", "\"")
            .replace("«", "\"")
            .replace("»", "\"")
            .replace(" ", " ")
            .replace(",,", ",")
            .replace(Regex(""",\s*Россия\s*\.?$"""), "")
            .replace(" .", ". ") //борьба с опечатками
            .replace("кв. квартира", "кв.") //неоднократно встречающаяся проблема с квартирами
            .replace("кв.квартира", "кв.")

        //убираем все в круглых скобках - неединичные случаи дублирования номера дома прописью
        //Калужская область, Боровский район, деревня Кабицыно, улица А. Кабаевой, дом 25 (Двадцать пять)
        filteredEgrnAddress = filteredEgrnAddress.replace(Regex("""\(([А-Яа-я ])+\)"""), "")

         //     var fullParseResult = fullParse(filteredEgrnAddress)

        var placeParseResult = placeParser.parse(filteredEgrnAddress, requestCoordinate, editDataSet)
        var streetParseResult = streetParser.parse(filteredEgrnAddress, requestCoordinate, editDataSet)
        var houseNumberParse = houseNumberParser.parse(filteredEgrnAddress, requestCoordinate, editDataSet)

        if (isStreetOverlapWithHousenumber(filteredEgrnAddress, streetParseResult, houseNumberParse))
        {
            val addrWithoutNumber = houseNumberParse.removeStartingAt(filteredEgrnAddress)
            val cutStreetParse = streetParser.parse(addrWithoutNumber, requestCoordinate, editDataSet)
            if (!cutStreetParse.flags.contains(ParsingFlags.CANNOT_EXTRACT_STREET_NAME)) {
                Logging.warn("EGRN-PLUGIN Parsed street name ${streetParseResult.extractedName} by removing housenumber ${houseNumberParse.houseNumber}, success ${cutStreetParse.extractedName}")
                streetParseResult = cutStreetParse
            } else {
                Logging.warn("EGRN-PLUGIN Discard housenumber parsing result, because parsed street name ${streetParseResult.extractedName} overlaps with housenumber ${houseNumberParse.houseNumber}")
                houseNumberParse =
                    ParsedHouseNumber(
                        "",
                        "",
                        null,
                        houseNumberParse.flags.plus(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED)
                    )
            }
        }

        val placeParseAddress = "${placeParseResult.extractedType?.name} ${placeParseResult.extractedName}"
        if (isPlaceOverlapWithHousenumber(filteredEgrnAddress, placeParseResult, houseNumberParse)) {
            val addrWithoutNumber = houseNumberParse.removeStartingAt(filteredEgrnAddress)
            val cutPlaceParse = placeParser.parse(addrWithoutNumber, requestCoordinate, editDataSet)
            if (!cutPlaceParse.flags.contains(ParsingFlags.CANNOT_FIND_PLACE_TYPE)) {
                Logging.warn("EGRN-PLUGIN Parsed place name ${streetParseResult.extractedName} by removing housenumber ${houseNumberParse.houseNumber}, success ${cutPlaceParse.extractedName}")
                placeParseResult = cutPlaceParse
            } else {
                Logging.warn("EGRN-PLUGIN Discard housenumber parsing result, because place name $placeParseAddress overlaps with housenumber ${houseNumberParse.houseNumber}")
                houseNumberParse =
                    ParsedHouseNumber(
                        "",
                        "",
                        null,
                        houseNumberParse.flags.plus(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED)
                    )
            }
        }

        if (isStreetAndPlaceClash(filteredEgrnAddress, placeParseResult, streetParseResult)) {
            Logging.warn("EGRN-PLUGIN Discard parsed address, because place name $placeParseAddress clashes with street name  ${streetParseResult.extractedName}")
            streetParseResult = ParsedStreet("",placeParseResult.extractedName, streetParseResult.extractedType, emptyList<OsmPrimitive>(), streetParseResult.flags.plus(ParsingFlags.CANNOT_EXTRACT_STREET_NAME).toMutableList())
            placeParseResult = ParsedPlace("",placeParseResult.extractedName, placeParseResult.extractedType, emptyList<OsmPrimitive>(), streetParseResult.flags.plus(ParsingFlags.CANNOT_FIND_PLACE_TYPE))
            //инвалидировать и номер дома?
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

    private fun isStreetOverlapWithHousenumber(
        address: String,
        street: ParsedStreet,
        houseNumber: ParsedHouseNumber
    ) : Boolean {
        val houseNumberRange = houseNumber.getMatchRange(address)
        if (houseNumberRange == IntRange.EMPTY) return false
        val streetRange = street.getMatchRange(address)
        if (streetRange == IntRange.EMPTY) return false
        return streetRange.last > houseNumberRange.first
    }

    private fun isPlaceOverlapWithHousenumber(
        address: String,
        place: ParsedPlace,
        houseNumber: ParsedHouseNumber
    ) : Boolean {
        val houseNumberRange = houseNumber.getMatchRange(address)
        if (houseNumberRange == IntRange.EMPTY) return false
        val placeRange = place.getMatchRange(address)
        if (placeRange == IntRange.EMPTY) return false
        return placeRange.last > houseNumberRange.first
    }

    private fun isStreetAndPlaceClash(address: String, place: ParsedPlace, street: ParsedStreet): Boolean {
        val placeRange = place.getMatchRange(address)
        if (placeRange == IntRange.EMPTY) return false
        val streetRange = street.getMatchRange(address)
        if (streetRange == IntRange.EMPTY) return false
        //это так же включает неявно случай когда улица распозналась корректно, но левее места (нарушена иерархия частей адреса)
        return streetRange.first <= placeRange.last
    }

    private fun fullParse(address: String) : List<String> {
        //сверхазадача - разобрать произвольный адрес на составляющие
        //составить список всех возможных вариантов разбиения и выбрать из них валидные
        //для дальнейшего сопоставления с адресными данными из ОСМ
        //1. разбиваем строку на подстроки по запятым (HARD_DIVIDERS)
        //2. проходим по подстрокам сопоставителем адресных частей, для каждой подстроки получаем список совпавших адресных частей
        //3. в каждом списке совпавших адресных частей пытаемся навести порядок - для конфликтующих частей пытаемся найти такое разбиение по пробелам, чтобы конфликтующие части распознались
        //3.1
        val numerator = """(?<numerator>\d{1,2}(\s|-)(й|ий|ый|ой|я|ая|ья|е|ое|ье)\s)?"""
        val namedBy ="(имени|им.)?"
        val result : List<String>
        val addrSubstrings = address.split(",").filterNot { a -> a.isBlank() }
        val results : MutableList<MutableList<String>>
    /*    addrSubstrings.forEach{ part ->
            addressParts.parts.forEach{addressPart ->
                val allRegexes = addressPart.getAllRegexes()
                allRegexes.forEach{
                    if (it.find(part)) {
                        results.add
                    }

                }
            }
        }*/
        return listOf()
    }


}