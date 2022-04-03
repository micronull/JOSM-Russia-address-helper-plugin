package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.HouseNumberParser
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedStreet
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.StreetParser

data class EGRNResponse(val total: Int, val results: List<EGRNFeature>) {
    fun parseAddresses(): ParsedAddressInfo {
        val streetParser = StreetParser()
        val houseNumberParser = HouseNumberParser()

        val addresses: MutableList<Triple<Int, OSMAddress, String>> = mutableListOf()
        val badAddresses: MutableList<Triple<Int, Pair<ParsedStreet, OSMAddress>, String>> = mutableListOf()
        val existingAddresses: MutableList<String> = mutableListOf()
        this.results.forEach { res ->
            val egrnAddress = res.attrs?.address ?: return@forEach
            val streetParse = streetParser.parse(egrnAddress)
            val houseNumberParse = houseNumberParser.parse(egrnAddress)
            val parsedOsmAddress =
                OSMAddress(streetParse.name, houseNumberParse.housenumber, houseNumberParse.flats)
            val key = parsedOsmAddress.getInlineAddress() ?: "${streetParse.extractedName} ${streetParse.extractedType}"
            if (!existingAddresses.contains(key)) {
                if (streetParse.name != "" && houseNumberParse.housenumber != "") {
                    addresses.add(Triple(res.type, parsedOsmAddress, egrnAddress))
                } else {
                    badAddresses.add(Triple(res.type, Pair(streetParse, parsedOsmAddress), egrnAddress))
                }
                existingAddresses.add(key)
            }
        }
        return ParsedAddressInfo(addresses, badAddresses)
    }
}