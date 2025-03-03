package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress

data class ParsedAddress(
    val parsedPlace: ParsedPlace,
    val parsedStreet: ParsedStreet,
    val parsedHouseNumber: ParsedHouseNumber,
    val egrnAddress: String,
    val flags: MutableList<ParsingFlags>,
    var layer: NSPDLayer? = null
) {
    fun getOsmAddress(): OSMAddress {
        return OSMAddress(parsedPlace.name, parsedStreet.name, parsedHouseNumber.houseNumber, parsedHouseNumber.flats)
    }

    fun isBuildingAddress():Boolean {
        return flags.contains(ParsingFlags.IS_BUILDING)
    }

    fun isMatchedByStreetOrPlace():Boolean {
        return isMatchedByStreet() || isMatchedByPlace()
    }

    fun isMatchedByStreet() : Boolean {
        val osmAddress = getOsmAddress()
        return osmAddress.isFilledStreetAddress() //|| flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM)
    }

    fun isMatchedByPlace() : Boolean {
        val osmAddress = getOsmAddress()
        return osmAddress.isFilledPlaceAddress() && !flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM)
    }

}
