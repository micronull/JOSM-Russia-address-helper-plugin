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
    private val parcelsCoordinatesShiftLabel = JLabel(I18n.tr("Enable requested coordinates shift for parcels request according to the layer:"))
    private val buildingsCoordinatesShiftLabel = JLabel(I18n.tr("Enable requested coordinates shift for buildings request according to the layer:"))
    private val parcelsLayerCombo: JosmComboBox<String> = JosmComboBox()
    private val buildingsLayerCombo: JosmComboBox<String> = JosmComboBox()
    private val useBuildingsLayerCheckbox = JCheckBox(I18n.tr("Use EGRN buildings layer as address source"))


    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        panel.add(parcelsCoordinatesShiftLabel)

        panel.add(parcelsLayerCombo, GBC.eol().insets(10, 0, 0, 0))

        panel.add(buildingsCoordinatesShiftLabel)

        panel.add(buildingsLayerCombo, GBC.eol().insets(10, 0, 0, 0))

        fillComboWithLayers()

        useBuildingsLayerCheckbox.isSelected = LayerShiftSettingsReader.USE_BUILDINGS_LAYER_AS_SOURCE.get()
        panel.add(useBuildingsLayerCheckbox, GBC.eol().insets(0, 0, 0, 0))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        LayerShiftSettingsReader.PARCELS_LAYER_SHIFT_SOURCE.put(parcelsLayerCombo.selectedItem?.toString() ?: "")
        LayerShiftSettingsReader.BUILDINGS_LAYER_SHIFT_SOURCE.put(buildingsLayerCombo.selectedItem?.toString() ?: "")
        if (LayerShiftSettingsReader.checkIfBuildingLayerCanBeUsed()) {
            LayerShiftSettingsReader.USE_BUILDINGS_LAYER_AS_SOURCE.put(true)
        }
    }

    fun fillComboWithLayers() {
        val currentWMSLayers: List<WMSLayer> = MainApplication.getLayerManager().getLayersOfType(WMSLayer::class.java)
        parcelsLayerCombo.removeAllItems()
        buildingsLayerCombo.removeAllItems()
        if (currentWMSLayers.isEmpty()) {
            parcelsLayerCombo.addItem(LayerShiftSettingsReader.PARCELS_LAYER_SHIFT_SOURCE.get())
            parcelsLayerCombo.selectedIndex = 0
            buildingsLayerCombo.addItem(LayerShiftSettingsReader.BUILDINGS_LAYER_SHIFT_SOURCE.get())
            buildingsLayerCombo.selectedIndex = 0
        } else {
            currentWMSLayers.forEach {
                parcelsLayerCombo.addItem(it.name)
                if (it.name == LayerShiftSettingsReader.PARCELS_LAYER_SHIFT_SOURCE.get()) {
                    parcelsLayerCombo.selectedItem = it.name
                }
                buildingsLayerCombo.addItem(it.name)
                if (it.name == LayerShiftSettingsReader.BUILDINGS_LAYER_SHIFT_SOURCE.get()) {
                    buildingsLayerCombo.selectedItem = it.name
                }
            }
        }
    }
}