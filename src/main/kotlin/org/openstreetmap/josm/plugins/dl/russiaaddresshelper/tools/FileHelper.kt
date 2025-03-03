package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter




class FileHelper {
    companion object {
        fun getCurrentExportFilename() :String {
            val formatter = DateTimeFormatter.ofPattern("YYYY_MM_dd")
            val date = LocalDateTime.now()
            return "addressExport_${RussiaAddressHelperPlugin.versionInfo}_${formatter.format(date)}.csv"
        }
    }
}