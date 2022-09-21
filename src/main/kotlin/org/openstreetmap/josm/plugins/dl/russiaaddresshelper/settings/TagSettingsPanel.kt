package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import javax.swing.*

class TagSettingsPanel : JPanel(GridBagLayout()) {
    private val egrnAddrRecord = JCheckBox(I18n.tr("Record address from egrn to addr:RU:egrn tag."))
    private val doubleClear = JCheckBox(I18n.tr("Enable duplicate cleaning."))
    private val doubleClearDistance = JosmTextField(4)

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        egrnAddrRecord.isSelected = TagSettingsReader.EGRN_ADDR_RECORD.get()
        panel.add(egrnAddrRecord, GBC.eol().insets(0, 0, 0, 0))

        doubleClear.isSelected = TagSettingsReader.ENABLE_CLEAR_DOUBLE.get()
        panel.add(doubleClear, GBC.eol().insets(0, 0, 0, 0))

        panel.add(JLabel(I18n.tr("Duplicates search distance in meters")), GBC.std())
        doubleClearDistance.text = TagSettingsReader.CLEAR_DOUBLE_DISTANCE.get().toString()
        panel.add(doubleClearDistance, GBC.eop().insets(5, 0, 0, 5))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        TagSettingsReader.EGRN_ADDR_RECORD.put(egrnAddrRecord.isSelected)
        TagSettingsReader.ENABLE_CLEAR_DOUBLE.put(doubleClear.isSelected)
        val distanceText = doubleClearDistance.getText()
        try {
            var distance = Integer.valueOf(distanceText)

            if (distance <= 0) {
                distance = 100
            }
            TagSettingsReader.CLEAR_DOUBLE_DISTANCE.put(distance)
        } catch (e: NumberFormatException) {
            Logging.warn(e.message + "(need numeric)")
            TagSettingsReader.CLEAR_DOUBLE_DISTANCE.put(100)
        }
    }

}