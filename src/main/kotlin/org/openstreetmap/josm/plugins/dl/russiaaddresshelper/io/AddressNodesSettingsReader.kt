package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io

import org.openstreetmap.josm.data.preferences.BooleanProperty

class AddressNodesSettingsReader {
    companion object {

        val GENERATE_ADDRESS_NODES_FOR_ADDITIONAL_ADDRESSES = BooleanProperty("dl.russiaaddresshelper.tag.generate_additional_address_nodes", true)

        val GENERATE_ADDRESS_NODES_FOR_BAD_ADDRESSES = BooleanProperty("dl.russiaaddresshelper.tag.generate_bad_address_nodes", false)

    }
}