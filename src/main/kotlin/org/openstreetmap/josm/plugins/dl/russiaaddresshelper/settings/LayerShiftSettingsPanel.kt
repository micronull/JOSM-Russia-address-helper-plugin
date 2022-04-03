package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.layer.WMSLayer
import org.openstreetmap.josm.gui.widgets.JosmComboBox

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.LayerShiftSettingsReader

import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.*

class LayerShiftSettingsPanel : JPanel(GridBagLayout()) {
    private val shiftLayerCoordinatesShiftLabel =
        JLabel(I18n.tr("Enable requested coordinates shift for requests according to the layer:"))
    private val shiftLayerCombo: JosmComboBox<String> = JosmComboBox()

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        panel.add(shiftLayerCoordinatesShiftLabel)

        panel.add(shiftLayerCombo, GBC.eol().insets(10, 0, 0, 0))

        fillComboWithLayers()

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        LayerShiftSettingsReader.LAYER_SHIFT_SOURCE.put(shiftLayerCombo.selectedItem?.toString() ?: "")
    }

    fun fillComboWithLayers() {
        val currentWMSLayers: List<WMSLayer> = MainApplication.getLayerManager().getLayersOfType(WMSLayer::class.java)
        shiftLayerCombo.removeAllItems()
        if (currentWMSLayers.isEmpty()) {
            shiftLayerCombo.addItem(LayerShiftSettingsReader.LAYER_SHIFT_SOURCE.get())
            shiftLayerCombo.selectedIndex = 0
        } else {
            currentWMSLayers.forEach {
                shiftLayerCombo.addItem(it.name)
                if (it.name == LayerShiftSettingsReader.LAYER_SHIFT_SOURCE.get()) {
                    shiftLayerCombo.selectedItem = it.name
                }
            }
        }
    }
}