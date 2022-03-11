package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io

import org.openstreetmap.josm.data.preferences.StringProperty
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.layer.WMSLayer
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import javax.swing.JOptionPane

class LayerShiftSettingsReader {
    companion object {
        /**
         * @since 0.7
         */

        val LAYER_SHIFT_SOURCE = StringProperty("dl.russiaaddresshelper.tag.layer_shift_source", "")

        fun getValidShiftLayer(setting: StringProperty): WMSLayer? {
            val shiftLayerName = setting.get()
            if (shiftLayerName.isBlank()) {
                return null
            }
            val layers: List<WMSLayer> = MainApplication.getLayerManager().getLayersOfType(WMSLayer::class.java)
            val shiftLayer = layers.find { it.name == shiftLayerName }

            if (shiftLayer == null) {
                setting.put("")
                val msg =
                    "Shift layer found in settings, but is absent in current layers, correction not applied. Name:"
                Logging.warn("$msg ${setting.key} $shiftLayerName")
                val msgLoc = I18n.tr(msg)
                Notification("$msgLoc ${setting.key} $shiftLayerName").setIcon(JOptionPane.WARNING_MESSAGE).show()
                return null
            }
            return shiftLayer
        }
    }
}