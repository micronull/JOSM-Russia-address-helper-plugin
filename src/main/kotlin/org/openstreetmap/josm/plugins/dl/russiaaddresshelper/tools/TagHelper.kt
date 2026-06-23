package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDFeature
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDOptions
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader.Companion.EGRN_BUILDING_TYPES_SETTINGS

class TagHelper {
    companion object {

        fun getBuildingTags(feature: NSPDFeature?, layer: NSPDLayer): MutableMap<String, String> {
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
            rules.forEach { (key, value) ->
                if (value.any {
                        (feature.properties?.descr?.contains(it, true) == true)
                                || options?.purpose?.contains(it, true) == true
                    }) return key
            }
            return "yes"
        }

        fun overwriteValue(key: String, oldvalue: String, value: String): Boolean {
            //TODO: нужна ли эта настройка, учитывая что есть валидатор конфликта данных?
            val forceAddressOverwrite = TagSettingsReader.OVERWRITE_ADDRESS.get()
            return when (key) {
                "building" -> if (oldvalue == "yes") return true else false
                "addr:housenumber" -> if (forceAddressOverwrite) return true else false
                "addr:street" -> if (forceAddressOverwrite) return true else false
                "addr:place" -> if (forceAddressOverwrite) return true else false
                else -> {
                    false
                }
            }
        }

        fun getPlaceTags(feature: NSPDFeature?): Map<String, String> {
            val placeTags: MutableMap<String, String> = mutableMapOf()
            if (feature?.properties?.options != null) {
                val options: NSPDOptions = feature.properties.options
                if (!options.description.isNullOrBlank()) {
                    placeTags.plusAssign(splitLongValue("autoremove:description", options.description))
                }
                if (!options.loc.isNullOrBlank() && options.loc != options.description) {
                    placeTags.plusAssign(splitLongValue("autoremove:loc", options.loc))
                }
                if (!options.name.isNullOrBlank() && options.name != options.description) {
                    placeTags.plusAssign(splitLongValue("autoremove:name", options.name))
                }
                if (!options.documentName.isNullOrBlank()) {
                    placeTags.plusAssign(splitLongValue("autoremove:geometry:docName", options.documentName))
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
                    placeTags.plusAssign(splitLongValue("autoremove:description", options.description))
                }
                if (!options.ownershipType.isNullOrBlank()) {
                    placeTags.plusAssign(splitLongValue("autoremove:ownershipType", options.ownershipType))
                }
                if (!options.permittedUseEstablishedByDocument.isNullOrBlank()) {
                    placeTags.plusAssign(splitLongValue("autoremove:permittedUseByDoc", options.permittedUseEstablishedByDocument))
                }
                if (!options.permittedUseName.isNullOrBlank()) {
                    placeTags.plusAssign(splitLongValue("autoremove:permittedUseName", options.permittedUseName))
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
                    getDebugAddressTags(result, address)
                }
                result.plusAssign(splitLongValue("addr:RU:egrn",address.egrnAddress))
            }
            return result
        }

        private fun getDebugAddressTags(
            result: MutableMap<String, String>,
            address: ParsedAddress
        ) {
            result["addr:RU:extracted_street_name"] = address.parsedStreet.extractedName
            result["addr:RU:extracted_street_type"] = address.parsedStreet.extractedType?.name ?: ""
            result["addr:RU:extracted_place_name"] = address.parsedPlace.extractedName
            result["addr:RU:extracted_place_type"] = address.parsedPlace.extractedType?.name ?: ""
            result["addr:RU:parsed_housenumber"] = address.parsedHouseNumber.houseNumber
            result["addr:RU:parsed_flats"] = address.parsedHouseNumber.flats
        }

        fun getAddressTagsForMassAction(address: ParsedAddress?): MutableMap<String, String> {
            val result: MutableMap<String, String> = mutableMapOf()
            if (address != null) {
                getDebugAddressTags(result, address)
                result.plusAssign(splitLongValue("addr:RU:egrn",address.egrnAddress))
            }
            return result
        }

       fun collectAllAddressTags(addresses: List<ParsedAddress>): MutableMap<String,String> {
            val nodeTags: MutableMap<Pair<NSPDLayer, Int>, MutableMap<String, String>> = mutableMapOf()
            val indexMap : MutableMap <NSPDLayer, Int> = mutableMapOf()

            addresses.forEach{ addr ->
                if (addr.layer == null) return@forEach
                val index = indexMap.getOrDefault(addr.layer,0)
                nodeTags[Pair(addr.layer!!, index)] = getAddressTagsForMassAction(addr)
                indexMap[addr.layer!!] = index + 1
            }
            return getMergedTags(nodeTags)
        }

        fun collectAllEgrnTags (nspdResponse: NSPDResponse) : MutableMap<String, String> {
            val nodeTags: MutableMap<Pair<NSPDLayer, Int>, MutableMap<String, String>> = mutableMapOf()
            nspdResponse.responses.forEach {(layer, resp)->
                 if (resp.features.isNotEmpty()) {
                     resp.features.forEachIndexed { localIndex, feature ->
                     val tags  = feature.getTags("autoremove:egrn:")
                         if (feature.properties?.options?.readableAddress != null) {
                            tags.plusAssign(splitLongValue("addr:RU:egrn", feature.properties.options.readableAddress))
                         }
                         nodeTags[Pair(layer, localIndex)] = tags

                     }
                 }
            }
            return getMergedTags(nodeTags)
        }

        //выглядит очень неэффективно, нужен рефакторинг
        fun getMergedTags(nodeTags: MutableMap<Pair<NSPDLayer, Int>, MutableMap<String, String>>): MutableMap<String, String> {
            val result = mutableMapOf<String, String>()
            val tagsByKeyMap = mutableMapOf<String, MutableSet<Pair<String, Pair<NSPDLayer, Int>>>>()
            nodeTags.forEach { (info, tags) ->
                tags.forEach { (key, value) ->
                    if (value.isNotBlank()) {
                        if (tagsByKeyMap.containsKey(key)) {
                            tagsByKeyMap[key]?.add(Pair(value, info))
                        } else {
                            tagsByKeyMap[key] = mutableSetOf(Pair(value, info))
                        }
                    }
                }
            }

            tagsByKeyMap.forEach { (key, setOfValues) ->
                if (setOfValues.size == 1 || setOfValues.distinctBy { it.first }.size == 1) {
                    result[key] = setOfValues.first().first
                } else {
                    if (setOfValues.distinctBy { it.second.second }.size > 1) {
                        setOfValues.forEach { entry ->
                            result["$key:${entry.second.first.name.lowercase()}:${entry.second.second}"] = entry.first
                        }
                    } else {
                        setOfValues.forEach { entry ->
                            result["$key:${entry.second.first.name.lowercase()}"] = entry.first
                        }
                    }
                }
            }

            return result
        }

        fun splitLongValue (tag: String, value: String, maxChunkSize: Int = 255) :MutableMap<String,String> {
            if (value.length <= maxChunkSize) {
                return mutableMapOf(Pair(tag, value))
            }
            val parts :MutableMap<String,String> = mutableMapOf()
            var partIndex = 1
            var start = 0
            while(start < value.length) {
                var end = minOf(start + maxChunkSize, value.length)
                if (end < value.length) {
                    val lastComma = value.lastIndexOf(',', end) + 1
                    if (lastComma > start) {
                        end = lastComma
                    } else {
                        val lastSpace = value.lastIndexOf(' ', end)
                        if (lastSpace > start) {
                            end = lastSpace
                        }
                    }
                }

                parts["$tag:p$partIndex"] = value.substring(start, end).trim()

                start = end
                while (start < value.length && value[start].isWhitespace()) {
                    start++
                }
                partIndex++
            }
            return parts
        }
    }

}