package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks

import org.openstreetmap.josm.actions.upload.UploadHook
import org.openstreetmap.josm.data.APIDataSet
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.tools.Logging


class EGRNCleanPluginCache : UploadHook {
    override fun checkUpload(apiDataSet: APIDataSet): Boolean {
        val objectsToUpload = apiDataSet.primitives
        var removedCount = 0;
        objectsToUpload.forEach { primitive ->
            val egrnResult = RussiaAddressHelperPlugin.egrnResponses[primitive]
            if (egrnResult != null && egrnResult.third.getPreferredAddress() != null) {
                val preferredAddress = egrnResult.third.getPreferredAddress()!!
                if (preferredAddress.getOsmAddress().getBaseAddressTagsWithSource()
                        .all { primitive.hasTag(it.key, it.value) }
                ) {
                    RussiaAddressHelperPlugin.removeFromAllCaches(primitive)
                    removedCount++
                }
            }
        }

        val editLayer = MainApplication.getLayerManager().editLayer
        editLayer?.validationErrors?.clear()

        Logging.info("EGRN-PLUGIN Removed uploaded addresses from plugin validation cache ($removedCount)")

        return true
    }
}