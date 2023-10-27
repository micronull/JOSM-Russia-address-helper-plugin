package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.*

@kotlinx.serialization.Serializable
data class EGRNResponse(val total: Int, val results: List<EGRNFeature>) {
    fun parseAddresses(requestCoordinate: EastNorth): ParsedAddressInfo {
        val addressParser = AddressParser()
        val addresses: MutableList<ParsedAddress> = mutableListOf()
        val existingAddresses: MutableList<String> = mutableListOf()
        this.results.forEach { res ->
            val egrnAddress = res.attrs?.address ?: return@forEach
            val parsedAddress = addressParser.parse(egrnAddress, requestCoordinate, OsmDataManager.getInstance().editDataSet)

            if (res.type == EGRNFeatureType.BUILDING.type) {
                parsedAddress.flags.add(ParsingFlags.IS_BUILDING)
            }

            val key = parsedAddress.getOsmAddress().getInlineAddress()
                ?: getAddressMatchKeyForUnparsedAddress(parsedAddress)
            if (!existingAddresses.contains(key)) {
                addresses.add(parsedAddress)
                existingAddresses.add(key)
            }
        }
        return ParsedAddressInfo(addresses)
    }

    private fun getAddressMatchKeyForUnparsedAddress(parsedAddress: ParsedAddress): String {
        /*return ("${parsedAddress.parsedPlace.extractedName} ${parsedAddress.parsedPlace.extractedType?.name ?: ""}" +
                " ${parsedAddress.parsedStreet.extractedName} ${parsedAddress.parsedStreet.extractedType?.name ?: ""}" +
                " ${parsedAddress.parsedHouseNumber.houseNumber} ${parsedAddress.parsedHouseNumber.flats}")*/
        return parsedAddress.egrnAddress
    }
}