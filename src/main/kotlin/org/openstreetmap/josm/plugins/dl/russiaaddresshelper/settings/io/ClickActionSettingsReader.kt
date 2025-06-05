package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.DoubleProperty
import org.openstreetmap.josm.data.preferences.IntegerProperty

class ClickActionSettingsReader {
    companion object {

        /**
         * Enable import of geometry features.
         * @since 0.9.4
         */
        val EGRN_CLICK_ENABLE_GEOMETRY_IMPORT = BooleanProperty(
            "dl.russiaaddresshelper.click.enable_geometry_import",
            false
        )

        /**
         * Filter imported polygons with area smaller than property.
         * @since 0.9.4.1
         */
        val EGRN_CLICK_GEOMETRY_IMPORT_THRESHOLD = DoubleProperty(
            "dl.russiaaddresshelper.click.geometry_import_threshold",
            3.0
        )

        /**
         * Auto .
         * @since 0.9.4
         */
        val EGRN_CLICK_ENABLE_GEOMETRY_SIMPLIFY = BooleanProperty(
            "dl.russiaaddresshelper.click.enable_geometry_simplify",
            true
        )

        /**
         * Filter imported polygons with area smaller than property.
         * @since 0.9.4
         */
        val EGRN_CLICK_GEOMETRY_SIMPLIFY_THRESHOLD = DoubleProperty(
            "dl.russiaaddresshelper.click.geometry_simplify_threshold",
            0.3
        )

        /**
         * Merge features properties on single node
         * @since 0.9.4
         */
        val EGRN_CLICK_MERGE_FEATURES = BooleanProperty(
            "dl.russiaaddresshelper.click.merge_features_on_node",
            true
        )

        /**
         * Distance in meters of request boundaries for click actions
         * @since 0.9.4
         */
        val EGRN_CLICK_BOUNDS_EXTENSION = IntegerProperty(
            "dl.russiaaddresshelper.click.boundaries_extension",
            200
        )

        /**
         * Filter imported place boundary polygons with area smaller than property.
         * @since 0.9.6.7
         */
        val EGRN_CLICK_BOUNDARY_IMPORT_THRESHOLD = DoubleProperty(
            "dl.russiaaddresshelper.click.boundary_geometry_import_threshold",
            50.0
        )
    }
}