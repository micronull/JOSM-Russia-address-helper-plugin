package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.osm.*
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JOptionPane
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
                p !is Node && p.hasKey("building") && p.hasKey("addr:housenumber") && (p.hasKey("addr:street") || p.hasKey(
                    "addr:place"
                ))
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
        val assignAllLimit = 5
        val affectedPrimitives = testError.primitives
        val primitive = affectedPrimitives.find { RussiaAddressHelperPlugin.egrnResponses[it] != null }
        if (primitive == null) {
            Logging.warn("EGRN-PLUGIN Trying to fix duplicate error on primitive which already out of plugin cache somehow, exiting")
            return null
        }
        val affectedAddresses = affectedPrimitives.filter { RussiaAddressHelperPlugin.egrnResponses[it] != null }
            .map { RussiaAddressHelperPlugin.egrnResponses[it]?.third?.getPreferredAddress() }
        val duplicateAddress =
            RussiaAddressHelperPlugin.egrnResponses[primitive]!!.third.getPreferredAddress()!!

        val inlineDuplicateAddress = duplicateAddress.getOsmAddress()
            .getInlineAddress(",")

        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        val infoLabel = JMultilineLabel(
            "Несколько (${affectedPrimitives.size}) зданий получили из ЕГРН адрес :<br> <b>${inlineDuplicateAddress}</b>, <br>" +
                    "который совпадает с другими полученными и/или существующими в данных ОСМ адресами." +
                    "<br>Для разрешения ошибки вам доступны следующие варианты:" +
                    "<br><li>Удалить адресные тэги со всех дублей и заново перезапросить адреса для них из ЕГРН" +
                    " (если есть подозрение что дубликат изначально присвоен неверно)" +
                    "<br><li>Присвоить всем элементам (не более $assignAllLimit) одинаковый адрес" +
                    " (подходит для частей многоквартных домов, где точно известно что адрес у всех частей один)" +
                    "<br><li>Перенести адрес на здание наибольшей площади" +
                    " (дефолтный вариант для частной застройки)" +
                    "<br><li>Перенести адрес на здание, ближайшее к линии улицы" +
                    " (экспериментальная опция)" +
                    "<br><li>Так же можно соединить соприкасающиеся дубликаты в один контур" +
                    " с помощью операции объединения (Shift+J), тэги будут так же объединены для всех частей" +
                    "<br><li>Проигнорировать ошибку дубля (больше не будет отображаться в валидации)"
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
            I18n.tr("Remove address from all"),
            I18n.tr("Assign same address to all"),
            I18n.tr("Assign to biggest"),
            I18n.tr("Assign to closest"),
            I18n.tr("Ignore error"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление дублирующихся адресов"),
            *buttonTexts
        )
        dialog.setContent(p, false)
        dialog.setButtonIcons(
            "dialogs/edit",
            "dialogs/edit",
            "dialogs/edit",
            "dialogs/edit",
            "dialogs/edit",
            "dialogs/edit",
            "cancel"
        )
        dialog.showDialog()

        val answer = dialog.value


        val cmds: MutableList<Command> = mutableListOf()
        var msg = "default message"

        if (answer == 1 || answer == 2) {
            //remove address tags for all primitives in error
            cmds.add(removeAddressTagsCommand(affectedPrimitives))

            if (answer == 1) {
                // and re-request addresses from egrn
                val dataSet: DataSet = OsmDataManager.getInstance().editDataSet ?: return null
                dataSet.setSelected(affectedPrimitives)
                RussiaAddressHelperPlugin.selectAction.actionPerformed(ActionEvent(this, 0, ""))
                return null
            }
            msg = "Removed duplicate address tags from all found primitives"
        }

        if (answer == 3) {
            //Assign same address to all
            if (affectedAddresses.size > assignAllLimit) {
                Notification(I18n.tr("Too many affected buildings") + "($assignAllLimit), " + I18n.tr("assign all operation canceled"))
                    .setIcon(JOptionPane.WARNING_MESSAGE).show()
                return null
            }
            cmds.add(
                addAddressToPrimitivesCommand(
                    duplicateAddress.getOsmAddress(),
                    duplicateAddress.egrnAddress,
                    affectedPrimitives
                )
            )
            msg = "Added duplicate address tags to all found primitives"

            affectedPrimitives.forEach {
                if (RussiaAddressHelperPlugin.egrnResponses[it] != null) {
                    RussiaAddressHelperPlugin.ignoreValidator(it, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)
                }
            }
        }

        if (answer == 4) {
            //Assign to biggest
            val biggestBuilding = affectedPrimitives.maxByOrNull { Geometry.computeArea(it) }!!
            val needToRemoveTags = affectedPrimitives.minus(biggestBuilding)
            if (needToRemoveTags.isNotEmpty()) {
                cmds.add(removeAddressTagsCommand(needToRemoveTags))
            }
            cmds.add(
                addAddressToPrimitivesCommand(
                    duplicateAddress.getOsmAddress(),
                    duplicateAddress.egrnAddress,
                    listOf(biggestBuilding)
                )
            )
            msg = "Moved address tags to biggest building"
        }

        if (answer == 5) {
            //assign to closest
            val streets = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives().filter { way ->
                way is Way && way.hasKey("highway") && way.hasTag("name", duplicateAddress.parsedStreet.name)
            }
            val centroids = affectedPrimitives.map { Node(Geometry.getCentroid((it as Way).nodes)) }
            val centroidOfBuildings = Node(Geometry.getCentroid(centroids))
            val highway = Geometry.getClosestPrimitive(centroidOfBuildings, streets)
            val closestBuilding = Geometry.getClosestPrimitive(highway, affectedPrimitives)
            cmds.add(removeAddressTagsCommand(affectedPrimitives.minus(closestBuilding)))
            cmds.add(
                addAddressToPrimitivesCommand(
                    duplicateAddress.getOsmAddress(),
                    duplicateAddress.egrnAddress,
                    listOf(closestBuilding)
                )
            )
            msg = "Moved address tags to building closest to highway"
        }

        if (answer == 6) {
            //ignore error for all primitives
            affectedPrimitives.forEach {
                if (RussiaAddressHelperPlugin.egrnResponses[it] != null) {
                    RussiaAddressHelperPlugin.ignoreValidator(it, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)
                }
            }
            return null
        }

        if (answer == 7) {
            return null
        }

        if (cmds.isNotEmpty()) {
            return SequenceCommand(I18n.tr(msg), cmds)
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

    private fun removeAddressTagsCommand(primitives: Collection<OsmPrimitive>): Command {
        val removeAddressTags = listOf("addr:street", "addr:place", "addr:housenumber", "source:addr")
        return ChangePropertyCommand(primitives, removeAddressTags.associateWith { null })
    }

    private fun addAddressToPrimitivesCommand(
        address: OSMAddress,
        egrnAddress: String,
        primitives: Collection<OsmPrimitive>
    ): Command {
        val addAddressTags = address.getBaseAddressTagsWithSource().toMutableMap()
        addAddressTags.put("addr:RU:egrn", egrnAddress)
        return ChangePropertyCommand(primitives, addAddressTags)
    }

}