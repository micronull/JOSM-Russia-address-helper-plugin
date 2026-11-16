package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.gui

import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.table.TableCellRenderer


class TagConflictsTable(existingTags: Map<String, String>, conflictTags: Map<String, String>) :
    JTable(TagConflictsTableModel(existingTags, conflictTags)) {

    private val outside: Border = MatteBorder(1, 0, 1, 0, Color.BLUE)
    private val inside: Border = EmptyBorder(0, 1, 0, 1)
    private val highlight: Border = CompoundBorder(outside, inside)
    private var buttonToManage: JButton? = null

    private val wrappingRenderer = WrappingCellRenderer()
    private val measurer = WrappingCellRenderer()

    /**
     * Рендерер с переносом по словам: значения тегов (особенно addr:RU:egrn)
     * длиннее колонки и раньше обрезались.
     */
    private class WrappingCellRenderer : JTextArea(), TableCellRenderer {
        init {
            lineWrap = true
            wrapStyleWord = true
            isOpaque = true
            border = BorderFactory.createEmptyBorder(1, 2, 1, 2)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            font = table.font
            text = value?.toString() ?: ""
            background = (table.model as TagConflictsTableModel).getBackgroundColorAt(row, column)
                ?: table.background
            foreground = table.foreground
            return this
        }
    }

    override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
        return wrappingRenderer
    }

    override fun prepareRenderer(renderer: TableCellRenderer?, row: Int, column: Int): Component {
        val c: Component = super.prepareRenderer(renderer, row, column)
        val jc = c as JComponent
        jc.background = (this.model as TagConflictsTableModel).getBackgroundColorAt(row, column)
        if (isRowSelected(row))
            jc.border = highlight
        adjustRowHeight(row)
        return c
    }

    /**
     * Высота строки — по самой высокой ячейке после переноса.
     * Меряем отдельным JTextArea, чтобы не сбить настройки рендерера текущей ячейки.
     */
    private fun adjustRowHeight(row: Int) {
        measurer.font = font
        var needed = rowHeight
        for (col in 0 until columnCount) {
            val width = columnModel.getColumn(col).width
            measurer.size = Dimension(maxOf(width - 6, 10), Short.MAX_VALUE.toInt())
            measurer.text = model.getValueAt(row, col)?.toString() ?: ""
            needed = maxOf(needed, measurer.preferredSize.height)
        }
        if (getRowHeight(row) != needed) {
            setRowHeight(row, needed)
        }
    }

    fun getData(): Map<String, String> {
        return (this.model as TagConflictsTableModel).getData()
    }

    fun takeLeft() {
        val selectedRow = this.selectedRow
        if (selectedRow == -1) return
        (this.model as TagConflictsTableModel).takeLeft(selectedRow)
        if (selectedRow + 1 < model.rowCount) {
            this.changeSelection(selectedRow + 1, 0, false, false)
        } else {
            this.changeSelection(selectedRow, 0, false, false)
        }
        mergeCompleteCheck()
    }

    fun takeAllLeft() {
        (this.model as TagConflictsTableModel).takeAllLeft()
        mergeCompleteCheck()
    }

    fun takeRight() {
        val selectedRow = this.selectedRow
        if (selectedRow == -1) return
        (this.model as TagConflictsTableModel).takeRight(selectedRow)
        if (selectedRow + 1 < model.rowCount) {
            this.changeSelection(selectedRow + 1, 0, false, false)
        } else {
            this.changeSelection(selectedRow, 0, false, false)
        }
        mergeCompleteCheck()
    }

    fun takeAllRight() {
        (this.model as TagConflictsTableModel).takeAllRight()
        mergeCompleteCheck()
    }

    fun clearMerged() {
        (this.model as TagConflictsTableModel).clearMerged()
        mergeCompleteCheck()
    }

    fun setButton(button: JButton) {
        buttonToManage = button
    }

    fun mergeCompleteCheck() {
        if (buttonToManage != null) {
            buttonToManage!!.isEnabled = (this.model as TagConflictsTableModel).isMergeComplete()
        }
    }
}

