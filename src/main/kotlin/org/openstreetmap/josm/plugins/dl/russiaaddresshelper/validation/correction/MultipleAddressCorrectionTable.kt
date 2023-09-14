package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.correction

import org.openstreetmap.josm.gui.correction.CorrectionTable
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
                if (arrayValue.size > 1) {
                    val newHeight = table.rowHeight * arrayValue.size
                    if (table.getRowHeight(row) != newHeight) {
                        table.setRowHeight(row, newHeight)
                    }
                }
            }

            val model = table.model as AddressCorrectionTableModel
            val f = font
            if (model.isBoldCell(row, column)) {
                font = Font(f.name, f.style or Font.BOLD, f.size)
            } else {
                font = Font(f.name, f.style xor Font.BOLD, f.size)
            }
            return this
        }
    }

    override fun getCellRenderer(row: Int, column: Int): TableCellRenderer? {
        return if (column == 0) {
            multilineTableCellRenderer
        } else {
            super.getCellRenderer(row, column)
        }
    }
}