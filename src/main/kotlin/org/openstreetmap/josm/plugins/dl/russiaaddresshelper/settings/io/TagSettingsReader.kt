package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.IntegerProperty

class TagSettingsReader {
    companion object {
        /**
         * @since 0.2
         */
        val EGRN_ADDR_RECORD = BooleanProperty("dl.russiaaddresshelper.tag.egrn_addr_record", true)

        val ENABLE_CLEAR_DOUBLE = BooleanProperty("dl.russiaaddresshelper.tag.double_clear", true)

        val CLEAR_DOUBLE_DISTANCE = IntegerProperty("dl.russiaaddresshelper.tag.double_clear_distance", 100)
    }
}