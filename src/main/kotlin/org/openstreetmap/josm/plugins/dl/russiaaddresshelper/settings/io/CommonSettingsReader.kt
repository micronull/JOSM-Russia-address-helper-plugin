package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.DoubleProperty
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

        /**
         * Enable auto-orthoganlize imported geometry if all angles is close to 90, 0 ,180 degrees.
         * @since 0.9.6.8
         */
        val EGRN_ENABLE_GEOMETRY_ORTOGONALIZE = BooleanProperty(
            "dl.russiaaddresshelper.common.enable_geometry_orthogonalize",
            true
        )

        /**
         * Auto-orthogonalize allowed angle threshold. Ways containing angles more than threshold will NOT be processed.
         * @since 0.9.6.8
         */
        val EGRN_GEOMETRY_ORTOGONALIZE_THRESHOLD = DoubleProperty(
            "dl.russiaaddresshelper.common.boundary_geometry_othrogonalize_threshold",
            10.0
        )

    }
}