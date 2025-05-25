package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.StringProperty
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer

class LayerFilterSettingsReader {
    companion object {
        /**
         * @since 0.9.4
         */

        val CLICK_ACTION_LAYER_FILTER = StringProperty("dl.russiaaddresshelper.layer_request_filter.click", "BUILDING=true,PARCEL=true,CONSTRUCTS=false,UNFINISHED=false")

        val MASS_REQUEST_ACTION_LAYER_FILTER = StringProperty("dl.russiaaddresshelper.layer_request_filter.mass_request", "BUILDING=true,PARCEL=true,CONSTRUCTS=false,UNFINISHED=false")

        private fun getLayers(setting: StringProperty): Map<NSPDLayer, Boolean> {
            val settingValue = setting.get()
            return  settingValue.split(",")
                    .associate { r -> Pair(NSPDLayer.valueOf(r.substringBefore("=").trim()), r.substringAfter("=").trim().toBoolean()) }
        }

        fun getClickActionLayers() : Map<NSPDLayer, Boolean> {
            return getLayers(CLICK_ACTION_LAYER_FILTER)
        }

        fun getMassRequestActionLayers (): Map<NSPDLayer, Boolean> {
            return getLayers(MASS_REQUEST_ACTION_LAYER_FILTER)
        }

        fun getClickActionEnabledLayers (): Set<NSPDLayer> {
            return getClickActionLayers().filter { (_, enabled) -> enabled }.keys
        }

        fun getMassRequestActionEnabledLayers (): Set<NSPDLayer> {
            return getMassRequestActionLayers().filter { (_, enabled) -> enabled }.keys.filter { it.hasAddressInfo() }.toSet()
        }

        fun saveSettings(data: Map<NSPDLayer, Pair<Boolean, Boolean>>) {
            val clickSettings = data.map{entry -> "${entry.key.name}=${entry.value.first}"}.joinToString(",")
            CLICK_ACTION_LAYER_FILTER.put(clickSettings)
            val massSettings = data.map{entry -> "${entry.key.name}=${entry.value.second}"}.joinToString(",")
            MASS_REQUEST_ACTION_LAYER_FILTER.put(massSettings)
        }
    }
}