package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDFeature
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDOptions
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader.Companion.EGRN_BUILDING_TYPES_SETTINGS
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader.Companion.PARSE_CULTURAL_HERITAGE
import org.openstreetmap.josm.tools.Logging

class TagHelper {
    companion object {
        fun getBuildingTags(feature: NSPDFeature?, layer: NSPDLayer): MutableMap<String, String> {
            val buildTags: MutableMap<String, String> = mutableMapOf()
            if (feature?.properties?.options != null) {
                val options: NSPDOptions = feature.properties.options
                if (layer == NSPDLayer.BUILDING || layer == NSPDLayer.CONSTRUCTS) {
                    buildTags["building"] = getPossibleBuildingValue(feature)
                } else { //UNFINISHED
                    buildTags["building"] = "construction"
                    buildTags["construction"] = getPossibleBuildingValue(feature)
                }
                if (!options.yearBuilt.isNullOrBlank()) {
                    buildTags["start_date"] = options.yearBuilt
                } else if (!options.constructYearBuilt.isNullOrBlank()) {
                    buildTags["start_date"] = options.constructYearBuilt
                }
                if (!options.yearCommissioning.isNullOrBlank()) {
                    buildTags["start_date"] = options.yearCommissioning
                } else if (!options.constructYearCommissioning.isNullOrBlank()) {
                    buildTags["start_date"] = options.constructYearCommissioning
                }

                var resultUndergroundFloors: Int? = null
                if (!options.undergroundFloors.isNullOrBlank()) {
                    resultUndergroundFloors = options.undergroundFloors.toIntOrNull()
                } else if (!options.constructUndergroundFloors.isNullOrBlank()) {
                    resultUndergroundFloors = options.constructUndergroundFloors.toIntOrNull()
                }
                var resultFloors: Int? = null
                if (!options.floors.isNullOrBlank()) {
                    resultFloors = options.floors.toIntOrNull()
                } else if (!options.constructFloors.isNullOrBlank()) {
                    resultFloors = options.constructFloors.toIntOrNull()
                }

                if (resultFloors != null || resultUndergroundFloors != null) {
                    if (resultUndergroundFloors == null || resultUndergroundFloors == 0 || resultFloors == 1) {
                        buildTags["building:levels"] = resultFloors.toString()
                    } else {
                        var levels: Int?
                        if (TagSettingsReader.CALCULATE_LEVELS.get()) {
                            //вычисление этажей, из предположения что ЕГРН в поле floors верно указывает общую этажность (надземные + подземные). По Москве это точно не так!
                            levels = (resultFloors ?: 0) - resultUndergroundFloors
                            if (levels < 0) {
                                Logging.warn("EGRN PLUGIN calculated levels incorrect: total $resultFloors, underground $resultUndergroundFloors, levels: $levels")
                                levels = null
                            }
                        } else {
                            levels = resultFloors
                        }
                        buildTags["fixme"] = "resurvey building:levels"
                        buildTags["building:underground:levels"] = resultUndergroundFloors.toString()
                        if (levels != null) {
                            buildTags["building:levels"] = levels.toString()
                        }

                    }
                }
            }
            buildTags.putAll(getCulturalHeritageTags(feature))

            return buildTags
        }

        private fun getCulturalHeritageTags(feature: NSPDFeature?): MutableMap<String, String> {
            val result: MutableMap<String, String> = mutableMapOf()
            var prefix = ""
            if (!PARSE_CULTURAL_HERITAGE.get()) return mutableMapOf()
            val culturalHeritageValue = feature?.properties?.options?.culturalHeritageVal
            if (culturalHeritageValue.isNullOrBlank()) return mutableMapOf()
            Logging.info("EGRN_Plugin: received cultural_heritage_val $culturalHeritageValue")
            result.putAll(splitLongValues(mutableMapOf("autoremove:egrn_heritage" to culturalHeritageValue)))
            val heritageValues: List<String> = culturalHeritageValue.split(",").map { it.trim() }
            val egrokn = heritageValues[0]
            if (egrokn.length == 10 && egrokn.all { it.isDigit() }) {
                Logging.warn("EGRN PLUGIN Old type EGROKN index: $egrokn")
                return result
            }

            val parsedEgroknData = EgroknParser.parseOrNull(egrokn) ?: return result

            if (heritageValues.size > 1 && !parsedEgroknData.objectTypeName.equals(heritageValues[1], true)) {
                Logging.warn("EGRN PLUGIN EGROKN objectType != EGRN objectType, ${parsedEgroknData.objectTypeName}, ${heritageValues[1]}")
            }
            result["autoremove:object_type"] = parsedEgroknData.objectTypeName

            if (parsedEgroknData.objectType == "1") {
                result["heritage"] = "building"

            } else {
                Logging.warn("EGRN PLUGIN Heritage cant be auto-mapped: ${parsedEgroknData.objectTypeName}")
                prefix = "autoremove:"
                result[prefix + "heritage"] = "yes"
            }

            result[prefix + "ref:egrokn"] = egrokn
            result[prefix + "heritage"] = parsedEgroknData.getOsmHeritageCategory()
            result["autoremove:category"] = parsedEgroknData.categoryName

            val heritageName :String
            if (heritageValues.size >= 3) {
                if (heritageValues.size == 4) {
                    result["autoremove:start_date"] = heritageValues[3]
                     heritageName = heritageValues[2]
                } else {
                    heritageName = heritageValues.drop(2).joinToString(",")
                }
                result[prefix + "name:heritage"] = heritageName
                result[prefix + "historic"] = getPossibleHistoricValue(heritageName)
            }

            return result
        }

        private fun getPossibleBuildingValue(feature: NSPDFeature): String {
            val rules = EGRN_BUILDING_TYPES_SETTINGS.get()
            val options = feature.properties?.options
            rules.forEach { (key, value) ->
                if (value.any {
                        (feature.properties?.descr?.contains(it, true) == true) ||
                                (options?.purpose?.contains(it, true) == true) ||
                                (options?.buildingName?.contains(it, true) == true) ||
                                (options?.constructName?.contains(it, true) == true) ||
                                (options?.constructPurpose?.contains(it, true) == true)

                    }) return key
            }
            return "yes"
        }

        private fun getPossibleHistoricValue(heritage: String): String {
            val rules = EGRN_BUILDING_TYPES_SETTINGS.get()
            rules.forEach { (key, value) ->
                if (value.any {
                        heritage.contains(it, true)
                    }) return key
            }
            return "building"
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
                    placeTags.plusAssign(
                        splitLongValue(
                            "autoremove:permittedUseByDoc",
                            options.permittedUseEstablishedByDocument
                        )
                    )
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
                result.plusAssign(splitLongValue("addr:RU:egrn", address.egrnAddress))
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
                result.plusAssign(splitLongValue("addr:RU:egrn", address.egrnAddress))
            }
            return result
        }

