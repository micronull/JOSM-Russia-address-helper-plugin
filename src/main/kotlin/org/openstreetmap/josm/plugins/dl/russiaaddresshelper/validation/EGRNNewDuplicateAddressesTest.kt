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
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ValidationSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper.Companion.splitLongValue
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JOptionPane
import javax.swing.JPanel


class EGRNNewDuplicateAddressesTest : Test(
    I18n.tr("EGRN duplicate addresses new"),
    I18n.tr("EGRN test for duplicate addresses received from registry")
) {

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
        if (!ValidationSettingsReader.ENABLE_NEW_DOUBLES_CHECK.get()) return

        if (!RussiaAddressHelperPlugin.cache.contains(w) || RussiaAddressHelperPlugin.cache.isIgnored(
                w,
                EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND
            )
        ) return

        val doubledAddress = RussiaAddressHelperPlugin.cache.get(w)?.addressInfo?.getPreferredAddress()?.getOsmAddress() ?: return

        val markedAsDoubles =
            RussiaAddressHelperPlugin.addressRegistry.getDoubles(w, doubledAddress.getBaseAddressTags())

        if (markedAsDoubles.isEmpty()) return
        val affectedPrimitives = markedAsDoubles.plus(w)
        //тут будут исключены из подсветки точечные адреса. А надо ли нам это делать?
        val highlightPrimitives: List<OsmPrimitive> =
            affectedPrimitives.map { p -> GeometryHelper.getOuterWays(p) }.flatten()
        errors.add(
            TestError.builder(
                this, Severity.WARNING,
                EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND.code
            )
                .message(
                    I18n.tr(EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND.message) +
                            ": ${doubledAddress.getInlineAddress(", ", ignoreFlats = true)}"
                )
                .primitives(affectedPrimitives)
                .highlight(highlightPrimitives)
                .build()
        )
    }

    override fun fixError(testError: TestError): Command? {
        val assignAllLimit = 5
        //примитивы содержат и новые и уже существующие в ОСМ
        val affectedPrimitives = testError.primitives.toSet()

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
            RussiaAddressHelperPlugin.cache.markProcessed(affectedPrimitives, EGRNTestCode.EGRN_VALID_ADDRESS_ADDED)
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
            RussiaAddressHelperPlugin.cache.markProcessed(affectedPrimitives, EGRNTestCode.EGRN_VALID_ADDRESS_ADDED)
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
            RussiaAddressHelperPlugin.cache.markProcessed(affectedPrimitives, EGRNTestCode.EGRN_VALID_ADDRESS_ADDED)
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
        super.endTest()
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNNewDuplicateAddressesTest
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
        addAddressTags.plusAssign(splitLongValue("addr:RU:egrn", egrnAddress))
        return ChangePropertyCommand(primitives, addAddressTags)
    }

}