package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks

import org.openstreetmap.josm.actions.upload.UploadHook
import org.openstreetmap.josm.data.APIDataSet
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.tools.Logging


class EGRNCleanPluginCache : UploadHook {
    override fun checkUpload(apiDataSet: APIDataSet): Boolean {
        var removedCount = RussiaAddressHelperPlugin.egrnResponses.size
        RussiaAddressHelperPlugin.emptyAllCaches()
        val editLayer = MainApplication.getLayerManager().editLayer
        editLayer?.validationErrors?.clear()

        Logging.info("EGRN-PLUGIN Removed uploaded addresses from plugin validation cache ($removedCount)")

        return true
    }

}