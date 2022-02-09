package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.layer.WMSLayer
import org.openstreetmap.josm.gui.widgets.JosmComboBox

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.LayerShiftSettingsReader

import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.*

class LayerShiftSettingsPanel : JPanel(GridBagLayout()) {
    private val coordinatesShiftLabel = JLabel(I18n.tr("Enable requested coordinates shift according to the layer:"))

    private val shiftSourceLayerCombo: JosmComboBox<String> = JosmComboBox()

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        panel.add(coordinatesShiftLabel)

        fillComboWithLayers()

        panel.add(shiftSourceLayerCombo, GBC.eol().insets(10, 0, 0, 0))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        LayerShiftSettingsReader.SHIFT_SOURCE_LAYER.put(shiftSourceLayerCombo.selectedItem?.toString() ?: "")
    }

    fun fillComboWithLayers() {
        val currentWMSLayers: List<WMSLayer> = MainApplication.getLayerManager().getLayersOfType(WMSLayer::class.java)
        shiftSourceLayerCombo.removeAllItems()
        if (currentWMSLayers.isEmpty()) {
            shiftSourceLayerCombo.addItem(LayerShiftSettingsReader.SHIFT_SOURCE_LAYER.get())
            shiftSourceLayerCombo.selectedIndex = 0
        } else {
            currentWMSLayers.forEach {
                shiftSourceLayerCombo.addItem(it.name)
                if (it.name == LayerShiftSettingsReader.SHIFT_SOURCE_LAYER.get()) {
                    shiftSourceLayerCombo.selectedItem = it.name
                }
            }
        }
    }
}