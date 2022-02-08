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
    private val enableCoordinatesShift =
        JCheckBox(I18n.tr("Enable requested coordinates shift according to the layer."))

    private val shiftSourceLayer: JosmComboBox<String> = JosmComboBox()

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        enableCoordinatesShift.isSelected = LayerShiftSettingsReader.ENABLE_COORDINATES_SHIFT.get()
        panel.add(enableCoordinatesShift, GBC.eol().insets(0, 0, 0, 0))


        val currentWMSLayers: List<WMSLayer> = MainApplication.getLayerManager().getLayersOfType(WMSLayer::class.java)
        currentWMSLayers.forEach { shiftSourceLayer.addItem(it.name) }

        shiftSourceLayer.isEnabled = enableCoordinatesShift.isSelected
        shiftSourceLayer.setHint(I18n.tr("Selected layer shift will be appended to building coordinates"))


        shiftSourceLayer.selectedItem = LayerShiftSettingsReader.SHIFT_SOURCE_LAYER.get()
        //заполнять список выбора текущими слоями
        //проверять есть ли среди них сохраненное значение
        //если нет - сбрасывать галку и значение в списке на "none"

        panel.add(shiftSourceLayer, GBC.eol().insets(0, 0, 0, 0))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        LayerShiftSettingsReader.ENABLE_COORDINATES_SHIFT.put(enableCoordinatesShift.isSelected)
        LayerShiftSettingsReader.SHIFT_SOURCE_LAYER.put(shiftSourceLayer.selectedItem?.toString() ?: "none")
    }
}