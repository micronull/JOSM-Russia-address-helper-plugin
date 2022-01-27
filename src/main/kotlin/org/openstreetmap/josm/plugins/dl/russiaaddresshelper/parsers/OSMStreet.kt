package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.text.similarity.HammingDistance
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetTypes
import org.openstreetmap.josm.data.osm.OsmDataManager

class OSMStreet(val name: String, val extracted: String) {
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
                return OSMStreet("", "")
            }

            // Оставляем дороги у которых есть название
            val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives().filter { p ->
                p.hasKey("highway") && p.hasKey("name")
            }

            val distance = HammingDistance()

            val lowerCaseEGRNStreetName = egrnStreetName.lowercase()

            // Ищем соответствующий адресу примитив
            for (primitive in primitives) {
                if (!primitive.hasKey("name")) {
                    continue
                }

                val street = primitive.get("name")

                val osmStreetName = extractStreetName(streetType!!.osm.asRegExpList(), street).lowercase()

                if (osmStreetName == "") {
                    continue
                }

                if (lowerCaseEGRNStreetName == osmStreetName || egrnStreetName.length == osmStreetName.length && distance.apply(egrnStreetName, osmStreetName) < 3) {
                    return OSMStreet(street, egrnStreetName)
                }
            }

            return OSMStreet("", egrnStreetName)
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