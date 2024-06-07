package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.AddressParser
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress

@kotlinx.serialization.Serializable
data class EGRNFeature(
    val type: Int,
    val attrs: EGRNAttribute?,
    val extent: EGRNExtent?,
    val sort: Long?,
    val center: EGRNCoord
) {
    fun getTags(prefix: String = "egrn:", filter: Set<String> = setOf()): MutableMap<String, String> {
        return attrs?.getExtTags(prefix, filter) ?: mutableMapOf()
    }

    fun parseAddress(requestCoordinate: EastNorth): ParsedAddress? {
        val addressParser = AddressParser()
        val egrnAddress = this.attrs?.address ?: return null
        val parsedAddress =
            addressParser.parse(egrnAddress, requestCoordinate, OsmDataManager.getInstance().editDataSet)

        if (this.type == EGRNFeatureType.BUILDING.type) {
            parsedAddress.flags.add(ParsingFlags.IS_BUILDING)
        }
        return parsedAddress
    }
}

@kotlinx.serialization.Serializable
data class EGRNCoord(val x: Double, val y: Double)

@kotlinx.serialization.Serializable
data class EGRNExtent(val xmin: Double, val xmax: Double, val ymin: Double, val ymax: Double)

@kotlinx.serialization.Serializable
data class EGRNAttribute(
    val address: String?, val util_by_doc: String?, val floors: Int?, val underground_floors: Int?,
    val name: String?, val purpose: String?, val purpose_name: String?, val oks_type: String?,
    val year_built: Double?, val year_used: Double?,
    val cn: String, val id: String
) {
    fun getExtTags(prefix: String = "egrn:", filter: Set<String> = setOf()): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        if (util_by_doc != null && !filter.contains("util_by_doc")) {
            result[prefix + "util_by_doc"] = util_by_doc
        }
        if (floors != null && !filter.contains("levels")) {
            val undergroundLevels: Int = underground_floors ?: 0
            result[prefix + "levels"] = (floors - undergroundLevels).toString()
        }
        if (name != null && !filter.contains("name")) {
            result[prefix + "name"] = name
        }
        if (purpose != null && !filter.contains("purpose")) {
            result[prefix + "purpose"] = purpose
        }
        if (purpose_name != null && !filter.contains("purpose_name")) {
            result[prefix + "purpose_name"] = purpose_name
        }

        if (oks_type != null && !filter.contains("oks_type")) {
            val result_oks_type: String = when (oks_type) {
                "building" -> "здание"
                "construction" -> "сооружение"
                "incomplete" -> "незавершенное"
                else -> oks_type
            }
            result[prefix + "oks_type"] = result_oks_type
        }

        if (year_built != null && !filter.contains("year_built")) {
            result[prefix + "year_built"] = year_built.toInt().toString()
        }

        if (year_used != null && !filter.contains("year_used")) {
            result[prefix + "year_used"] = year_used.toInt().toString()
        }

        return result
    }
}

@kotlinx.serialization.Serializable
data class EGRNFeatureExtDataResponse(
    val feature: EGRNFeature?
)
