package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedStreet

data class ParsedAddressInfo(val addresses : List<Triple<Int, OSMAddress, String>>,
                             val badAddresses: List<Triple<Int, Pair<ParsedStreet, OSMAddress>, String>>)
