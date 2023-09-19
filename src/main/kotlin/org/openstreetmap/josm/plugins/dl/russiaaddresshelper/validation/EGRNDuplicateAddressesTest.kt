package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.data.osm.*
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JPanel


class EGRNDuplicateAddressesTest : Test(
    I18n.tr("EGRN duplicate addresses"),
    I18n.tr("EGRN test for duplicate addresses received from registry")
) {

    private var duplicateAddressToPrimitivesMap: Map<String, Set<OsmPrimitive>> = mutableMapOf()

    override fun visit(w: Way) {
        if (!w.isUsable) return
        if (duplicateAddressToPrimitivesMap.isNotEmpty()) return
        val markedAsDoubles =
            RussiaAddressHelperPlugin.processedByValidators.filter { it.value.contains(EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND) }
                .map { it.key }
        Logging.info("EGRN-PLUGIN Got all marked as doubles validated primitives, size ${markedAsDoubles.size}")
        if (markedAsDoubles.isEmpty()) return
        Logging.info("EGRN-PLUGIN Start form all addressed primitives map")
        val allLoadedPrimitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives()
            .filter { p ->
               p !is Node && p.hasKey("building") && p.hasKey("addr:housenumber") && (p.hasKey("addr:street") || p.hasKey("addr:place"))
            }
        Logging.info("EGRN-PLUGIN Finish filtering all addressed primitives map, size ${allLoadedPrimitives.size}")
        val existingPrimitivesMap = allLoadedPrimitives.associateBy({ getOsmInlineAddress(it) }, { setOf(it) })
        Logging.info("EGRN-PLUGIN Finish associating all addressed primitives map, size ${existingPrimitivesMap.size}")

        markedAsDoubles.forEach { primitive ->
            if (RussiaAddressHelperPlugin.egrnResponses[primitive] == null) {
                Logging.warn("Doubles check for object not in cache, id={0}", primitive.id)
                return@forEach
            }
            val addressInfo = RussiaAddressHelperPlugin.egrnResponses[primitive]!!.third
            val addresses = addressInfo.addresses
            addresses.forEach {
                val inlineAddress = it.getOsmAddress().getInlineAddress(",")
                if (inlineAddress == null) {
                    Logging.warn("Doubles check for object without address id={0}", primitive.id)
                    return@forEach
                }
                var affectedPrimitives =
                    duplicateAddressToPrimitivesMap.getOrDefault(
                        inlineAddress,
                        mutableSetOf()
                    )
                affectedPrimitives = affectedPrimitives.plus(primitive)
                affectedPrimitives = affectedPrimitives.plus(getOsmDoubles(it.getOsmAddress(), existingPrimitivesMap))
                duplicateAddressToPrimitivesMap =
                    duplicateAddressToPrimitivesMap.plus(Pair(inlineAddress, affectedPrimitives))
            }
        }
        Logging.info("EGRN-PLUGIN Finish creating duplicated addressToPrimitivesMap, size ${duplicateAddressToPrimitivesMap.size}")
        duplicateAddressToPrimitivesMap.forEach { entry ->
            val errorPrimitives = entry.value
            errors.add(
                TestError.builder(
                    this, Severity.WARNING,
                    EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND.code
                )
                    .message(I18n.tr("EGRN double address") + ": ${entry.key}")
                    .primitives(errorPrimitives)
                    .highlight(errorPrimitives)
                    .build()
            )
        }
        Logging.info("EGRN-PLUGIN Finish error adding")
    }

    override fun fixError(testError: TestError): Command? {

        val affectedPrimitives = testError.primitives
        val primitive = affectedPrimitives.find { RussiaAddressHelperPlugin.egrnResponses[it] != null }
        if (primitive == null) {
            Logging.warn("EGRN-PLUGIN Trying to fix duplicate error on primitive which already out of plugin cache somehow, exiting")
            return null
        }
        val affectedAddresses = affectedPrimitives.filter { RussiaAddressHelperPlugin.egrnResponses[it] != null }.map { RussiaAddressHelperPlugin.egrnResponses[it]?.third?.getPreferredAddress() }
        val duplicateAddress =
            RussiaAddressHelperPlugin.egrnResponses[primitive]!!.third.getPreferredAddress()!!.getOsmAddress()
                .getInlineAddress(",")

        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        val infoLabel = JMultilineLabel(
            "Несколько (${affectedPrimitives.size}) зданий получили из ЕГРН адрес :<br> <b>${duplicateAddress}</b>, <br>" +
                    "который совпадает с другими полученными и/или существующими в данных ОСМ адресами." +
                    "<br>Функционал авторазрешения дубликатов в разработке, сейчас для разрешения ошибки" +
                    "<br>вы можете вручную присвоить распознанный адрес или " +
                    "<br>удалить адресные тэги с одного из дубликатов и перезапросить адреса из ЕГРН"
        )
        infoLabel.setMaxWidth(600)

        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        var labelText = "Полученные из ЕГРН адреса: <br>"
        affectedAddresses.forEach {
            labelText += "${it?.egrnAddress},<b> тип: ${if (it?.isBuildingAddress() == true) "здание" else "участок"}</b><br>"
        }
        val egrnAddressesLabel = JMultilineLabel(labelText, false, true)
        egrnAddressesLabel.setMaxWidth(800)
        p.add(egrnAddressesLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val buttonTexts = arrayOf(
            I18n.tr("Remove address and request again"),
            I18n.tr("Assign same address to all duplicates"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление дублирующихся адресов"),
            *buttonTexts
        )
        dialog.setContent(p, false)
        dialog.setButtonIcons("dialogs/edit","dialogs/edit", "cancel")
        dialog.showDialog()

        val answer = dialog.value


        val cmds: MutableList<Command> = mutableListOf()
        if (answer == 1) {
            //удалить адресные тэги со всех примитивов в ошибке
            //отправить команду на выполнение
            val dataSet: DataSet = OsmDataManager.getInstance().editDataSet ?: return null
            dataSet.setSelected(testError.primitives)
            RussiaAddressHelperPlugin.selectAction.actionPerformed(ActionEvent(this, 0, ""))
            return null
        }

        if (answer == 2) {
            //взять все примитивы в ошибке и присвоить им одинаковые адресные тэги
            return null
        }

        if (answer == 3) {
            return null
        }

        return null
    }

    override fun endTest() {
        duplicateAddressToPrimitivesMap = mutableMapOf()
        super.endTest()
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNDuplicateAddressesTest
    }

    private fun getOsmDoubles(
        address: OSMAddress,
        existingAddressesMap: Map<String, Set<OsmPrimitive>>
    ): Set<OsmPrimitive> {
        return existingAddressesMap.getOrDefault(address.getInlineAddress(",")!!, setOf())
    }

    private fun getOsmInlineAddress(p: OsmPrimitive): String {
        return if (p.hasKey("addr:street")) {
            "${p["addr:street"]}, ${p["addr:housenumber"]}"
        } else {
            "${p["addr:place"]}, ${p["addr:housenumber"]}"
        }
    }
}