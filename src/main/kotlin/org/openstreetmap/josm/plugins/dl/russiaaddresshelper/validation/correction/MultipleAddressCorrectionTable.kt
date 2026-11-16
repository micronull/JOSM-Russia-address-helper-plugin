package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.correction

import org.openstreetmap.josm.gui.correction.CorrectionTable
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.JList
import javax.swing.JTable
import javax.swing.table.TableCellRenderer


class MultipleAddressCorrectionTable(corrections: MutableList<AddressCorrection>) :
    CorrectionTable<AddressCorrectionTableModel>(AddressCorrectionTableModel(corrections)) {

    private val multilineTableCellRenderer: MultiLineTableCellRenderer = MultiLineTableCellRenderer()

    class MultiLineTableCellRenderer : JList<String?>(), TableCellRenderer {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            //make multi line where the cell value is String[]
            if (value is Array<*>) {
                val arrayValue = value as Array<String?>
                setListData(arrayValue)
            } else {
                val valueAsArray = arrayOf(value.toString())
                setListData(valueAsArray)
            }

            // высота строки — по самой высокой ячейке: у колонок разное число строк,
            // иначе перенос в одной колонке обрежется из-за другой
            var lines = 1
            for (c in 0 until table.columnCount) {
                val cellValue = table.model.getValueAt(row, c)
                if (cellValue is Array<*>) lines = maxOf(lines, cellValue.size)
            }
            val newHeight = table.rowHeight * lines
            if (table.getRowHeight(row) != newHeight) {
                table.setRowHeight(row, newHeight)
            }

            val model = table.model as AddressCorrectionTableModel
            val f = this.font
            this.font = if (model.isBoldCell(row, column)) {
                Font(f.name, Font.BOLD, f.size)
            } else {
                Font(f.name, Font.PLAIN, f.size)
            }
            if (model.isColored(row, column)) {
                this.background = Color.PINK
            } else {
                this.background = Color.WHITE
            }
            return this
        }
    }

    override fun getCellRenderer(row: Int, column: Int): TableCellRenderer? {
        return  if (column < 3) {
         multilineTableCellRenderer
        } else {
            super.getCellRenderer(row, column)
        }
    }
}