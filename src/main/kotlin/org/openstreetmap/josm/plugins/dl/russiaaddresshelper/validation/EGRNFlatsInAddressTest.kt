package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.AddCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
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
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.JPanel


class EGRNFlatsInAddressTest : Test(
    I18n.tr("EGRN address has flats"),
    I18n.tr("EGRN test for addresses with flat numbers in it")
) {

    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    private fun visitForPrimitive(p: OsmPrimitive) {
        if (!p.isUsable) return
        if (RussiaAddressHelperPlugin.cache.isIgnored(p, EGRNTestCode.EGRN_ADDRESS_HAS_FLATS)) {
            return
        }

        val egrnResponse = RussiaAddressHelperPlugin.cache.get(p)

        if (egrnResponse != null) {
            val addressInfo = egrnResponse.addressInfo!!
            val validAddressesWithFlats =
                addressInfo.getValidAddresses().filter { it.flags.contains(ParsingFlags.HOUSENUMBER_HAS_FLATS) }
            if (validAddressesWithFlats.isNotEmpty()) {
                RussiaAddressHelperPlugin.cache.markProcessed(p, EGRNTestCode.EGRN_ADDRESS_HAS_FLATS)
                val inlineAddress: String = validAddressesWithFlats.first().getOsmAddress().getInlineAddress(",", true)!!
                val flats : String = validAddressesWithFlats.map { it.getOsmAddress().flatnumber }.sorted().joinToString(", ")
                val highlightPrimitive = GeometryHelper.getBiggestPoly(p)
                errors.add(
                    TestError.builder(
                        this, Severity.WARNING,
                        EGRNTestCode.EGRN_ADDRESS_HAS_FLATS.code
                    )
                        .message(I18n.tr(EGRNTestCode.EGRN_ADDRESS_HAS_FLATS.message) + ": $inlineAddress: $flats")
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

        val createNodeAt: EastNorth
        //TODO: реализовать алгоритм расстановки точек в соответствии с их реальным положением
        //для каждого участка, где есть адрес с номером квартиры, найти пересечение границ участка с контуром здания, и взять точку центра полигона пересечения
        if (RussiaAddressHelperPlugin.cache.contains(primitive)) {
            val addressInfo = RussiaAddressHelperPlugin.cache.get(primitive)?.addressInfo
            createNodeAt =
                RussiaAddressHelperPlugin.cache.get(primitive)?.coordinate ?: GeometryHelper.getPrimitiveCentroid(
                    primitive
                )
            affectedAddresses.addAll(
                addressInfo!!.getValidAddresses().filter { it.flags.contains(ParsingFlags.HOUSENUMBER_HAS_FLATS) })

        } else {
            return null
        }
        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val infoLabel = JMultilineLabel(
            "В одном или более распознанном адресе из ЕГРН содержатся номера квартир." +
                    "<br>Можно проигнорировать их или создать адресные точки с номерами." +
                    "<br>Внимание: положение создаваемых адресных точек не связано с реальным положением квартир!" +
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

        val cmds: MutableList<Command> = mutableListOf()
        if (answer == 1) {
            val ds = MainApplication.getLayerManager().editDataSet
            affectedAddresses.forEachIndexed { index, element ->
                val node = Node(getNodePlacement(createNodeAt, index))
                node.putAll(element.getOsmAddress().getTags())
                node.put("addr:RU:egrn", element.egrnAddress)
                cmds.add(AddCommand(ds, node))
            }

        }

        if (answer == 2) {
            RussiaAddressHelperPlugin.cache.ignoreValidator(primitive, EGRNTestCode.getByCode(testError.code)!!)
            return null
        }

        if (cmds.isNotEmpty()) {
            val c: Command =
                SequenceCommand(I18n.tr("Added address nodes from RussiaAddressHelper AddressHasFlats validator"), cmds)
            RussiaAddressHelperPlugin.cache.ignoreValidator(primitive, EGRNTestCode.getByCode(testError.code)!!)

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