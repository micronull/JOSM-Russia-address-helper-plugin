package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Relation
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsedAddressInfo
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.correction.AddressCorrection
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.correction.MultipleAddressCorrectionTable
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable


class EGRNMultipleValidAddressTest : Test(
    I18n.tr("EGRN multiple addresses"),
    I18n.tr("EGRN test for multiple different valid addresses")
) {

    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    fun visitForPrimitive(p: OsmPrimitive) {
        if (!p.isUsable) return

        if (RussiaAddressHelperPlugin.cache.contains(p)) {
            val data: ValidationRecord = RussiaAddressHelperPlugin.cache.get(p)!!
            val addressInfo = data.addressInfo ?: ParsedAddressInfo(listOf())
            if (addressInfo.getDistinctValidAddresses(true).size > 1 && !data.isIgnored(EGRNTestCode.EGRN_HAS_MULTIPLE_VALID_ADDRESSES)
                && !p.hasTag("addr:housenumber")
            ) {
                RussiaAddressHelperPlugin.cache.markProcessed(p, EGRNTestCode.EGRN_HAS_MULTIPLE_VALID_ADDRESSES)
                val firstAddress = addressInfo.getPreferredAddress()!!
                val secondAddress = addressInfo.getValidAddresses().first { it != firstAddress }
                val highlightPrimitive = GeometryHelper.getBiggestPoly(p)
                errors.add(
                    TestError.builder(
                        this, Severity.ERROR,
                        EGRNTestCode.EGRN_HAS_MULTIPLE_VALID_ADDRESSES.code
                    )
                        .message(
                            I18n.tr(EGRNTestCode.EGRN_HAS_MULTIPLE_VALID_ADDRESSES.message) + ": ${
                                firstAddress.getOsmAddress().getInlineAddress(",")
                            } / ${secondAddress.getOsmAddress().getInlineAddress(",")}"
                        )
                        .primitives(p)
                        .highlight(highlightPrimitive)
                        .build()
                )
            }
        }
    }

    override fun fixError(testError: TestError): Command? {
        //для этого валидатора в списке всегда 1 примитив
        val primitive = testError.primitives.iterator().next()
        val affectedAddresses = mutableListOf<ParsedAddress>()

        if (RussiaAddressHelperPlugin.cache.contains(primitive)) {
            val addressInfo = RussiaAddressHelperPlugin.cache.get(primitive)?.addressInfo
            affectedAddresses.addAll(addressInfo!!.getValidAddresses())
        }

        val doubledAddresses = RussiaAddressHelperPlugin.findDoubledAddresses(affectedAddresses)
        val corrections = affectedAddresses.map { AddressCorrection(it, doubledAddresses.contains(it)) }.toMutableList()
        var preferredIndex = corrections.indexOfFirst { it.address.isBuildingAddress() }
        if (preferredIndex == -1) {
            preferredIndex = 0
        }
        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val infoLabel = JMultilineLabel(
            "Для одного здания было получено более одного распознанного адреса." +
                    "<br>Выберите верный (обычно это адрес типа <b><ЗДАНИЕ</b>), или оставьте здание без адреса" +
                    "<br>Жирным выделен текущий выбранный для присвоения адрес" +
                    "<br>Цветной заливкой выделены адреса, которые дублируют уже существующие на карте"
        )
        infoLabel.setMaxWidth(800)

        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val correctionTable = MultipleAddressCorrectionTable(corrections)
        correctionTable.tableHeader.reorderingAllowed = false
        correctionTable.rowSelectionAllowed = false
        correctionTable.columnSelectionAllowed = false
        correctionTable.cellSelectionEnabled = false
        correctionTable.setValueAt(true, preferredIndex, correctionTable.correctionTableModel.applyColumn)
        correctionTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        correctionTable.preferredScrollableViewportSize = Dimension(800, 96)
        correctionTable.columnModel.getColumn(0).preferredWidth = 400
        correctionTable.columnModel.getColumn(1).preferredWidth = 220
        correctionTable.columnModel.getColumn(2).preferredWidth = 100
        correctionTable.columnModel.getColumn(3).preferredWidth = 80

        val scrollPane = JScrollPane(correctionTable)
        p.add(scrollPane, GBC.eop().fill(GBC.BOTH))


        val buttonTexts = arrayOf(
            I18n.tr("Assign address to building"),
            I18n.tr("Ignore error"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление нескольких валидных адресов"),
            *buttonTexts
        )
        dialog.setContent(p, false)
        dialog.setButtonIcons("dialogs/edit", "dialogs/edit", "cancel")
        dialog.showDialog()

        val answer = dialog.value
        if (answer == 3) {
            return null
        }
        val cmds: MutableList<Command> = mutableListOf()
        if (answer == 1) {
            val selectedCorrectionAddress = correctionTable.correctionTableModel.getSelectedValue().address
            val hasDouble = correctionTable.correctionTableModel.getSelectedValue().hasDouble
            testError.primitives.forEach {
                var tags = selectedCorrectionAddress.getOsmAddress().getBaseAddressTagsWithSource()
                tags = tags.plus(Pair("addr:RU:egrn", selectedCorrectionAddress.egrnAddress))
                if (hasDouble) RussiaAddressHelperPlugin.cache.markProcessed(
                    primitive,
                    EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND
                )
                cmds.add(ChangePropertyCommand(mutableListOf(it), tags))
            }
        }

        if (answer == 2) {
            RussiaAddressHelperPlugin.cache.ignoreValidator(testError.primitives, EGRNTestCode.getByCode(testError.code)!!)
        }

        if (cmds.isNotEmpty()) {
            return SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper MultipleValidAddress validator"), cmds)
        }
        return null
    }

    override fun endTest() {
        super.endTest()
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNMultipleValidAddressTest
    }

}