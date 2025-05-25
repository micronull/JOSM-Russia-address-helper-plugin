package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDFeature
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDOptions
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader.Companion.EGRN_BUILDING_TYPES_SETTINGS

class TagHelper {
    companion object {

        fun getBuildingTags(feature: NSPDFeature?, layer: NSPDLayer) : MutableMap<String, String>{
            val buildTags: MutableMap<String, String> = mutableMapOf()
            if (feature?.properties?.options != null) {
                val options: NSPDOptions = feature.properties.options
                if (layer == NSPDLayer.BUILDING) {
                    buildTags["building"] = getPossibleBuildingValue(feature)
                } else { //UNFINISHED
                    buildTags["building"] = "construction"
                    buildTags["construction"] = getPossibleBuildingValue(feature)
                }
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

        fun overwriteValue(key: String, oldvalue: String, value: String): Boolean {
            val forceAddressOverwrite = TagSettingsReader.OVERWRITE_ADDRESS.get()
            return when (key) {
                "building" -> if (oldvalue == "yes") return true else false
                "addr:housenumber" -> if (forceAddressOverwrite) return true else false
                "addr:street" -> if (forceAddressOverwrite) return true else false
                "addr:place" -> if (forceAddressOverwrite) return true else false
                else -> {false}
            }
        }

        fun getPlaceTags(feature: NSPDFeature?): Map<String, String> {
            val placeTags: MutableMap<String, String> = mutableMapOf()
            if (feature?.properties?.options != null) {
                val options: NSPDOptions = feature.properties.options
                if (!options.description.isNullOrBlank()) {
                    placeTags["autoremove:description"] = options.description
                }
                if (!options.loc.isNullOrBlank() && options.loc != placeTags["autoremove:description"]) {
                    placeTags["autoremove:loc"] = options.loc
                }
                if (!options.name.isNullOrBlank() && options.name != placeTags["autoremove:description"]) {
                    placeTags["autoremove:name"] = options.name
                }
                if (!options.documentName.isNullOrBlank()) {
                    placeTags["autoremove:geometry:docName"] = options.documentName
                }
                if (!options.documentDate.isNullOrBlank()) {
                    placeTags["source:geometry:date"] = options.documentDate
                }
            }
            return placeTags
        }

        fun getLotTags(feature: NSPDFeature?): Map<String, String> {
            val placeTags: MutableMap<String, String> = mutableMapOf()
            if (feature?.properties?.options != null) {
                val options: NSPDOptions = feature.properties.options
                if (!options.description.isNullOrBlank()) {
                    placeTags["autoremove:description"] = options.description
                }
                if (!options.ownershipType.isNullOrBlank() ) {
                    placeTags["autoremove:ownershipType"] = options.ownershipType
                }
                if (!options.permittedUseEstablishedByDocument.isNullOrBlank()) {
                    placeTags["autoremove:permittedUseByDoc"] = options.permittedUseEstablishedByDocument
                }
                if (!options.permittedUseName.isNullOrBlank()) {
                    placeTags["autoremove:permittedUseName"] = options.permittedUseName
                }
                if (!options.documentDate.isNullOrBlank()) {
                    placeTags["source:geometry:date"] = options.documentDate
                }
            }
            return placeTags
        }

        fun getAddressTagsForClickAction(address: ParsedAddress?): MutableMap<String, String> {
            val result: MutableMap<String, String> = mutableMapOf()
            if (address != null) {
                if (address.isMatchedByStreetOrPlace()) {
                    result.putAll(address.getOsmAddress().getBaseAddressTags())
                } else {
                    result["addr:RU:extracted_street_name"] = address.parsedStreet.extractedName
                    result["addr:RU:extracted_street_type"] = address.parsedStreet.extractedType?.name ?: ""
                    result["addr:RU:extracted_place_name"] = address.parsedPlace.extractedName
                    result["addr:RU:extracted_place_type"] = address.parsedPlace.extractedType?.name ?: ""
                    result["addr:RU:parsed_housenumber"] = address.parsedHouseNumber.houseNumber
                    result["addr:RU:parsed_flats"] = address.parsedHouseNumber.flats
                }
                if (TagSettingsReader.EGRN_ADDR_RECORD.get())
                    result["addr:RU:egrn"] = address.egrnAddress
            }
            return result
        }
    }
}