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
            0 -> formatAddress(correction.address.egrnAddress, 55)
            1 -> wrapAddress(correction.address.getOsmAddress().getInlineAddress(",") ?: "", 30)
            2 -> wrapAddress(
                correction.address.layer?.description
                    ?: if (correction.address.isBuildingAddress()) I18n.tr("BUILDING") else I18n.tr("PARCEL"),
                14
            )
            else -> null
        }
    }

    public override fun isBoldCell(row: Int, column: Int): Boolean {
        return column != applyColumn && getApply(row)
    }

    fun getSelectedValue(): AddressCorrection {
        return corrections.filterIndexed { index, _ -> super.getApply(index) }.first()
    }

    /**
     * Переносит адрес по словам, чтобы он целиком помещался в узкой колонке.
     * Короткий «хвост» после запятой (номер дома) стараемся не отрывать от улицы.
     */
    private fun wrapAddress(address: String, charLimit: Int): Array<String> {
        if (address.isBlank()) return arrayOf("")
        if (address.length <= charLimit) return arrayOf(address)

        val rawWords = address.split(" ")
        val words = mutableListOf<String>()
        var i = 0
        while (i < rawWords.size) {
            var word = rawWords[i]
            if (word.endsWith(",") && i + 1 < rawWords.size &&
                word.length + 1 + rawWords[i + 1].length <= charLimit
            ) {
                word = "$word ${rawWords[i + 1]}"
                i++
            }
            words.add(word)
            i++
        }

        val result = mutableListOf<String>()
        val line = StringBuilder()
        for (word in words) {
            if (line.isEmpty()) {
                line.append(word)
            } else if (line.length + 1 + word.length <= charLimit) {
                line.append(' ').append(word)
            } else {
                result.add(line.toString())
                line.setLength(0)
                line.append(word)
            }
        }
        if (line.isNotEmpty()) result.add(line.toString())
        return result.toTypedArray()
    }

    /**
     * Разбивает адрес ЕГРН на строки, стараясь резать по запятым.
     * В отличие от прежней версии лимит проверяется до добавления части,
     * поэтому ни одна строка не выходит за ширину колонки и не обрезается «…».
     * Слишком длинная часть дополнительно переносится по словам.
     */
    private fun formatAddress(address: String, charLimit: Int): Array<String> {
        if (address.length <= charLimit) {
            return arrayOf(address)
        }

        val pieces = mutableListOf<String>()
        for (rawPart in address.split(",")) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue
            if (part.length <= charLimit) {
                pieces.add(part)
            } else {
                pieces.addAll(wrapAddress(part, charLimit))
            }
        }

        val result = mutableListOf<String>()
        val line = StringBuilder()
        for (piece in pieces) {
            val candidate = if (line.isEmpty()) piece else "$line, $piece"
            if (line.isEmpty() || candidate.length <= charLimit) {
                line.setLength(0)
                line.append(candidate)
            } else {
                result.add(line.toString())
                line.setLength(0)
                line.append(piece)
            }
        }
        if (line.isNotEmpty()) result.add(line.toString())
        return result.toTypedArray()
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

    fun isColored(row: Int, column: Int): Boolean {
        val correction =  this.corrections[row] as AddressCorrection
        return correction.hasDouble
    }

}