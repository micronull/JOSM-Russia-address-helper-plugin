package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io

import org.openstreetmap.josm.data.preferences.StringProperty

class LayerShiftSettingsReader {
    companion object {
        /**
         * @since 0.2
         */

        val SHIFT_SOURCE_LAYER = StringProperty("dl.russiaaddresshelper.tag.shift_source_layer", "")
    }
}