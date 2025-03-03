package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.table

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model.BuildingSettingsTableModel
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.table.TableCellRenderer


class BuildingTagsTable(filterSettings: Map<String, List<String>>) :
    JTable(BuildingSettingsTableModel(filterSettings)) {

    private val outside: Border = MatteBorder(1, 0, 1, 0, Color.BLUE)
    private val inside: Border = EmptyBorder(0, 1, 0, 1)
    private val highlight: Border = CompoundBorder(outside, inside)
    override fun prepareRenderer( renderer: TableCellRenderer?, row: Int, column: Int ): Component {
        val c: Component = super.prepareRenderer(renderer, row, column)
        val jc = c as JComponent
        if (isRowSelected(row))
            jc.border = highlight
        return c
    }

    fun getData(): Map<String, List<String>> {
        return (this.model as BuildingSettingsTableModel).getData()
    }

    fun upPressed() {
        val selectedRow = this.selectedRow
        if (selectedRow == -1) return
        if ((this.model as BuildingSettingsTableModel).moveRowUp(selectedRow)) {
            this.changeSelection(selectedRow - 1, this.selectedColumn, false, false)
        }
    }

    fun downPressed() {
        val selectedRow = this.selectedRow
        if (selectedRow == -1) return
        if ((this.model as BuildingSettingsTableModel).moveRowDown(selectedRow)) {
            this.changeSelection(selectedRow + 1, this.selectedColumn, false, false)
        }

    }

    fun removePressed() {
        val selectedRow = this.selectedRow
        if (selectedRow == -1) return
        (this.model as BuildingSettingsTableModel).removeRow(selectedRow)
    }

    fun fillData(filterSettings: Map<String, List<String>>) {
        (this.model as BuildingSettingsTableModel).fillData(filterSettings)
    }
}

