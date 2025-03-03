package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model

import org.openstreetmap.josm.tools.I18n
import javax.swing.table.AbstractTableModel

class StopWordsTableModel(tagSettings: List<String>) : AbstractTableModel() {

    private var values: ArrayList<String>

    init {
        this.values =
            ArrayList(tagSettings)
    }

    fun fillData(filterSettings: List<String>) {
        this.values = ArrayList(filterSettings)
        super.fireTableDataChanged()
    }

    fun getData(): List<String> {
        return values.filter { it.isNotBlank() }
    }

    override fun getRowCount(): Int {
        return values.size + 1
    }

    override fun getColumnCount(): Int {
        return 1
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return true
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return String::class.javaObjectType
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        return if (rowIndex >= values.size) "" else {
            when (columnIndex) {
                0 -> values[rowIndex]
                else -> {
                    "undefined"
                }
            }
        }
    }

    override fun getColumnName(column: Int): String? {
        return when (column) {
            0 -> I18n.tr("Key words", *arrayOfNulls(0))
            else -> null
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 0 && aValue is String) {
            val filteredValue = filterValue(aValue)
            if (rowIndex >= values.size && filteredValue.isNotBlank()) {
                values.add(filteredValue)
            } else {
                if (filteredValue.isNotBlank()) {
                    values[rowIndex] = filteredValue
                } else {
                    values.removeAt(rowIndex)
                }
            }
            super.setValueAt(aValue, rowIndex, columnIndex)
            super.fireTableDataChanged()
        }
    }


    private fun filterValue(value: String): String {
        return value
    }

}