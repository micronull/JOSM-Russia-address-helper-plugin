package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.table

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model.StopWordsTableModel
import javax.swing.JTable


class StopWordsTable(filterSettings: List<String>) :
    JTable(StopWordsTableModel(filterSettings)) {

    fun getData(): List<String> {
        return (this.model as StopWordsTableModel).getData()
    }

    fun fillData(data: List<String>) {
        (this.model as StopWordsTableModel).fillData(data)
    }
}

