package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JSeparator

class PluginSetting : DefaultTabPreferenceSetting("icon.svg", I18n.tr("Russia address helper settings"), "", false) {
    companion object {
        val egrnSettingsPanel = EgrnRequestSettingsPanel()
        val tagSettingsPanel = TagSettingsPanel()
        val layerShiftSettingsPanel = LayerShiftSettingsPanel()
        val validationSettingsPanel = ValidationSettingsPanel()

    }

    override fun addGui(gui: PreferenceTabbedPane) {
        val panel = VerticallyScrollablePanel(GridBagLayout())

        panel.add(JLabel(I18n.tr("Network settings.")), GBC.eol())
        panel.add(egrnSettingsPanel, GBC.eop().fill(GridBagConstraints.HORIZONTAL))
        egrnSettingsPanel.initFromPreferences()

        panel.add(JSeparator(), GBC.eop().fill(GBC.HORIZONTAL))

        panel.add(tagSettingsPanel, GBC.eop().fill(GridBagConstraints.HORIZONTAL))

        panel.add(JSeparator(), GBC.eop().fill(GBC.HORIZONTAL))

        panel.add(validationSettingsPanel, GBC.eop().fill(GridBagConstraints.HORIZONTAL))
        validationSettingsPanel.initFromPreferences()

        panel.add(JSeparator(), GBC.eop().fill(GBC.HORIZONTAL))
        panel.add(layerShiftSettingsPanel, GBC.eop().fill(GridBagConstraints.HORIZONTAL))
        layerShiftSettingsPanel.fillComboWithLayers()

        panel.add(Box.createVerticalGlue(), GBC.eol().fill(GBC.VERTICAL))

        createPreferenceTabWithScrollPane(gui, panel)
    }

    override fun ok(): Boolean {
        egrnSettingsPanel.saveToPreferences()
        tagSettingsPanel.saveToPreferences()
        layerShiftSettingsPanel.saveToPreferences()
        validationSettingsPanel.saveToPreferences()
        return false
    }
}