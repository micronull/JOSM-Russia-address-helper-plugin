package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ClickActionSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.SettingsSaver.Companion.saveDouble
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.SettingsSaver.Companion.saveInteger
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.*

class ClickActionSettingsPanel : JPanel(GridBagLayout()) {
    private val enableGeometryImport = JCheckBox(I18n.tr("Enable geometry import"))
    private val geometryImportAreaThreshold = JosmTextField(3)
    private val boundaryImportAreaThreshold = JosmTextField(3)
    private val enableGeometrySimplification = JCheckBox(I18n.tr("Simplify geometry after import"))
    private val geometrySimplificationThreshold = JosmTextField(3)
    private val enableMergeFeaturesOnSingleNode = JCheckBox(I18n.tr("Merge features tags to single node"))
    private val clickRequestBoundaryExtension = JosmTextField(4)


    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        panel.add(enableGeometryImport, GBC.std())
        panel.add(JLabel(I18n.tr("Remove building polygons smaller than")), GBC.std().insets(0,0,40,0))
        panel.add(geometryImportAreaThreshold, GBC.eop().insets(5, 0, 0, 5))
        panel.add(JLabel(I18n.tr("Remove boundary polygons smaller than")), GBC.std().insets(0,0,40,0).grid(1,1))
        panel.add(boundaryImportAreaThreshold, GBC.eop().insets(5, 0, 0, 5))
        panel.add(enableGeometrySimplification, GBC.std().insets(0,0,40,0))
        panel.add(JLabel(I18n.tr("Simplify ways threshold")), GBC.std())
        panel.add(geometrySimplificationThreshold, GBC.eop().insets(5, 0, 0, 5))
        panel.add(enableMergeFeaturesOnSingleNode, GBC.eop())
        panel.add(JLabel(I18n.tr("Boundaries of request, meters")), GBC.std().insets(5,0,40,5))
        panel.add(clickRequestBoundaryExtension, GBC.eop())

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())

        enableGeometryImport.addActionListener {
            run {
                geometryImportAreaThreshold.isEditable = enableGeometryImport.isSelected
            }
        }
        enableGeometrySimplification.addActionListener {
            run {
                geometrySimplificationThreshold.isEditable = enableGeometrySimplification.isSelected
            }
        }
    }

    /**
     * Initializes the panel from preferences
     */
    fun initFromPreferences() {
        enableGeometryImport.isSelected = ClickActionSettingsReader.EGRN_CLICK_ENABLE_GEOMETRY_IMPORT.get()
        geometryImportAreaThreshold.text =
            ClickActionSettingsReader.EGRN_CLICK_GEOMETRY_IMPORT_THRESHOLD.get().toString()
        boundaryImportAreaThreshold.text =
            ClickActionSettingsReader.EGRN_CLICK_BOUNDARY_IMPORT_THRESHOLD.get().toString()
        enableGeometrySimplification.isSelected = ClickActionSettingsReader.EGRN_CLICK_ENABLE_GEOMETRY_SIMPLIFY.get()
        geometrySimplificationThreshold.text =
            ClickActionSettingsReader.EGRN_CLICK_GEOMETRY_SIMPLIFY_THRESHOLD.get().toString()
        enableMergeFeaturesOnSingleNode.isSelected = ClickActionSettingsReader.EGRN_CLICK_MERGE_FEATURES.get()
        clickRequestBoundaryExtension.text = ClickActionSettingsReader.EGRN_CLICK_BOUNDS_EXTENSION.get().toString()
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        ClickActionSettingsReader.EGRN_CLICK_ENABLE_GEOMETRY_IMPORT.put(enableGeometryImport.isSelected)
        ClickActionSettingsReader.EGRN_CLICK_ENABLE_GEOMETRY_SIMPLIFY.put(enableGeometrySimplification.isSelected)
        ClickActionSettingsReader.EGRN_CLICK_MERGE_FEATURES.put(enableMergeFeaturesOnSingleNode.isSelected)

        saveDouble(
            ClickActionSettingsReader.EGRN_CLICK_GEOMETRY_IMPORT_THRESHOLD,
            geometryImportAreaThreshold.text,
            0.0, 50.0
        )

        saveDouble(
            ClickActionSettingsReader.EGRN_CLICK_BOUNDARY_IMPORT_THRESHOLD,
            boundaryImportAreaThreshold.text,
            0.0, 200.0
        )
        saveDouble(
            ClickActionSettingsReader.EGRN_CLICK_GEOMETRY_SIMPLIFY_THRESHOLD,
            geometrySimplificationThreshold.text,
            0.1, 10.0
        )
        saveInteger(ClickActionSettingsReader.EGRN_CLICK_BOUNDS_EXTENSION, clickRequestBoundaryExtension.text, 5, 500)
    }


}