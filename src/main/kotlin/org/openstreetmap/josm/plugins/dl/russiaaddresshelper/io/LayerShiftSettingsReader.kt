package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.StringProperty

class LayerShiftSettingsReader {
    companion object {
        /**
         * @since 0.2
         */

        val ENABLE_COORDINATES_SHIFT = BooleanProperty("dl.russiaaddresshelper.tag.coordinates_shift", false)

        val SHIFT_SOURCE_LAYER = StringProperty("dl.russiaaddresshelper.tag.shift_source_layer", "none")
    }
}