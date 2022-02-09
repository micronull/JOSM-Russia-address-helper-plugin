package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetTypes
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitiveType
import org.openstreetmap.josm.tools.Logging

class OSMStreet(val name: String, val extractedName: String, val extractedType: String) {
    companion object {
        fun identify(address: String, streetTypes: StreetTypes): OSMStreet {
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
                Logging.info("Cannot extract street name from EGRN address $address")
                return OSMStreet("", "", "")
            }

            // Оставляем дороги у которых есть название
            val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives().filter { p ->
                p.hasKey("highway") && p.hasKey("name") && p.type.equals(OsmPrimitiveType.WAY)
            }

            // вариант - искать searchWays(Bbox) в некоей окрестности от домика/домиков
            // улицы, собранные отношениями, в которых на вэях нет тэгов??

            val JWSsimilarity = JaroWinklerSimilarity()

            val lowerCaseEGRNStreetName = egrnStreetName.lowercase().replace('ё','е')

            var maxSimilarity = 0.0
            var mostSimilar = ""
            // Ищем соответствующий адресу примитив
            for (primitive in primitives) {

                val street = primitive.get("name")

                //пропускаем осм имена которые заведомо не содержат определенного нами типа
                if (!street.contains(streetType!!.name, true)) {
                    continue
                }

                val lowercaseOsmStreetName = extractStreetName(streetType.osm.asRegExpList(), street).lowercase()
                    .replace('ё','е')

                if (lowercaseOsmStreetName == "") {
                    Logging.info("Cannot get openStreetMap name for $street, type ${streetType.name}")
                    continue
                }

                if (lowerCaseEGRNStreetName == lowercaseOsmStreetName) {
                    return OSMStreet(street, egrnStreetName, streetType.name)
                } else {
                    val similarity = JWSsimilarity.apply(lowerCaseEGRNStreetName, lowercaseOsmStreetName)
                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity
                        mostSimilar = street
                    }
                }
            }
            if (mostSimilar.isNotBlank() && maxSimilarity > 0.9) {
                Logging.warn("Exact street match not found, use most similar: $mostSimilar with distance $maxSimilarity")
                return OSMStreet(mostSimilar, egrnStreetName, streetType!!.name)
            }

            return OSMStreet("", egrnStreetName, streetType!!.name)
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