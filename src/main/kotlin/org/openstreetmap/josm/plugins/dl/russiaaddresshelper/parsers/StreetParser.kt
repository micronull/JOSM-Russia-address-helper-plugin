package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.text.similarity.HammingDistance
import org.openstreetmap.josm.data.osm.OsmDataManager

class StreetParser() : Parser {
    private val streets: MutableList<String> = mutableListOf()
    private val streetsShort: MutableList<String> = mutableListOf()

    private var egrnStreet: String = ""

    init {
        loadStreets()
    }

    val extracted: String
        get() {
            return egrnStreet
        }

    companion object {
        val OSM_STREET_NAME_REGEXP = Regex("^(?:ул\\S*\\s+)?(?<street>.+?)(?:\\s+ул\\S*)?\$")
    }

    private fun loadStreets() {
        val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives().filter { p -> p.hasKey("highway") && p.hasKey("name") }

        for (primitive in primitives) {
            primitive.visitKeys { _, key, value ->
                if (key == "name" && !streets.contains(value)) {
                    val match = OSM_STREET_NAME_REGEXP.find(value)

                    if (match != null) {
                        streets.add(value)
                        streetsShort.add(match.groups["street"]!!.value.lowercase())
                    }
                }
            }
        }
    }

    override fun parse(egrnAddress: String): String {
        for (pattern in regexList) {
            val match = pattern.find(egrnAddress)

            if (match != null) {
                egrnStreet = match.groups["street"]!!.value.lowercase()
                break
            }
        }

        if (egrnStreet == "") {
            return ""
        }

        val streetsShortList = streetsShort.toList()
        val streetsList = streets.toList()
        val distance = HammingDistance()

        for (i in streetsShort.indices) {
            val streetName = streetsShortList[i]

            if (egrnStreet == streetName || egrnStreet.length == streetName.length && distance.apply(egrnStreet, streetName) < 3) {
                return streetsList[i]
            }
        }

        return ""
    }
}