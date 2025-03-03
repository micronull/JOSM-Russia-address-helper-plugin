package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions

import org.openstreetmap.josm.actions.JosmAction
import org.openstreetmap.josm.data.imagery.ImageryInfo
import org.openstreetmap.josm.data.imagery.ImageryLayerInfo
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Shortcut
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent

class AddNSPDLayersAction : JosmAction(
    ACTION_NAME, ICON_NAME, null, Shortcut.registerShortcut(
        "data:add_pkk_wms_layers", I18n.tr("Data: {0}", I18n.tr(ACTION_NAME)), KeyEvent.KEY_LOCATION_UNKNOWN, Shortcut.NONE
    ), false
) {
    companion object {
        val ACTION_NAME = I18n.tr("Add PKK WMS imagery layers")
        val ICON_NAME = "dialogs/add_wms"
    }

    override fun actionPerformed(e: ActionEvent?) {
        NSPDLayer.values().forEach { layer ->
            val info = ImageryInfo(getImageryName(layer), getWMSUrl(layer))
            info.imageryType = ImageryInfo.ImageryType.WMS
            info.isGeoreferenceValid = true
            info.customHttpHeaders = getCommonHeaders()
            val existingLayer = ImageryLayerInfo.instance.layers.find { l -> info.name.equals(l.name) }
            if (existingLayer != null) {
                ImageryLayerInfo.instance.remove(existingLayer)
            }
            ImageryLayerInfo.instance.add(info)
        }
        ImageryLayerInfo.instance.save()
    }

    private fun getWMSUrl(layer: NSPDLayer): String {
        var url =  EgrnSettingsReader.NSPD_GET_MAP_REQUEST_URL.get().replace("{layer}", layer.layerId.toString())
        if (EgrnSettingsReader.EGRN_DISABLE_SSL_FOR_REQUEST.get()) {
            url = url.replace("{site}", EgrnSettingsReader.LOCALHOST_PROXY_URL.get())
        } else {
            url = url.replace("{site}", EgrnSettingsReader.NSPD_SITE_URL.get())
        }
        return url
    }

    private fun getImageryName(layer: NSPDLayer): String {
        return "WMS ПКК " + layer.description
    }

    private fun getCommonHeaders():Map<String,String> {
        return mapOf("Referer" to EgrnSettingsReader.NSPD_SITE_URL.get())
    }

}

