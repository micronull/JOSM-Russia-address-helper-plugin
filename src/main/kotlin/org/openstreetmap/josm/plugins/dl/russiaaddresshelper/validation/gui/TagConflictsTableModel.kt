package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.gui

import org.openstreetmap.josm.tools.I18n
import java.awt.Color
import javax.swing.table.AbstractTableModel

class TagConflictsTableModel(existingTags: Map<String, String>, conflictTags: Map<String, String>) :
    AbstractTableModel() {

    private var existingTags: ArrayList<Pair<String, String>>
    private var conflictTags: ArrayList<Pair<String, String>>
    private var mergedTags: ArrayList<Pair<String, String>>

    private val LEFT_COLOR = Color(255,0,0, 30)
    private val RIGHT_COLOR = Color(0,255,0, 30)

    init {
        this.conflictTags = ArrayList(conflictTags.toList())
        this.existingTags = ArrayList(existingTags.filter { (key, _) -> conflictTags.containsKey(key) }.toList())
        this.mergedTags = ArrayList(List(conflictTags.size) { Pair("", "") })
    }

    fun getData(): Map<String, String> {
        return mergedTags.toMap()
    }

    override fun getRowCount(): Int {
        return conflictTags.size
    }

    override fun getColumnCount(): Int {
        return 4
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return false
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return String::class.javaObjectType
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        return when (columnIndex) {
            0 -> conflictTags[rowIndex].first
            1 -> existingTags[rowIndex].second
            2 -> mergedTags[rowIndex].second
            3 -> conflictTags[rowIndex].second
        else -> "undefined"
        }
    }

    override fun getColumnName(column: Int): String? {
        return when (column) {
            0 -> I18n.tr("Tag", *arrayOfNulls(0))
            1 -> I18n.tr("OSM value", *arrayOfNulls(0))
            2 -> I18n.tr("Result value", *arrayOfNulls(0))
            3 -> I18n.tr("EGRN value", *arrayOfNulls(0))
            else -> null
        }
    }

    fun getBackgroundColorAt(rowIndex: Int, columnIndex: Int) : Color? {
        return when (columnIndex) {
            0 -> Color.WHITE
            1 -> LEFT_COLOR
            2 -> getMergeColor(rowIndex)
            3 -> RIGHT_COLOR
            else -> null
        }
    }

    private fun getMergeColor(rowIndex: Int) :Color {
        return when (mergedTags[rowIndex].second) {
            "" -> Color.WHITE
            existingTags[rowIndex].second -> LEFT_COLOR
            conflictTags[rowIndex].second -> RIGHT_COLOR
            else -> Color.RED
        }
    }

    fun takeRight(selectedRow: Int) {
        mergedTags[selectedRow] = conflictTags[selectedRow]
        super.fireTableDataChanged()
    }

    fun takeLeft(selectedRow: Int) {
        mergedTags[selectedRow] = existingTags[selectedRow]
        super.fireTableDataChanged()
    }

    fun takeAllRight() {
        conflictTags.forEachIndexed { index, value -> mergedTags[index] = value }
        super.fireTableDataChanged()
    }

    fun takeAllLeft() {
        existingTags.forEachIndexed { index, value -> mergedTags[index] = value }
        super.fireTableDataChanged()
    }

    fun clearMerged() {
        mergedTags.forEachIndexed { index, _ -> mergedTags[index] = Pair("", "") }
        super.fireTableDataChanged()
    }

    fun isMergeComplete() :Boolean {
        return mergedTags.none { it.second == "" }
    }

}