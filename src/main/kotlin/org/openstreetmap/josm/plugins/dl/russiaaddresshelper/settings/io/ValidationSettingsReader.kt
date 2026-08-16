package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.IntegerProperty

class ValidationSettingsReader {
    companion object {

        val DISTANCE_FOR_STREET_WAY_SEARCH = IntegerProperty("dl.russiaaddresshelper.validation.distance_for_street_search", 200)

        val DISTANCE_FOR_PLACE_NODE_SEARCH = IntegerProperty("dl.russiaaddresshelper.validation.distance_for_place_node_search", 1000)

        val IGNORE_ADDRESS_NODES = BooleanProperty("dl.russiaaddresshelper.validation.ignore_address_nodes_for_duplicates_search", true)

        val ENABLE_NEW_DOUBLES_CHECK = BooleanProperty("dl.russiaaddresshelper.validation.enable_new_doubles_check", true)

    }
}