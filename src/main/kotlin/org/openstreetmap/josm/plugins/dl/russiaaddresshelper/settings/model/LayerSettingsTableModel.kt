package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer
import org.openstreetmap.josm.tools.I18n
import javax.swing.table.AbstractTableModel

class LayerSettingsTableModel(clickSettings: Map<NSPDLayer, Boolean>, massSettings: Map<NSPDLayer, Boolean>) : AbstractTableModel() {

    private var values: ArrayList<Pair<NSPDLayer, Pair<Boolean, Boolean>>> = ArrayList()

    fun fillData(clickSettings: Map<NSPDLayer, Boolean>, massSettings: Map<NSPDLayer, Boolean>) {
        this.values =
            ArrayList(clickSettings.map { (layer, enabled) -> Pair(layer, Pair(enabled, massSettings[layer] ?: false)) }
                .toList())
    }

    override fun getRowCount(): Int {
        return values.size
    }

    override fun getColumnCount(): Int {
        return 4

    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex in 2..3
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        if (columnIndex in 2..3) {
            return Boolean::class.javaObjectType
        } else {
            return String::class.javaObjectType
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = values[rowIndex]
        return when (columnIndex) {
            0 -> row.first.name
            1 -> row.first.description
            2 -> row.second.first as Boolean
            3 -> row.second.second as Boolean
            else -> {
                "undefined"
            }
        }
    }

    override fun getColumnName(column: Int): String? {
        return when (column) {
            0 -> I18n.tr("Layer type", *arrayOfNulls(0))
            1 -> I18n.tr("Description", *arrayOfNulls(0))
            2 -> I18n.tr("Click action", *arrayOfNulls(0))
            3 -> I18n.tr("Mass request", *arrayOfNulls(0))
            else -> null
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex in 2..3 && aValue is Boolean) {
            val current = values[rowIndex]
            if (columnIndex == 2) {
                values[rowIndex] = Pair(current.first, Pair(!current.second.first, current.second.second))
                if (values.all { a -> !a.second.first }) values[0] = Pair(values[0].first, Pair(true, values[0].second.second))
            } else {
                values[rowIndex] = Pair(current.first, Pair(current.second.first, !current.second.second))
                if (values.all { a -> !a.second.second }) values[0] = Pair(values[0].first, Pair(values[0].second.first, true))
            }
            super.setValueAt(aValue, rowIndex, columnIndex)
            super.fireTableDataChanged()
        }
    }

    fun getValues() : ArrayList<Pair<NSPDLayer, Pair<Boolean, Boolean>>> {
        return values
    }
}