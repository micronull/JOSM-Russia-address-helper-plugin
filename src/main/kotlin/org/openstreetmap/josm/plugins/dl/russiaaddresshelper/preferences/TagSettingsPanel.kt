package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.preferences

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.TagSettingsReader
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JPanel

class TagSettingsPanel : JPanel(GridBagLayout()) {
    private val egrnAddrRecord = JCheckBox(I18n.tr("Record address from egrn to addr:RU:egrn tag."))
    private val doubleClear = JCheckBox(I18n.tr("Enable duplicate cleaning."))

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        egrnAddrRecord.isSelected = TagSettingsReader.EGRN_ADDR_RECORD.get()
        panel.add(egrnAddrRecord, GBC.eol().insets(0, 0, 0, 0))

        doubleClear.isSelected = TagSettingsReader.ENABLE_CLEAR_DOUBLE.get()
        panel.add(doubleClear, GBC.eol().insets(0, 0, 0, 0))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        TagSettingsReader.EGRN_ADDR_RECORD.put(egrnAddrRecord.isSelected)
        TagSettingsReader.ENABLE_CLEAR_DOUBLE.put(doubleClear.isSelected)
    }
}