        fun collectAllAddressTags(addresses: List<ParsedAddress>): MutableMap<String, String> {
            val nodeTags: MutableMap<Pair<NSPDLayer, Int>, MutableMap<String, String>> = mutableMapOf()
            val indexMap: MutableMap<NSPDLayer, Int> = mutableMapOf()

            addresses.forEach { addr ->
                if (addr.layer == null) return@forEach
                val index = indexMap.getOrDefault(addr.layer, 0)
                nodeTags[Pair(addr.layer!!, index)] = getAddressTagsForMassAction(addr)
                indexMap[addr.layer!!] = index + 1
            }
            return getMergedTags(nodeTags)
        }

        fun collectAllEgrnTags(nspdResponse: NSPDResponse): MutableMap<String, String> {
            val nodeTags: MutableMap<Pair<NSPDLayer, Int>, MutableMap<String, String>> = mutableMapOf()
            nspdResponse.responses.forEach { (layer, resp) ->
                if (resp.features.isNotEmpty()) {
                    resp.features.forEachIndexed { localIndex, feature ->
                        val tags = splitLongValues(feature.getTags("autoremove:egrn:"))
                        if (feature.properties?.options?.getAnyReadableAddress() != null) {
                            tags.plusAssign(
                                splitLongValue(
                                    "addr:RU:egrn",
                                    feature.properties.options.getAnyReadableAddress()!!
                                )
                            )
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

        fun splitLongValues(data: MutableMap<String, String>): MutableMap<String, String> {
            val result: MutableMap<String, String> = mutableMapOf()
            data.forEach { (tag, value) -> result.plusAssign(splitLongValue(tag, value)) }
            return result
        }

        fun splitLongValue(tag: String, value: String, maxChunkSize: Int = 255): MutableMap<String, String> {
            if (value.length <= maxChunkSize) {
                return mutableMapOf(Pair(tag, value.trim()))
            }
            val parts: MutableMap<String, String> = mutableMapOf()
            var partIndex = 1
            var start = 0
            while (start < value.length) {
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