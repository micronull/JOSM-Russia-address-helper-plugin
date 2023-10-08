package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.IntegerProperty

class ValidationSettingsReader {
    companion object {

        val DISTANCE_FOR_STREET_WAY_SEARCH = IntegerProperty("dl.russiaaddresshelper.validation.distance_for_street_search", 200)

        val DISTANCE_FOR_PLACE_NODE_SEARCH = IntegerProperty("dl.russiaaddresshelper.validation.distance_for_place_node_search", 1000)

    }
}