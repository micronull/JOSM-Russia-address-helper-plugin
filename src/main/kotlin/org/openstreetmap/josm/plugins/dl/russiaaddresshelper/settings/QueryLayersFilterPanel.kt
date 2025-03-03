package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.LayerFilterSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model.LayerSettingsTableModel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.table.RequestLayersTable

import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.*

class QueryLayersFilterPanel : JPanel(GridBagLayout()) {
    private val label =
        JLabel(I18n.tr("Select query layers for action:"))
    private val layerFilterTable: RequestLayersTable = RequestLayersTable(mapOf(), mapOf())

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        panel.add(label, GBC.eol().insets(10, 0, 0, 0))

        //panel.add(Box.createVerticalGlue(), GBC.eol().fill())

        layerFilterTable.tableHeader.reorderingAllowed = false

        layerFilterTable.rowSelectionAllowed = false
        layerFilterTable.columnSelectionAllowed = false
        layerFilterTable.cellSelectionEnabled = false
        layerFilterTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        layerFilterTable.preferredScrollableViewportSize = Dimension(450, 100)
        layerFilterTable.columnModel.getColumn(0).preferredWidth = 100
        layerFilterTable.columnModel.getColumn(1).preferredWidth = 150
        layerFilterTable.columnModel.getColumn(2).preferredWidth = 100
        layerFilterTable.columnModel.getColumn(3).preferredWidth = 100

        panel.add(JScrollPane(layerFilterTable), GBC.eol().insets(10, 0, 0, 0))

        fillTableWithData()

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */

    fun fillTableWithData() {
        val clickSettings = LayerFilterSettingsReader.getClickActionLayers()
        val massSettings = LayerFilterSettingsReader.getMassRequestActionLayers()
        (layerFilterTable.model as LayerSettingsTableModel).fillData(clickSettings,massSettings)
    }

    fun saveToPreferences() {
        LayerFilterSettingsReader.saveSettings(layerFilterTable.getData())
    }
}