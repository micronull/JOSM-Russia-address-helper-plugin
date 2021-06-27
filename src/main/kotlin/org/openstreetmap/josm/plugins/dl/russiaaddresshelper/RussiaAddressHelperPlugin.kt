package org.openstreetmap.josm.plugins.dl.russiaaddresshelper

import org.openstreetmap.josm.data.Version
import org.openstreetmap.josm.data.validation.OsmValidator
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.MainMenu
import org.openstreetmap.josm.gui.MapFrame
import org.openstreetmap.josm.gui.download.DownloadDialog
import org.openstreetmap.josm.gui.preferences.PreferenceSetting
import org.openstreetmap.josm.plugins.Plugin
import org.openstreetmap.josm.plugins.PluginInformation
import org.openstreetmap.josm.tools.I18n
import javax.swing.ImageIcon


class RussiaAddressHelperPlugin(info: PluginInformation) : Plugin(info) {
    init {
        val menu = MainApplication.getMenu().toolsMenu;

        menu.addSeparator()
        menu.add(RussiaAddressHelperPluginAction())

        versionInfo = String.format("JOSM/%s JOSM-RussiaAddressHelper/%s", Version.getInstance().versionString, info.version);
    }

    override fun getPreferenceSetting(): PreferenceSetting {
        return RussiaAddressHelperPluginSetting()
    }

    companion object {
        lateinit var versionInfo: String
            private set

        val ACTION_NAME = I18n.tr("Russia address helper")
        val ICON_NAME = "icon.svg"
    }
}