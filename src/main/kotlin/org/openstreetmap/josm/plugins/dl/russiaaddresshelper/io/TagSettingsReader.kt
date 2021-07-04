package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io

import org.openstreetmap.josm.data.preferences.BooleanProperty

class TagSettingsReader {
    companion object {
        /**
         * @since 0.2
         */
        val EGRN_ADDR_RECORD = BooleanProperty("dl.russiaaddresshelper.tag.egrn_addr_record", false)
    }
}