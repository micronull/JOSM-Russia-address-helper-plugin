package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetTypes
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitiveType
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import javax.swing.JOptionPane

class OSMStreet(val name: String, val extractedName: String, val extractedType: String) {
    companion object {
        fun identify(sourceaddress: String, streetTypes: StreetTypes): OSMStreet {
            //убираем одинарные кавычки и Россия из конца адреса
            val address = sourceaddress.replace("''","\"").replace(Regex(""",\s*Россия\s*\.?$"""),"")

            var streetType: StreetType? = null
            var egrnStreetName = ""

            // Извлекаем название улицы
            for (type in streetTypes.types) {
                egrnStreetName = extractStreetName(type.egrn.asRegExpList(), address)

                if (egrnStreetName != "") {
                    streetType = type

                    break
                }
            }

            if (egrnStreetName == "") {
                Logging.error("EGRN-PLUGIN Cannot extract street name from EGRN address $address")
                return OSMStreet("", "", "")
            }

            // Оставляем дороги у которых есть название
            val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives().filter { p ->
                p.hasKey("highway") && p.hasKey("name") && p.type.equals(OsmPrimitiveType.WAY)
            }

            // вариант - искать searchWays(Bbox) в некоей окрестности от домика/домиков
            // улицы, собранные отношениями, в которых на вэях нет тэгов??

            val JWSsimilarity = JaroWinklerSimilarity()

            val filteredEGRNStreetName = egrnStreetName.replace('ё', 'е')

            var maxSimilarity = 0.0
            var mostSimilar = ""
            // Ищем соответствующий адресу примитив

            val primitiveNames :List<String> = primitives.map {it.name}.distinct()

            for (street in primitiveNames) {

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
                    return OSMStreet(street, egrnStreetName, streetType.name)
                } else {
                    if (matchedWithoutInitials(filteredEGRNStreetName, filteredOsmStreetName)) {
                        Logging.warn("EGRN-PLUGIN Matched OSM street name without initials $egrnStreetName -> $street")
                        return OSMStreet(street, egrnStreetName, streetType.name)
                    }
                    val similarity = JWSsimilarity.apply(filteredEGRNStreetName, filteredOsmStreetName)
                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity
                        mostSimilar = street
                    }
                }
            }
            if (mostSimilar.isNotBlank() && maxSimilarity > 0.9) {
                Logging.warn("EGRN-PLUGIN Exact street match for $egrnStreetName not found, use most similar: $mostSimilar with distance $maxSimilarity")
                val msg = I18n.tr("Exact street match not found, most similar will be used!")
                Notification("$msg $egrnStreetName -> $mostSimilar").setIcon(JOptionPane.WARNING_MESSAGE).show()
                return OSMStreet(mostSimilar, egrnStreetName, streetType!!.name)
            }

            return OSMStreet("", egrnStreetName, streetType!!.name)
        }

        //пытаемся поматчить улицы убирая из них инициалы, префикс "им" и имена
        private fun matchedWithoutInitials(EGRNStreetName: String, osmStreetName: String): Boolean {
            val initialRegexp = Regex("""[А-Я](\.\s?|\s)""")
            val namedByRegex = Regex("""им(\.|\s+)|имени\s+""")
            val divider = Regex("""\s+""")
            if (initialRegexp.find(EGRNStreetName) != null || initialRegexp.find(osmStreetName) != null) {
                val EGRNStreetWithoutInitials = EGRNStreetName.replace(namedByRegex,"").replace(initialRegexp, "")
                if (EGRNStreetWithoutInitials == osmStreetName) return true
                if (osmStreetName.split(divider).size > 1) {
                    val filteredOsmNameSurnameOnly = osmStreetName.split(divider).last()
                    if (EGRNStreetWithoutInitials == filteredOsmNameSurnameOnly) return true
                }
                val OSMStreetNameWithoutInitials = osmStreetName.replace(namedByRegex,"").replace(initialRegexp, "")
                if (EGRNStreetWithoutInitials == OSMStreetNameWithoutInitials || EGRNStreetName == OSMStreetNameWithoutInitials) return true
                if (EGRNStreetName.split(divider).size > 1) {
                    val filteredEGRNNameSurnameOnly = EGRNStreetName.split(divider).last()
                    if (filteredEGRNNameSurnameOnly == OSMStreetNameWithoutInitials) return true
                }
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
                    return match.groups["street"]!!.value
                }
            }

            return ""
        }
    }
}