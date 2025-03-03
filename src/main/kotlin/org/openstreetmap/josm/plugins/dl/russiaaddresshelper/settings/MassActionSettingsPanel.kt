package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.MassActionSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.table.FilterTagsTable
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.*

class MassActionSettingsPanel : JPanel(GridBagLayout()) {

    private val filterTagsTable =
        FilterTagsTable(MassActionSettingsReader.EGRN_MASS_ACTION_FILTER_LIST.get())
    private val enableExtAttributes = JCheckBox(I18n.tr("Проставлять дополнительные тэги по данным ЕГРН"))


    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        val infoLabel = JMultilineLabel(
            "Объекты, у которых есть тэги с этими значениями, будут отфильтрованы из выделения." +
                    "<br><b>*</b> = любое значение",
            false,
            true)
        infoLabel.setMaxWidth(600)
        panel.add(infoLabel, GBC.eop().anchor(GBC.NORTH).fill(GBC.HORIZONTAL))

        filterTagsTable.tableHeader.reorderingAllowed = false

        filterTagsTable.rowSelectionAllowed = false
        filterTagsTable.columnSelectionAllowed = false
        filterTagsTable.cellSelectionEnabled = false
        filterTagsTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        filterTagsTable.preferredScrollableViewportSize = Dimension(500, 200)
        filterTagsTable.columnModel.getColumn(0).preferredWidth = 100
        filterTagsTable.columnModel.getColumn(1).preferredWidth = 400

        panel.add(JScrollPane(filterTagsTable), GBC.eol().insets(10, 0, 0, 0))
        enableExtAttributes.toolTipText = "Проставлять тэги building, building:levels, start_date, если их нет"
        enableExtAttributes.isSelected = MassActionSettingsReader.EGRN_MASS_ACTION_USE_EXT_ATTRIBUTES.get()
        panel.add(enableExtAttributes, GBC.eol().anchor(GBC.NORTHWEST).insets(0,20,0,0))
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        MassActionSettingsReader.EGRN_MASS_ACTION_FILTER_LIST.put(filterTagsTable.getData())
        MassActionSettingsReader.EGRN_MASS_ACTION_USE_EXT_ATTRIBUTES.put(enableExtAttributes.isSelected)
    }

}