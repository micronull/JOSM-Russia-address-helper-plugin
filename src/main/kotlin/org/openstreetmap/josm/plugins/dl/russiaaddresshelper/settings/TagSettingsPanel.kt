package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader.Companion.EXPORT_PARSED_DATA_TO_CSV
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.FileHelper
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import javax.swing.*

class TagSettingsPanel : JPanel(GridBagLayout()) {
    private val egrnAddrRecord = JCheckBox(I18n.tr("Record address from egrn to addr:RU:egrn tag."))
    private val doubleClearDistance = JosmTextField(4)
    private val exportToCSV = JCheckBox(I18n.tr("Export parsed address data to CSV file"))

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        egrnAddrRecord.isSelected = TagSettingsReader.EGRN_ADDR_RECORD.get()
        panel.add(egrnAddrRecord, GBC.eol().insets(0, 0, 0, 0))

        panel.add(JLabel(I18n.tr("Duplicates search distance in meters")), GBC.std())
        doubleClearDistance.text = CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.get().toString()
        panel.add(doubleClearDistance, GBC.eop().insets(5, 0, 0, 5))
        exportToCSV.isSelected = EXPORT_PARSED_DATA_TO_CSV.get()
        exportToCSV.toolTipText =
            "Данные сохраняются в момент выгрузки в ОСМ в файл ${FileHelper.getCurrentExportFilename()} находящийся в папке редактора."
        panel.add(exportToCSV, GBC.std())

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        TagSettingsReader.EGRN_ADDR_RECORD.put(egrnAddrRecord.isSelected)
        EXPORT_PARSED_DATA_TO_CSV.put(exportToCSV.isSelected)
        val distanceText = doubleClearDistance.getText()
        try {
            var distance = Integer.valueOf(distanceText)

            if (distance <= 0) {
                distance = 100
            }
            CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.put(distance)
        } catch (e: NumberFormatException) {
            Logging.warn(e.message + "(need numeric)")
            CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.put(100)
        }
    }

}