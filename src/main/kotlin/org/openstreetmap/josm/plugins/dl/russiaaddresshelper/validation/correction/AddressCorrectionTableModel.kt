package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.correction

import org.openstreetmap.josm.gui.correction.CorrectionTableModel
import org.openstreetmap.josm.tools.I18n

class AddressCorrectionTableModel(corrections: List<AddressCorrection>) :
    CorrectionTableModel<AddressCorrection>(corrections) {

    override fun getColumnCount(): Int {
        return 4
    }

    override fun getCorrectionColumnName(colIndex: Int): String? {
        return when (colIndex) {
            0 -> I18n.tr("EGRN address", *arrayOfNulls(0))
            1 -> I18n.tr("Parsed address", *arrayOfNulls(0))
            2 -> I18n.tr("Address type", *arrayOfNulls(0))
            else -> null
        }
    }

    override fun getCorrectionValueAt(rowIndex: Int, colIndex: Int): Any? {
        val correction = this.corrections[rowIndex] as AddressCorrection
        return when (colIndex) {
            0 -> formatAddress(correction.address.egrnAddress, 40)
            1 -> correction.address.getOsmAddress().getInlineAddress(",")
            2 -> if (correction.address.isBuildingAddress()) {
                I18n.tr("BUILDING")
            } else {
                I18n.tr("PARCEL")
            }
            else -> null
        }
    }

    public override fun isBoldCell(row: Int, column: Int): Boolean {
        return column != applyColumn && getApply(row)
    }

    fun getSelectedValue(): AddressCorrection {
        return corrections.filterIndexed { index, _ -> super.getApply(index) }.first()
    }

    private fun formatAddress(address: String, charLimit: Int): Array<String> {
        if (address.length < charLimit) {
            return arrayOf(address)
        }
        var addressParts = address.split(",")
        if (addressParts.size == 1) {
            addressParts = address.split(" ")
        }
        if (addressParts.size == 1) {
            return arrayOf(address)
        }
        var result: Array<String> = arrayOf()
        var accumulator = ""
        for (part in addressParts) {
            accumulator = "$accumulator$part${if (addressParts.last() != part) "," else ""}"
            if (accumulator.length > charLimit) {
                result = result.plus(accumulator)
                accumulator = ""
            }
        }
        if (accumulator.isNotBlank()) result = result.plus(accumulator)
        return result
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == super.getApplyColumn() && aValue is Boolean) {
            for (i in 0 until super.getRowCount()) {
                super.setValueAt(false, i, columnIndex)
            }
            super.setValueAt(true, rowIndex, columnIndex)
            super.fireTableDataChanged()
        }
    }

    fun isFilled(row: Int, column: Int): Boolean {
        val correction =  this.corrections[row] as AddressCorrection
        return correction.hasDouble
    }

}