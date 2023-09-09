package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks

import org.openstreetmap.josm.actions.upload.UploadHook
import org.openstreetmap.josm.data.APIDataSet
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.tools.Logging


class EGRNCleanPluginCache : UploadHook {
    override fun checkUpload(apiDataSet: APIDataSet): Boolean {
        val objectsToUpload = apiDataSet.primitives
        var removedCount = 0
        objectsToUpload.forEach { primitive ->
            val egrnResult = RussiaAddressHelperPlugin.egrnResponses[primitive]
            if (egrnResult != null && primitiveHasAddress(primitive)) { //выбираем примитивы, у которых по итогам редактирования заполнены адресные тэги
                //возможно, стоит удалять так же и примитивы,
                // для которых не был распознан адрес (условия?)
                RussiaAddressHelperPlugin.removeFromAllCaches(primitive)
                removedCount++
            }
        }

        val editLayer = MainApplication.getLayerManager().editLayer
        editLayer?.validationErrors?.clear()

        Logging.info("EGRN-PLUGIN Removed uploaded addresses from plugin validation cache ($removedCount)")

        return true
    }

    private fun primitiveHasAddress(primitive: OsmPrimitive): Boolean {
        return primitive.hasTag("addr:housenumber") && (primitive.hasTag("addr:street") || primitive.hasTag("addr:place"))
    }
}