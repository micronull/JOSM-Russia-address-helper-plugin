package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.OSMStreet

data class ParsedAddressInfo(val addresses : Map<String, Triple<Int, OSMAddress, String>>, val badAddresses: List<Triple<Int, OSMStreet, String>>)
