package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetTypes
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.tools.Logging

class ParsedStreet(
    val name: String,
    val extractedName: String,
    val extractedType: String,
    val matchedPrimitives: List<OsmPrimitive>,
    val flags: List<ParsingFlags>
) {
    companion object {
        fun identify(sourceAddress: String, streetTypes: StreetTypes, osmObjectNames: Map<String, Pair<String, List<OsmPrimitive>>>): ParsedStreet {

            var streetType: StreetType? = null
            var egrnStreetName = ""
            val flags:MutableList<ParsingFlags> = mutableListOf()
            // Извлекаем название улицы
            for (type in streetTypes.types) {
                egrnStreetName = extractStreetName(type.egrn.asRegExpList(), sourceAddress)

                if (egrnStreetName != "") {
                    streetType = type

                    break
                }
            }

            if (egrnStreetName == "") {
                Logging.warn("EGRN-PLUGIN Cannot extract street name from EGRN address $sourceAddress")
                flags.add(ParsingFlags.CANNOT_EXTRACT_STREET_NAME)
                return ParsedStreet("", "", "", listOf(), flags)
            }

            val JWSsimilarity = JaroWinklerSimilarity()

            val filteredEGRNStreetName = egrnStreetName.replace('ё', 'е')

            var maxSimilarity = 0.0
            var mostSimilar = ""
            // Ищем соответствующий адресу примитив

            for (street in osmObjectNames.keys) {

                //пропускаем осм имена которые заведомо не содержат определенного нами типа
                if (!street.contains(streetType!!.name, true)) {
                    continue
                }

                val filteredOsmStreetName = extractStreetName(streetType.osm.asRegExpList(), street)
                    .replace('ё', 'е')

                if (filteredOsmStreetName == "") {
                    Logging.info("EGRN-PLUGIN Cannot get openStreetMap name for $street, type ${streetType.name}")
                    continue
                }

                if (filteredEGRNStreetName.lowercase() == filteredOsmStreetName.lowercase()) {
                    if (street != osmObjectNames[street]?.first) {
                        flags.add(ParsingFlags.MATCHED_STREET_BY_SECONDARY_TAGS)
                    }
                    return ParsedStreet(osmObjectNames[street]!!.first, egrnStreetName, streetType.name, osmObjectNames[street]!!.second, flags)
                } else {
                    if (matchedNumberedStreet(filteredEGRNStreetName, filteredOsmStreetName, streetType.name)) {
                        Logging.info("EGRN-PLUGIN Matched OSM street name by numerics parsing $egrnStreetName -> $street")
                        return ParsedStreet(osmObjectNames[street]!!.first, egrnStreetName, streetType.name, osmObjectNames[street]!!.second, flags)
                    }

                    if (matchedWithoutInitials(filteredEGRNStreetName, filteredOsmStreetName)) {
                        flags.add(ParsingFlags.STREET_NAME_INITIALS_MATCH)
                        Logging.warn("EGRN-PLUGIN Matched OSM street name without initials $egrnStreetName -> $street")
                        return ParsedStreet(osmObjectNames[street]!!.first, egrnStreetName, streetType.name, osmObjectNames[street]!!.second, flags)
                    }
                    val similarity = JWSsimilarity.apply(filteredEGRNStreetName, filteredOsmStreetName)
                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity
                        mostSimilar = street
                    }
                }
            }
            //TO DO low priority - вынести в настройки вкл.выкл нечеткого матчинга + значение схожести?
            if (mostSimilar.isNotBlank() && maxSimilarity > 0.9) {
                Logging.warn("EGRN-PLUGIN Exact street match for $egrnStreetName not found, use most similar: $mostSimilar with distance $maxSimilarity")
                flags.add(ParsingFlags.STREET_NAME_FUZZY_MATCH)
                return ParsedStreet(osmObjectNames[mostSimilar]!!.first, egrnStreetName, streetType!!.name, osmObjectNames[mostSimilar]!!.second, flags)
            }
            flags.add(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM)
            return ParsedStreet("", egrnStreetName, streetType!!.name, listOf(), flags)
        }

        //пытаемся поматчить улицы убирая из них инициалы, префикс "им" и имена
        private fun matchedWithoutInitials(EGRNStreetName: String, osmStreetName: String): Boolean {
            val initialsRegexp = Regex("""[А-Я](\.\s?|\s)""")
            val namedByRegex = Regex("""им(\.|\s+)|имени\s+""")

            val divider = Regex("""\s+""")
            if (initialsRegexp.find(EGRNStreetName) != null || initialsRegexp.find(osmStreetName) != null || namedByRegex.find(EGRNStreetName)!= null || namedByRegex.find(osmStreetName)!= null) {
                val EGRNStreetWithoutInitials = EGRNStreetName.replace(namedByRegex,"").replace(initialsRegexp, "")
                if (EGRNStreetWithoutInitials == osmStreetName) return true
                if (osmStreetName.split(divider).size > 1) {
                    val filteredOsmNameSurnameOnly = osmStreetName.split(divider).last()
                    if (EGRNStreetWithoutInitials == filteredOsmNameSurnameOnly) return true
                }
                val OSMStreetNameWithoutInitials = osmStreetName.replace(namedByRegex,"").replace(initialsRegexp, "")
                if (EGRNStreetWithoutInitials == OSMStreetNameWithoutInitials || EGRNStreetName == OSMStreetNameWithoutInitials) return true
                if (EGRNStreetName.split(divider).size > 1) {
                    val filteredEGRNNameSurnameOnly = EGRNStreetName.split(divider).last()
                    if (filteredEGRNNameSurnameOnly == OSMStreetNameWithoutInitials) return true
                }
            }
            return false
        }

        private fun matchedNumberedStreet(EGRNStreetName: String, osmStreetName: String, streetTypePrefix: String): Boolean {
            val numericsRegexp = Regex("""(?<streetNumber>\d{1,2})(\s|-)(й|ий|ый|ой|я|ая|ья|е|ое|ье)""")
            val numericsMatch = numericsRegexp.find(EGRNStreetName) ?: return false
            val egrnStreetNumber = numericsMatch.groups["streetNumber"]?:return false
            val osmNumericsMatch = numericsRegexp.find(osmStreetName) ?: return false
            val osmStreetNumber = osmNumericsMatch.groups["streetNumber"]?:return false

            if (egrnStreetNumber.value != osmStreetNumber.value) {
                return false
            }

            val filteredEgrnName = EGRNStreetName.replace(numericsRegexp,"").replace(streetTypePrefix,"").trim().lowercase()
            val filteredOsmName = osmStreetName.replace(numericsRegexp,"").replace(streetTypePrefix,"").trim().lowercase()

            if (filteredEgrnName == filteredOsmName) {
                return true
            }
            return false
        }

        private fun extractStreetName(regExList: Collection<Regex>, address: String): String {
            if (address == "") {
                return ""
            }

            for (pattern in regExList) {
                val match = pattern.find(address)

                if (match != null) {
                    val streetName = match.groups["street"]!!.value
                    //костыли, потому что эта функция обрабатывает и ОСМ имена и ЕГРН.
                    //возможно стоит добавить в ОСМ паттерн тоже номер улицы и убрать это условие?
                    if (pattern.toString().contains("streetNumber")) {
                        val numericPrefixMatch = match.groups["streetNumber"]
                        if (numericPrefixMatch != null) {
                            val numericPrefix = numericPrefixMatch.value
                            var filteredStreetName = streetName.replace(numericPrefix, "").trim()
                            filteredStreetName = "$numericPrefix$filteredStreetName"
                            return filteredStreetName
                        }
                    }
                    return streetName
                }
            }

            return ""
        }
    }
}