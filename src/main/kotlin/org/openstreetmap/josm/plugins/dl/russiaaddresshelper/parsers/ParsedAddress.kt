package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress

data class ParsedAddress(
    val parsedPlace: ParsedPlace,
    val parsedStreet: ParsedStreet,
    val parsedHouseNumber: ParsedHouseNumber,
    val egrnAddress: String,
    val flags: List<ParsingFlags>
) {
    fun getOsmAddress(): OSMAddress {
        return OSMAddress(parsedPlace.name, parsedStreet.name, parsedHouseNumber.housenumber, parsedHouseNumber.flats)
    }

    fun isBuildingAddress():Boolean {
        return flags.contains(ParsingFlags.IS_BUILDING)
    }
}
