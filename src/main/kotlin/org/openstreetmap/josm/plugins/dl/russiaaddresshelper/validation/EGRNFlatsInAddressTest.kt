package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.AddCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.JPanel


class EGRNFlatsInAddressTest : Test(
    I18n.tr("EGRN address has flats"),
    I18n.tr("EGRN test for addresses with flat numbers in it")
) {

    override fun visit(w: Way) {
        if (!w.isUsable) return
        if (RussiaAddressHelperPlugin.isIgnored(w, EGRNTestCode.EGRN_ADDRESS_HAS_FLATS)) {
           return
        }

        val egrnResponse = RussiaAddressHelperPlugin.egrnResponses[w]

        if (egrnResponse != null) {
            val addressInfo = egrnResponse.third
            val validAddressesWithFlats =
                addressInfo.getValidAddresses().filter { it.flags.contains(ParsingFlags.HOUSENUMBER_HAS_FLATS) }
            if (validAddressesWithFlats.isNotEmpty()) {
                validAddressesWithFlats.forEach {
                    RussiaAddressHelperPlugin.markAsProcessed(w, EGRNTestCode.EGRN_ADDRESS_HAS_FLATS)
                    val inlineAddress = it.getOsmAddress().getInlineAddress(",")
                    errors.add(
                        TestError.builder(
                            this, Severity.WARNING,
                            EGRNTestCode.EGRN_ADDRESS_HAS_FLATS.code
                        )
                            .message(I18n.tr("EGRN address has flats") + ": $inlineAddress")
                            .primitives(w)
                            .highlight(w)
                            .build()
                    )
                }
            }
        }
    }

    override fun fixError(testError: TestError): Command? {
        //для этого валидатора в списке всегда 1 примитив
        val primitive = testError.primitives.iterator().next()
        val affectedAddresses = mutableListOf<ParsedAddress>()
        if (primitive !is Way) {
            return null
        }
        var coordinate: EastNorth = Geometry.getCentroid(primitive.nodes)

        if (RussiaAddressHelperPlugin.egrnResponses[primitive] != null) {
            val addressInfo = RussiaAddressHelperPlugin.egrnResponses[primitive]?.third
            coordinate = RussiaAddressHelperPlugin.egrnResponses[primitive]?.first!!
            affectedAddresses.addAll(
                addressInfo!!.getValidAddresses().filter { it.flags.contains(ParsingFlags.HOUSENUMBER_HAS_FLATS) })

        }
        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val infoLabel = JMultilineLabel(
            "В одном или более распознанном адресе из ЕГРН содержатся номера квартир." +
                    "<br>Можно проигнорировать их или создать адресные точки с номерами." +
                    "<br>(Хинт: рядом могут быть части здания с тем же номером дома, но другими квартирами." +
                    "<br>(Запросите данные для них отдельно или объедините в общий контур здания.)"
        )
        infoLabel.setMaxWidth(800)
        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        var labelText = ""
        affectedAddresses.forEach {
            labelText += "${it.egrnAddress}, кв. номер: ${it.getOsmAddress().flatnumber} (${if (it.isBuildingAddress()) "здание" else "участок"})<br>"
        }
        val egrnAddressesLabel = JMultilineLabel(labelText, false, true)
        p.add(egrnAddressesLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val buttonTexts = arrayOf(
            I18n.tr("Generate address nodes"),
            I18n.tr("Ignore error"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление адресов с номерами квартир"),
            *buttonTexts
        )
        dialog.setContent(p, false)
        dialog.setButtonIcons("dialogs/edit", "dialogs/edit", "cancel")
        dialog.showDialog()

        val answer = dialog.value
        if (answer == 2) {
            RussiaAddressHelperPlugin.ignoreValidator(primitive, EGRNTestCode.getByCode(testError.code)!!)
            return null
        }
        val cmds: MutableList<Command> = mutableListOf()
        if (answer == 1) {
            val ds = MainApplication.getLayerManager().editDataSet
            affectedAddresses.forEachIndexed { index, element ->
                val node = Node(getNodePlacement(coordinate, index))
                element.getOsmAddress().getTags().forEach { node.put(it.key, it.value) }
                cmds.add(AddCommand(ds, node))
            }

        }

        if (cmds.isNotEmpty()) {
            val c: Command =
                SequenceCommand(I18n.tr("Added address nodes from RussiaAddressHelper AddressHasFlats validator"), cmds)
            testError.primitives.forEach {
                RussiaAddressHelperPlugin.egrnResponses.remove(it)
            }

            return c
        }
        return null
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNFlatsInAddressTest
    }


    private fun getNodePlacement(center: EastNorth, index: Int): EastNorth {
        //радиус разброса точек относительно центра в метрах
        val radius = 5
        val angle = 55.0
        if (index == 0) return center
        val startPoint = EastNorth(center.east() - radius, center.north())
        return startPoint.rotate(center, angle * index)
    }

}