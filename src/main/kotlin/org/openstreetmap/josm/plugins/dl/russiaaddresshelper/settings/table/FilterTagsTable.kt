package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.table

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model.FilterSettingsTableModel
import javax.swing.JTable


class FilterTagsTable(filterSettings: Map<String, List<String>>) :
    JTable(FilterSettingsTableModel(filterSettings)) {

    fun getData(): Map<String, List<String>> {
        return (this.model as FilterSettingsTableModel).getData()
    }
}

