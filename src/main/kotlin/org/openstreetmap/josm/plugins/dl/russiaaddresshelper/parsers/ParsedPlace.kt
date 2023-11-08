package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceTypes
import org.openstreetmap.josm.tools.Logging

//TO DO LOW PRIORITY в коде ParsedPlace и ParsedStreet слишком много дублирования
//подумать над рефакторингом этого кода на общих основаниях - общий список правил, общий парсер,
//общая процедура матчинга. Возвращаться должен обьект с разложенным в иерархию адресом
//проблема - текущая версия кода вернет только 1 распознанный адрес по месту, даже если таких частей больше 1
data class ParsedPlace(
    val name: String,
    val extractedName: String, //целевое решение - должен быть список обьектов типа AddressPart: name type priority
    val extractedType: PlaceType?,
    val matchedPrimitives: List<OsmPrimitive>,
    val flags: List<ParsingFlags>
) {
    companion object {
        fun identify(
            address: String,
            placeTypes: PlaceTypes,
            primitivesToCompare: MutableMap<String, Map<String, List<OsmPrimitive>>>
        ): ParsedPlace {
            if (StringUtils.isBlank(address)) {
                Logging.error("EGRN-PLUGIN address is empty")
                return emptyParsedPlace()
            }
            if (placeTypes.types.isEmpty()) {
                Logging.error("EGRN-PLUGIN no PlaceTypes to match by")
                return emptyParsedPlace()
            }
            if (primitivesToCompare.isEmpty()) {
                Logging.error("EGRN-PLUGIN no primitives to compare with")
                return emptyParsedPlace()
            }

            val filteredEgrnAddress = address.replace("ё", "е").replace("\"", "")
            val flags: MutableList<ParsingFlags> = mutableListOf()

            val parsedPlaceType = placeTypes.types.find { it.hasEgrnMatch(address) }

            if (parsedPlaceType == null) {
                Logging.warn("EGRN-PLUGIN Cannot extract place type from EGRN address $address")
                flags.add(ParsingFlags.CANNOT_FIND_PLACE_TYPE)
                return emptyParsedPlace(flags)
            }
            val egrnPlaceName = extractPlaceName(parsedPlaceType.egrn.asRegExpList(), filteredEgrnAddress)
            val filteredEgrnPlaceName = egrnPlaceName.replace("ё", "е")
            val JWSsimilarity = JaroWinklerSimilarity()

            var maxSimilarity = 0.0
            var mostSimilar = ""
            // Ищем соответствующий адресу примитив
            val primitiveNamesMap = primitivesToCompare[parsedPlaceType.name] ?: mapOf()

            for (osmPlaceEntry in primitiveNamesMap) {
                val osmObjectComparisonName = osmPlaceEntry.key
                val osmNameTagValue = osmPlaceEntry.value[0].name

                val filteredOsmPlaceName =
                    extractPlaceName(
                        parsedPlaceType.osm.asRegExpList(),
                        osmObjectComparisonName.replace('ё', 'е').replace('Ё', 'Е')
                    )


                if (filteredOsmPlaceName == "") {
                    //это условие вообще выполнится, если мы тут собрали только подходящие ОСМ обьекты?
                    Logging.info("EGRN-PLUGIN Cannot get openStreetMap name for $osmObjectComparisonName, type ${parsedPlaceType.name}")
                    continue
                }

                if (filteredOsmPlaceName.lowercase() == filteredEgrnPlaceName.lowercase()) {
                    if (filteredOsmPlaceName.contains(Regex("""\d"""))) flags.add(ParsingFlags.PLACE_HAS_NUMBERED_NAME)
                    return ParsedPlace(osmNameTagValue, egrnPlaceName, parsedPlaceType, osmPlaceEntry.value, flags)
                } else {
                    if (matchedNumberedPlace(
                            filteredEgrnPlaceName,
                            filteredOsmPlaceName,
                            parsedPlaceType.name
                        )
                    ) {
                        Logging.info("EGRN-PLUGIN Matched OSM place name by numerics parsing $egrnPlaceName -> $osmObjectComparisonName")
                        flags.add(ParsingFlags.PLACE_HAS_NUMBERED_NAME)
                        return ParsedPlace(
                            osmNameTagValue,
                            egrnPlaceName,
                            parsedPlaceType,
                            osmPlaceEntry.value,
                            flags
                        )
                    }

                    if (matchedWithoutInitials(filteredEgrnPlaceName, filteredOsmPlaceName)) {
                        flags.add(ParsingFlags.PLACE_NAME_INITIALS_MATCH)

                        Logging.warn("EGRN-PLUGIN Matched OSM place name without initials $egrnPlaceName -> $osmObjectComparisonName")
                        return ParsedPlace(
                            osmNameTagValue,
                            egrnPlaceName,
                            parsedPlaceType,
                            osmPlaceEntry.value,
                            flags
                        )
                    }
                    val similarity = JWSsimilarity.apply(filteredEgrnPlaceName, filteredOsmPlaceName)
                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity
                        mostSimilar = osmObjectComparisonName
                    }
                }
            }

            if (mostSimilar.isNotBlank() && maxSimilarity > 0.9) {
                Logging.warn("EGRN-PLUGIN Exact place match for $egrnPlaceName not found, use most similar: $mostSimilar with distance $maxSimilarity")
                flags.add(ParsingFlags.PLACE_NAME_FUZZY_MATCH)
                return ParsedPlace(
                    primitiveNamesMap[mostSimilar]?.get(0)?.name ?: "",
                    egrnPlaceName,
                    parsedPlaceType,
                    primitiveNamesMap[mostSimilar] ?: listOf(),
                    flags
                )
            }

            flags.add(ParsingFlags.CANNOT_FIND_PLACE_OBJECT_IN_OSM)
            return ParsedPlace("", egrnPlaceName, parsedPlaceType, listOf(), flags)
        }


        //пытаемся поматчить места убирая из них инициалы, префикс "им" и имена
        private fun matchedWithoutInitials(EGRNStreetName: String, osmStreetName: String): Boolean {
            val initialsRegexp = Regex("""[А-Я](\.\s?|\s)""")
            val namedByRegex = Regex("""им(\.|\s+)|имени\s+""")

            val divider = Regex("""\s+""")
            if (initialsRegexp.find(EGRNStreetName) != null || initialsRegexp.find(osmStreetName) != null || namedByRegex.find(
                    EGRNStreetName
                ) != null || namedByRegex.find(osmStreetName) != null
            ) {
                val EGRNStreetWithoutInitials = EGRNStreetName.replace(namedByRegex, "").replace(initialsRegexp, "")
                if (EGRNStreetWithoutInitials == osmStreetName) return true
                if (osmStreetName.split(divider).size > 1) {
                    val filteredOsmNameSurnameOnly = osmStreetName.split(divider).last()
                    if (EGRNStreetWithoutInitials == filteredOsmNameSurnameOnly) return true
                }
                val OSMStreetNameWithoutInitials = osmStreetName.replace(namedByRegex, "").replace(initialsRegexp, "")
                if (EGRNStreetWithoutInitials == OSMStreetNameWithoutInitials || EGRNStreetName == OSMStreetNameWithoutInitials) return true
                if (EGRNStreetName.split(divider).size > 1) {
                    val filteredEGRNNameSurnameOnly = EGRNStreetName.split(divider).last()
                    if (filteredEGRNNameSurnameOnly == OSMStreetNameWithoutInitials) return true
                }
            }
            return false
        }

        private fun matchedNumberedPlace(
            egrnPlaceName: String,
            osmObjectName: String,
            streetTypePrefix: String
        ): Boolean {
            val numericsRegexp = Regex("""(?<streetNumber>\d{1,2})((\s|-)(й|ий|ый|ой|я|ая|ья|е|ое|ье))?""")
            val numericsMatch = numericsRegexp.find(egrnPlaceName) ?: return false
            val egrnStreetNumber = numericsMatch.groups["streetNumber"] ?: return false
            val osmNumericsMatch = numericsRegexp.find(osmObjectName) ?: return false
            val osmStreetNumber = osmNumericsMatch.groups["streetNumber"] ?: return false

            if (egrnStreetNumber.value != osmStreetNumber.value) {
                return false
            }

            val filteredEgrnName =
                egrnPlaceName.replace(numericsRegexp, "").replace(streetTypePrefix, "").trim().lowercase()
            val filteredOsmName =
                osmObjectName.replace(numericsRegexp, "").replace(streetTypePrefix, "").trim().lowercase()

            if (filteredEgrnName == filteredOsmName) {
                return true
            }
            return false
        }


        private fun extractPlaceName(regExList: Collection<Regex>, address: String): String {
            if (address == "") {
                return ""
            }

            for (pattern in regExList) {
                if (pattern.containsMatchIn(address)) {
                    val lastMatch = pattern.findAll(address).last() //берем самое правое совпадение
                    val placeName = lastMatch.groups["place"]!!.value
                    //костыли, потому что эта функция обрабатывает и ОСМ имена и ЕГРН.
                    //возможно стоит добавить в ОСМ паттерн тоже номер улицы и убрать это условие?
                    //условие нужно для приведения нумерованных обьектов в одинаковое состояние?
                    if (pattern.toString().contains("placeNumber")) {
                        val numericPrefixMatch = lastMatch.groups["placeNumber"]
                        if (numericPrefixMatch != null) {
                            val numericPrefix = numericPrefixMatch.value
                            var filteredPlaceName = placeName.replace(numericPrefix, "").trim()
                            filteredPlaceName = "$numericPrefix$filteredPlaceName"
                            return filteredPlaceName
                        }
                    }
                    return placeName
                }
            }

            return ""
        }

        private fun emptyParsedPlace(flags: List<ParsingFlags> = listOf()): ParsedPlace {
            return ParsedPlace("", "", null, listOf(), flags)
        }
    }

    private fun getOsmObjectsByType(placeType: PlaceType): Set<OsmPrimitive> {

        val allLoadedPrimitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives()
        val foundPrimitives =
            allLoadedPrimitives.filter { p -> placeType.tags.all { entry -> entry.value.contains(p.get(entry.key)) } }
        return foundPrimitives.filter { p ->
            placeType.osm.asRegExpList().any { regex -> getOsmObjNames(p).any { it.matches(regex) } }
        }.toSet()
    }

    fun getMatchingPrimitives(): Set<OsmPrimitive> {
        if (extractedType == null || extractedName.isEmpty() || name.isEmpty()) {
            return emptySet()
        }
        return getOsmObjectsByType(extractedType).filter { getOsmObjNames(it).contains(name) }
            .toSet()
    }

    private fun getOsmObjNames(p: OsmPrimitive): Set<String> {
        val placeNameTags = setOf("name", "egrn_name", "alt_name")
        return placeNameTags.map { p[it] }.filter { it != null && it != "" }.distinct().toSet()
    }

    fun removeEndingWith(address: String): String {
        if (extractedType == null) return address
        val matchedPattern = extractedType.egrn.asRegExpList().find { it.containsMatchIn(address) }
        if (matchedPattern == null) {
            Logging.error("EGRN PLUGIN RemoveEndingWith - somehow matched place type ${extractedType.name} doesnt match $address")
            return address
        }
        val matchEndIndex = matchedPattern.findAll(address).last().groups["place"]?.range?.last ?: 0
        return address.slice(matchEndIndex + 1 until address.length)
    }

}
