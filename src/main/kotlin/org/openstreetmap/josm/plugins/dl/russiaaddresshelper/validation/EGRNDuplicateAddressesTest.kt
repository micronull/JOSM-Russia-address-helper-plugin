package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.coor.EastNorth
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
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
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
        visitPrimitive(w)
        return
    }

    override fun visit(r: Relation) {
        visitPrimitive(r)
        return
    }

    private fun visitPrimitive(w: OsmPrimitive) {
        if (!w.isUsable) return
        //запуск для одного полученного примитива добавляет все ошибки дубликации
        //если переменная не пуста, проход уже был - но если верить отладчику, она всегда пуста
        if (duplicateAddressToPrimitivesMap.isNotEmpty()) return
        //верно ли это? обрабатываем лишь те, что уже помечены как дубликаты при импорте.
        val markedAsDoubles = RussiaAddressHelperPlugin.cache.getProcessed(EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)
            .filter { !RussiaAddressHelperPlugin.cache.isIgnored(it.key, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND) }
            .keys.filter { !it.isDeleted }
        Logging.info("EGRN-PLUGIN Got all marked as doubles validated primitives, size ${markedAsDoubles.size}")
        if (markedAsDoubles.isEmpty()) return
        Logging.info("EGRN-PLUGIN Start form all addressed primitives map")
        val allLoadedPrimitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives()
            .filter { p ->
                p !is Node && p.hasKey("building") && p.hasKey("addr:housenumber") && (p.hasKey("addr:street") || p.hasKey(
                    "addr:place"
                ))
            }.map { p -> Pair(p, GeometryHelper.getPrimitiveCentroid(p)) }
        Logging.info("EGRN-PLUGIN Finish filtering all addressed primitives map, size ${allLoadedPrimitives.size}")
        val existingPrimitivesMap = allLoadedPrimitives.groupBy { getOsmInlineAddress(it.first) }
        Logging.info("EGRN-PLUGIN Finish associating all addressed primitives map, size ${existingPrimitivesMap.size}")

        markedAsDoubles.forEach outer@{ primitive ->
            if (!RussiaAddressHelperPlugin.cache.contains(primitive)) {
                Logging.warn("Doubles check for object not in cache, id={0}", primitive.id)
                return@outer
            }

            if (RussiaAddressHelperPlugin.cache.isIgnored(primitive, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)) {
                return@outer
            }

            val addressInfo = RussiaAddressHelperPlugin.cache.get(primitive)!!.addressInfo
            val coordinate =
                RussiaAddressHelperPlugin.cache.get(primitive)!!.coordinate ?: GeometryHelper.getPrimitiveCentroid(
                    primitive
                )
            val addresses = addressInfo!!.addresses
            addresses.forEach {
                val inlineAddress = it.getOsmAddress().getInlineAddress(",", ignoreFlats = true)
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
                affectedPrimitives = affectedPrimitives.plus(
                    getOsmDoublesWithinSetDistance(
                        it.getOsmAddress(),
                        coordinate,
                        existingPrimitivesMap
                    )
                )
                duplicateAddressToPrimitivesMap =
                    duplicateAddressToPrimitivesMap.plus(Pair(inlineAddress, affectedPrimitives))
            }
        }
        Logging.info("EGRN-PLUGIN Finish creating duplicated addressToPrimitivesMap, size ${duplicateAddressToPrimitivesMap.size}")
        duplicateAddressToPrimitivesMap.forEach { entry ->
            val errorPrimitives = entry.value
            val highlightPrimitives: List<OsmPrimitive> = errorPrimitives.mapNotNull { p -> GeometryHelper.getBiggestPoly(p) }
            errors.add(
                TestError.builder(
                    this, Severity.WARNING,
                    EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND.code
                )
                    .message(I18n.tr(EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND.message) + ": ${entry.key}")
                    .primitives(errorPrimitives)
                    .highlight(highlightPrimitives)
                    .build()
            )
        }
        Logging.info("EGRN-PLUGIN Finish error adding")
        return
    }

    override fun fixError(testError: TestError): Command? {
        val assignAllLimit = 5
        //примитивы содержат и новые и уже существующие в ОСМ
        val affectedPrimitives = testError.primitives

        val primitive = affectedPrimitives.find { RussiaAddressHelperPlugin.cache.contains(it) }
        if (primitive == null) {
            Logging.warn("EGRN-PLUGIN Trying to fix duplicate error on primitives, none of it in plugin cache somehow, exiting")
            return null
        }
        val affectedAddresses =
            affectedPrimitives.filter { RussiaAddressHelperPlugin.cache.contains(it) }
                .map { RussiaAddressHelperPlugin.cache.get(it)?.addressInfo?.getPreferredAddress() }
        val duplicateAddress = affectedAddresses.first()!!

        val inlineDuplicateAddress = duplicateAddress.getOsmAddress()
            .getInlineAddress(",", true)

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
                // TODO find a way to correctly re-request data
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

            RussiaAddressHelperPlugin.cache.ignoreValidator(affectedPrimitives, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)
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
            RussiaAddressHelperPlugin.cache.ignoreValidator(affectedPrimitives, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)
        }

        if (answer == 5) {
            //assign to closest
            val streets = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives().filter { way ->
                way is Way && way.hasKey("highway") && way.hasTag("name", duplicateAddress.parsedStreet.name)
            }
            if (streets.isEmpty()) {
                Notification(
                    I18n.tr("Somehow cannot find street lines with name=") + "$duplicateAddress.parsedStreet.name ," + I18n.tr(
                        "operation canceled"
                    )
                )
                    .setIcon(JOptionPane.WARNING_MESSAGE).show()
                return null
            }
            //сначала ищем центры всех затронутых примитивов
            val centroids: List<Node> = affectedPrimitives.map {
                Node(GeometryHelper.getPrimitiveCentroid(it))}
            //потом ищем центр сгустка примитивов
            val centroidOfBuildings = Node(Geometry.getCentroid(centroids))
            //находим ближайшую к сгустку улицу
            val highway = Geometry.getClosestPrimitive(centroidOfBuildings, streets)
            //ищем среди примитивов ближайший к ближайшей улице
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
            RussiaAddressHelperPlugin.cache.ignoreValidator(affectedPrimitives, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)
        }

        if (answer == 6) {
            //ignore error for all primitives
            RussiaAddressHelperPlugin.cache.ignoreValidator(affectedPrimitives, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)
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

    private fun getOsmDoublesWithinSetDistance(
        address: OSMAddress,
        coordinate: EastNorth,
        existingAddressesMap: Map<String, List<Pair<OsmPrimitive, EastNorth>>>
    ): List<OsmPrimitive> {
        val inlineAddress = address.getInlineAddress(",", ignoreFlats = true)!!
        return existingAddressesMap.getOrDefault(inlineAddress, listOf())
            .filter { CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.get() > it.second.distance(coordinate) }
            .map { it.first }
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
        addAddressTags["addr:RU:egrn"] = egrnAddress
        return ChangePropertyCommand(primitives, addAddressTags)
    }

}