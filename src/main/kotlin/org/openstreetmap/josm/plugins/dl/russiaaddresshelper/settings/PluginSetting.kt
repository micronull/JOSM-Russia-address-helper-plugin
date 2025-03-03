package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.GettingStarted.LinkGeneral
import org.openstreetmap.josm.gui.preferences.ExtensibleTabPreferenceSetting
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class PluginSetting : ExtensibleTabPreferenceSetting("icon.svg", I18n.tr("Russia address helper settings"), "", false) {
    companion object {
        val egrnSettingsPanel = EgrnRequestSettingsPanel()
        val clickSettingsPanel = ClickActionSettingsPanel()
        val tagSettingsPanel = TagSettingsPanel()
        val layerShiftSettingsPanel = LayerShiftSettingsPanel()
        val validationSettingsPanel = ValidationSettingsPanel()
        val queryLayersFilterPanel = QueryLayersFilterPanel()
        val massActionSettingsPanel = MassActionSettingsPanel()
        val buildingTagsSettingsPanel = BuildingTagsSettingsPanel()

    }

    private fun addSettingsSection(p: JPanel, name: String, section: JPanel, gbc: GBC) {
        val lbl = JLabel(name)
        lbl.font = lbl.font.deriveFont(Font.BOLD)
        lbl.labelFor = section
        p.add(lbl, GBC.std())
        p.add(JSeparator(), GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 0, 0, 0))
        p.add(section, gbc.insets(20, 5, 0, 10))
    }

    private fun addPanelToPane(panel: JPanel): JScrollPane {
        panel.add(JPanel(), GBC.eol().fill(GridBagConstraints.BOTH))
        val scrollPane = JScrollPane(panel)
        scrollPane.border = BorderFactory.createEmptyBorder()
        return scrollPane
    }

    override fun addGui(gui: PreferenceTabbedPane) {
        val pane = tabPane
        //общие настройки
        val commonPanel = VerticallyScrollablePanel(GridBagLayout())

        addSettingsSection(
            commonPanel,
            I18n.tr("Query layers settings"),
            queryLayersFilterPanel,
            GBC.eol().fill(GridBagConstraints.HORIZONTAL).anchor(GridBagConstraints.NORTHEAST)
        )

        addSettingsSection(
            commonPanel,
            I18n.tr("Layer shift settings"),
            layerShiftSettingsPanel,
            GBC.eol().fill(GridBagConstraints.HORIZONTAL)
        )

        addSettingsSection(
            commonPanel,
            I18n.tr("Miscellaneous"),
            tagSettingsPanel,
            GBC.eol().fill(GridBagConstraints.HORIZONTAL)
        )

        val infoLabel = LinkGeneral(
            "<a href=\"https://github.com/micronull/JOSM-Russia-address-helper-plugin\">Домашняя страница (руководство)</a>" +
                    "<br><br><a href=\"https://github.com/micronull/JOSM-Russia-address-helper-plugin/blob/master/CHANGELOG.md\">Изменения в текущей версии</a>"
        )
        infoLabel.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        commonPanel.add(infoLabel, GBC.eop().anchor(GBC.NORTH).fill(GBC.HORIZONTAL))
        commonPanel.add(infoLabel)

        layerShiftSettingsPanel.fillComboWithLayers()

        pane.addTab(I18n.tr("Common settings"), addPanelToPane(commonPanel))
        //настройки сети
        pane.addTab(I18n.tr("Network settings."), egrnSettingsPanel)
        egrnSettingsPanel.initFromPreferences()
        //настройки по клику
        pane.addTab(
            I18n.tr("Click action settings"),
            clickSettingsPanel
        )
        clickSettingsPanel.initFromPreferences()
        //настройки массовые
        pane.addTab(
            I18n.tr("Mass action settings"),
            addPanelToPane(massActionSettingsPanel)
        )
        //маппинг типов зданий
        pane.addTab(
            I18n.tr("Buildings ext info"),
            addPanelToPane(buildingTagsSettingsPanel)
        )
        //не пойми что собранное в кучу
        val tagSettingsPanel1 = VerticallyScrollablePanel(GridBagLayout())

        addSettingsSection(
            tagSettingsPanel1,
            I18n.tr("Validation settings"),
            validationSettingsPanel,
            GBC.eol().fill(GridBagConstraints.HORIZONTAL)
        )
        validationSettingsPanel.initFromPreferences()

        pane.addTab(I18n.tr("Validation settings"), addPanelToPane(tagSettingsPanel1))

        super.addGui(gui)
    }

    override fun ok(): Boolean {
        egrnSettingsPanel.saveToPreferences()
        clickSettingsPanel.saveToPreferences()
        tagSettingsPanel.saveToPreferences()
        layerShiftSettingsPanel.saveToPreferences()
        validationSettingsPanel.saveToPreferences()
        queryLayersFilterPanel.saveToPreferences()
        massActionSettingsPanel.saveToPreferences()
        buildingTagsSettingsPanel.saveToPreferences()

        return false
    }
}