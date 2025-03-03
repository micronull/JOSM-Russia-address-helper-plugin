package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks

import org.openstreetmap.josm.actions.upload.UploadHook
import org.openstreetmap.josm.data.APIDataSet
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.FileHelper
import org.openstreetmap.josm.tools.Logging


class EGRNCleanPluginCache : UploadHook {
    override fun checkUpload(apiDataSet: APIDataSet): Boolean {
        val removedCount = RussiaAddressHelperPlugin.cache.size()
        if (CommonSettingsReader.EXPORT_PARSED_DATA_TO_CSV.get()) {
            val filename = FileHelper.getCurrentExportFilename()
            RussiaAddressHelperPlugin.cache.exportData(filename)
        }
        RussiaAddressHelperPlugin.cache.emptyCache()
        val editLayer = MainApplication.getLayerManager().editLayer
        editLayer?.validationErrors?.clear()

        Logging.info("EGRN-PLUGIN Removed uploaded addresses from plugin validation cache ($removedCount)")

        return true
    }

}