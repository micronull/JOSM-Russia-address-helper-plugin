package org.openstreetmap.josm.plugins.dl.russiaaddresshelper

import org.openstreetmap.josm.data.Version
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.preferences.PreferenceSetting
import org.openstreetmap.josm.plugins.Plugin
import org.openstreetmap.josm.plugins.PluginInformation
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import javax.swing.JMenu

class RussiaAddressHelperPlugin(info: PluginInformation) : Plugin(info) {
    init {
        val menu = MainApplication.getMenu().dataMenu
        menu.isVisible = true

        if (menu.itemCount > 0) {
            menu.addSeparator()
        }

        val subMenu = JMenu(ACTION_NAME)
        subMenu.icon = ImageProvider(ICON_NAME).resource.getPaddedIcon(ImageProvider.ImageSizes.SMALLICON.imageDimension)

        subMenu.add(RussiaAddressHelperPluginSelectedAction())
        subMenu.add(RussiaAddressHelperPluginClickAction())

        menu.add(subMenu)

        versionInfo = String.format("JOSM/%s JOSM-RussiaAddressHelper/%s", Version.getInstance().versionString, info.version)
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