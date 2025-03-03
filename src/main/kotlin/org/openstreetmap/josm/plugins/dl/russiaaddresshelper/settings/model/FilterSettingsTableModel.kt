package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model

import org.openstreetmap.josm.tools.I18n
import javax.swing.table.AbstractTableModel

class FilterSettingsTableModel(tagSettings: Map<String, List<String>>) : AbstractTableModel() {

    private var values: ArrayList<Pair<String, String>>

    init {
        this.values =
            ArrayList(tagSettings.map { (key, values) -> Pair(key, values.joinToString(",")) }
                .toList())
    }

    fun fillData(filterSettings: Map<String, List<String>>) {
        this.values =
            ArrayList(filterSettings.map { (key, values) -> Pair(key, values.joinToString(",")) }
                .toList())
    }

    fun getData(): Map<String, List<String>> {
        return values.filter { it.first.isNotBlank() && it.second.isNotBlank() }.associate { data ->
            data.first to if (data.second.contains(",")) data.second.split(",") else listOf(data.second)
        }
    }

    override fun getRowCount(): Int {
        return values.size + 1
    }

    override fun getColumnCount(): Int {
        return 2
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return true
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return String::class.javaObjectType
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        return if (rowIndex >= values.size) "" else {
            val row = values[rowIndex]
            when (columnIndex) {
                0 -> row.first
                1 -> row.second
                else -> {
                    "undefined"
                }
            }
        }
    }

    override fun getColumnName(column: Int): String? {
        return when (column) {
            0 -> I18n.tr("Tag", *arrayOfNulls(0))
            1 -> I18n.tr("Values", *arrayOfNulls(0))
            else -> null
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex in 0..1 && aValue is String) {
            if (columnIndex == 0) {
                val filteredTag = filterTag(aValue)
                if (rowIndex >= values.size && filteredTag.isNotBlank()) {
                    values.add(Pair(filteredTag, ""))
                } else {
                    values[rowIndex] = Pair(filteredTag, values[rowIndex].second)
                }

            } else {
                val filteredValues = filterValues(aValue)
                if (rowIndex >= values.size && filteredValues.isNotBlank()) {
                    values.add(Pair("", filteredValues))
                } else {
                    values[rowIndex] = Pair(values[rowIndex].first, filteredValues)
                }
            }
            super.setValueAt(aValue, rowIndex, columnIndex)
            super.fireTableDataChanged()
        }
    }

    private fun filterValues(value: String): String {
        //TODO фильтрация по регекспу - буквы цифры двоеточие запятая
        return value
    }

    private fun filterTag(value: String): String {
        //TODO фильтрация по регекспу - буквы цифры двоеточие
        return value
    }

}