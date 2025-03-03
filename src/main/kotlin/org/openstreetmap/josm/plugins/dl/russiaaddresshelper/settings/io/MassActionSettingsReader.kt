package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io


import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model.MapStringListProperty

class MassActionSettingsReader {
    companion object {

        /**
         * Store list of maps of lists of tags to filter out when doing mass request.
         * @since 0.9.4.8
         */
       val EGRN_MASS_ACTION_FILTER_LIST = MapStringListProperty(
            "dl.russiaaddresshelper.mass.filtering_tag_map",
            mapOf("fixme" to listOf("*"), "building" to listOf("garage","shed","roof","shack"), "addr:housenumber" to listOf("*"))
         )

        /**
         * Determine secondary building info (building type, start date and so on) and change it when doing mass request.
         * @since 0.9.4.8
         */
        val EGRN_MASS_ACTION_USE_EXT_ATTRIBUTES = BooleanProperty("dl.russiaaddresshelper.mass.use_extednded_attributes", true)
    }
}