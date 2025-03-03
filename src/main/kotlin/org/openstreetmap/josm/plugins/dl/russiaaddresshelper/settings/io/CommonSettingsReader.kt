package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.IntegerProperty

class CommonSettingsReader {
    companion object {

        /**
         * @since 0.9.4.5 Enable saving address data to CSV on Export
         */

        val EXPORT_PARSED_DATA_TO_CSV = BooleanProperty("dl.russiaaddresshelper.common.enableExportData", false)

        /**
         * @since 0.9.4.5 Debug geometry of request window
         */

        val ENABLE_DEBUG_GEOMETRY_CREATION = BooleanProperty("dl.russiaaddresshelper.common.enableDebugGeometry", false)

        /**
         * Distance to search for doubles
         */

        val CLEAR_DOUBLE_DISTANCE = IntegerProperty("dl.russiaaddresshelper.tag.double_clear_distance", 100)

    }
}