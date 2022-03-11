package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetTypes

class StreetParser : IParser<ParsedStreet> {
    private val streetTypes: StreetTypes = StreetTypes.byYml("/references/street_types.yml")

    override fun parse(address: String): ParsedStreet {
        return ParsedStreet.identify(address, streetTypes)
    }
}