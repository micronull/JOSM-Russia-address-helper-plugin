package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags

class ParsedHouseNumber(val housenumber: String, val flats: String, val flags: List<ParsingFlags>) {
}