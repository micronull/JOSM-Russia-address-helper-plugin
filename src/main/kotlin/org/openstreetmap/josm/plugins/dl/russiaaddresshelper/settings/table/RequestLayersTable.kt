package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.table

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model.LayerSettingsTableModel
import javax.swing.JTable


class RequestLayersTable(clickSettings: Map<NSPDLayer, Boolean>, massSettings: Map<NSPDLayer, Boolean>) :
    JTable(LayerSettingsTableModel(clickSettings, massSettings)) {

        fun getData(): Map<NSPDLayer, Pair<Boolean,Boolean>> {
            val tableValues = (this.model as LayerSettingsTableModel).getValues()
            return tableValues.associate { data ->
                NSPDLayer.valueOf(data.first.name) to Pair(data.second.first, data.second.second)
            }
        }
}
