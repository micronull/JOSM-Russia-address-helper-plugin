package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDFeature
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDOptions
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader.Companion.EGRN_BUILDING_TYPES_SETTINGS

class TagHelper {
    companion object {
        fun getBuildingTagsWithEgrnAddress(feature: NSPDFeature?, address: ParsedAddress?): MutableMap<String, String> {
            val buildTags: MutableMap<String, String> = getBuildingTags(feature)

            if (address != null && TagSettingsReader.EGRN_ADDR_RECORD.get()) {
                buildTags["addr:RU:egrn"] = address.egrnAddress
            }
            return buildTags
        }

        fun getBuildingTags( feature: NSPDFeature?) : MutableMap<String, String>{
            val buildTags: MutableMap<String, String> = mutableMapOf()
            if (feature?.properties?.options != null) {
                val options: NSPDOptions = feature.properties.options
                buildTags["building"] = getPossibleBuildingValue(feature)
                if (!options.yearBuilt.isNullOrBlank()) {
                    buildTags["start_date"] = options.yearBuilt
                }
                if (!options.yearCommissioning.isNullOrBlank()) {
                    buildTags["start_date"] = options.yearCommissioning
                }
                if (!options.floors.isNullOrBlank()) {
                    val undergroundLevels: Int = options.undergroundFloors?.toIntOrNull() ?: 0
                    val levels: Int = options.floors.toIntOrNull() ?: 0
                    if (levels > 0) {
                        buildTags["building:levels"] = (levels - undergroundLevels).toString()
                    }
                }
            }
            return buildTags
        }

        private fun getPossibleBuildingValue(feature: NSPDFeature): String {
            val rules = EGRN_BUILDING_TYPES_SETTINGS.get()
            val options = feature.properties?.options
            rules.forEach{ (key, value) ->
                if (value.any { (feature.properties?.descr?.contains(it, true) == true)
                || options?.purpose?.contains(it, true) == true }) return key }
            return "yes"
        }
    }
}