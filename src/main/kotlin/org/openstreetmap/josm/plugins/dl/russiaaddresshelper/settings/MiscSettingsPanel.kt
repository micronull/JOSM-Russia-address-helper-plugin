package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader.Companion.EXPORT_PARSED_DATA_TO_CSV
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.SettingsSaver.Companion.saveDouble
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.SettingsSaver.Companion.saveInteger
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.FileHelper
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.*

class MiscSettingsPanel : JPanel(GridBagLayout()) {
    private val doubleClearDistance = JosmTextField(4)
    private val exportToCSV = JCheckBox(I18n.tr("Export parsed address data to CSV file"))
    private val enableGeometryOrthogonalization = JCheckBox(I18n.tr("Orthogonalize imported geometry"))
    private val geometryOrthogonalizationThreshold = JosmTextField(3)

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)


        panel.add(JLabel(I18n.tr("Duplicates search distance in meters")), GBC.std().insets(5, 0, 0, 5))
        doubleClearDistance.text = CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.get().toString()
        panel.add(doubleClearDistance, GBC.eol().insets(5, 0, 0, 5))

        panel.add(enableGeometryOrthogonalization, GBC.eol().insets(0,0,40,0))
        panel.add(JLabel(I18n.tr("Orthogonalize, if all angles closer to 90/180 than degrees")), GBC.std().insets(10,0,40,0))
        panel.add(geometryOrthogonalizationThreshold, GBC.eol().insets(5, 0, 0, 5))


        exportToCSV.toolTipText =
            "Данные сохраняются в момент выгрузки в ОСМ в файл ${FileHelper.getCurrentExportFilename()} находящийся в папке редактора."
        panel.add(exportToCSV, GBC.std().insets(0, 0, 0, 5))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())

        enableGeometryOrthogonalization.addActionListener {
            run {
                geometryOrthogonalizationThreshold.isEditable = enableGeometryOrthogonalization.isSelected
            }
        }
        initFromPreferences()
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        saveInteger(CommonSettingsReader.CLEAR_DOUBLE_DISTANCE,doubleClearDistance.getText(), 10, 1000 )
        CommonSettingsReader.EGRN_ENABLE_GEOMETRY_ORTOGONALIZE.put(enableGeometryOrthogonalization.isSelected)
        saveDouble(
            CommonSettingsReader.EGRN_GEOMETRY_ORTOGONALIZE_THRESHOLD,
            geometryOrthogonalizationThreshold.text,
            1.0, 45.0
        )
    }

    fun initFromPreferences() {
        exportToCSV.isSelected = EXPORT_PARSED_DATA_TO_CSV.get()
        enableGeometryOrthogonalization.isSelected = CommonSettingsReader.EGRN_ENABLE_GEOMETRY_ORTOGONALIZE.get()
        geometryOrthogonalizationThreshold.text =
            CommonSettingsReader.EGRN_GEOMETRY_ORTOGONALIZE_THRESHOLD.get().toString()
    }

}