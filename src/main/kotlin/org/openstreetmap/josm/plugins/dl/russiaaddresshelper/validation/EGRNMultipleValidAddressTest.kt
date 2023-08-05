package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
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
        if (!w.isUsable) return
        val egrnResponse = RussiaAddressHelperPlugin.egrnResponses[w]

        if (egrnResponse != null) {
            val addressInfo = egrnResponse.third
            if (addressInfo.getValidAddresses().size > 1 && !RussiaAddressHelperPlugin.isIgnored(w, EGRNTestCode.EGRN_HAS_MULTIPLE_VALID_ADDRESSES)) {
                RussiaAddressHelperPlugin.markAsProcessed(w, EGRNTestCode.EGRN_HAS_MULTIPLE_VALID_ADDRESSES)
                val prefferedAddress = addressInfo.getPreferredAddress()!!.getOsmAddress().getInlineAddress(",")
                errors.add(
                    TestError.builder(
                        this, Severity.WARNING,
                        EGRNTestCode.EGRN_HAS_MULTIPLE_VALID_ADDRESSES.code
                    )
                        .message(I18n.tr("EGRN multiple addresses") + ": $prefferedAddress ")
                        .primitives(w)
                        .highlight(w)
                        .build()
                )
            }
        }
    }

    override fun fixError(testError: TestError): Command? {
        //для этого валидатора в списке всегда 1 примитив
        val primitive = testError.primitives.iterator().next()
        val affectedAddresses = mutableListOf<ParsedAddress>()

        if (RussiaAddressHelperPlugin.egrnResponses[primitive] != null) {
            val addressInfo = RussiaAddressHelperPlugin.egrnResponses[primitive]?.third
            affectedAddresses.addAll(addressInfo!!.getValidAddresses())


        }
        val corrections = affectedAddresses.map { AddressCorrection(it) }.toMutableList()
        var prefferedIndex = corrections.indexOfFirst { it.address.isBuildingAddress() }
        if (prefferedIndex == -1) {
            prefferedIndex = 0
        }
        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val infoLabel = JMultilineLabel(
            "Для одного здания было получено более одного распознанного адреса." +
                    "<br>Выберите верный (обычно это адрес типа <b><ЗДАНИЕ</b>), или оставьте здание без адреса"
        )
        infoLabel.setMaxWidth(800)

        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val correctionTable = MultipleAddressCorrectionTable(corrections)
        correctionTable.setValueAt(true, prefferedIndex, correctionTable.correctionTableModel.applyColumn)
        correctionTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        val preferredHeight = correctionTable.preferredScrollableViewportSize.height
        correctionTable.preferredScrollableViewportSize = Dimension(800, preferredHeight)
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
        dialog.setButtonIcons("dialogs/edit","dialogs/edit", "cancel")
        dialog.showDialog()

        val answer = dialog.value
        if (answer == 3) {
            return null
        }
        val cmds: MutableList<Command> = mutableListOf()
        if (answer == 1) {
            val selectedCorrectionAddress = correctionTable.correctionTableModel.getSelectedValue().address
            testError.primitives.forEach {
                    val tags = selectedCorrectionAddress.getOsmAddress().getBaseAddressTagsWithSource()
                    cmds.add(ChangePropertyCommand(mutableListOf(it), tags))
                }
            }
        if (answer == 2) {
            testError.primitives.forEach {
                RussiaAddressHelperPlugin.ignoreValidator(
                    it,
                    EGRNTestCode.getByCode(testError.code)!!
                )
            }
        }

        if (cmds.isNotEmpty()) {
            val c: Command =
                SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper MultipleValidAddress validator"), cmds)
            testError.primitives.forEach {
                RussiaAddressHelperPlugin.egrnResponses = RussiaAddressHelperPlugin.egrnResponses.minus(it)
            }

            return c
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