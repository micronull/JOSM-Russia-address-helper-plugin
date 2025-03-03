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
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JPanel


class EGRNFuzzyOrInitialsPlaceMatchTest : Test(
    I18n.tr("EGRN fuzzy or initials place match"),
    I18n.tr("EGRN test for parsed place name fuzzy/without initials match with OSM")
) {

    private var parsedPlaceToPrimitiveMap: Map<Pair<String, EGRNTestCode>, Pair<Set<OsmPrimitive>, String>> =
        mutableMapOf()

    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    fun visitForPrimitive(p: OsmPrimitive) {
        if (!p.isUsable) return
        if (parsedPlaceToPrimitiveMap.isNotEmpty()) return
        RussiaAddressHelperPlugin.cache.responses.forEach { entry ->
            val primitive = entry.key
            val addressInfo = entry.value.addressInfo
            val address = addressInfo?.getPreferredAddress()

            if (address != null && !(primitive.hasTag("addr:place") || primitive.hasTag("addr:street"))) {
                val code: EGRNTestCode = if (address.flags.contains(ParsingFlags.PLACE_NAME_FUZZY_MATCH)
                    && !entry.value.isIgnored(EGRNTestCode.EGRN_PLACE_FUZZY_MATCHING)) {
                    EGRNTestCode.EGRN_PLACE_FUZZY_MATCHING
                } else if (address.flags.contains(ParsingFlags.PLACE_NAME_INITIALS_MATCH)
                    && !entry.value.isIgnored(EGRNTestCode.EGRN_PLACE_MATCH_WITHOUT_INITIALS)) {
                    EGRNTestCode.EGRN_PLACE_MATCH_WITHOUT_INITIALS
                } else {
                    return@forEach
                }
                val parsedStreetName = address.parsedPlace.extractedType?.name + " " + address.parsedPlace.extractedName
                val osmObjName = address.parsedPlace.name
                var affectedPrimitives =
                    parsedPlaceToPrimitiveMap.getOrDefault(
                        Pair(parsedStreetName, code),
                        Pair(mutableSetOf(), osmObjName)
                    ).first
                affectedPrimitives = affectedPrimitives.plus(primitive)
                affectedPrimitives = affectedPrimitives.plus(address.parsedStreet.matchedPrimitives.toSet())
                parsedPlaceToPrimitiveMap = parsedPlaceToPrimitiveMap.plus(
                    Pair(
                        Pair(parsedStreetName, code),
                        Pair(affectedPrimitives, osmObjName)
                    )
                )
            }
        }

        parsedPlaceToPrimitiveMap.forEach { entry ->
            val errorPrimitives = entry.value.first
            val errorCode = entry.key.second
            val message: String = if (errorCode == EGRNTestCode.EGRN_PLACE_FUZZY_MATCHING) {
                EGRNTestCode.EGRN_PLACE_FUZZY_MATCHING.message
            } else {
                EGRNTestCode.EGRN_PLACE_MATCH_WITHOUT_INITIALS.message
            }

            RussiaAddressHelperPlugin.cache.markProcessed(errorPrimitives, errorCode)
            val highlightPrimitives: List<OsmPrimitive> = errorPrimitives.mapNotNull { p ->
                GeometryHelper.getBiggestPoly(p)
            }
            errors.add(
                TestError.builder(
                    this, Severity.ERROR,
                    errorCode.code
                )
                    .message(I18n.tr(message) + ": ${entry.key.first} " + " -> " + entry.value.second)
                    .primitives(errorPrimitives)
                    .highlight(highlightPrimitives)
                    .build()
            )
        }
    }

    override fun fixError(testError: TestError): Command? {

        val affectedHousenumbers = mutableSetOf<String>()
        var egrnPlaceName = ""
        var egrnPlaceType = ""
        var osmPlaceName = ""
        val affectedAddresses = mutableListOf<ParsedAddress>()
        testError.primitives.forEach {
            if (RussiaAddressHelperPlugin.cache.responses[it] != null) {
                val addressInfo = RussiaAddressHelperPlugin.cache.responses[it]?.addressInfo
                val prefferedAddress = addressInfo?.getPreferredAddress()
                egrnPlaceName = prefferedAddress!!.parsedPlace.extractedName
                egrnPlaceType = prefferedAddress.parsedPlace.extractedType!!.name
                osmPlaceName = prefferedAddress.parsedPlace.name
                affectedAddresses.add(prefferedAddress)
                prefferedAddress.parsedHouseNumber.houseNumber.let { it1 -> affectedHousenumbers.add(it1) }
            }
        }

        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        val errorCode = EGRNTestCode.getByCode(testError.code)
        val reason = if (errorCode == EGRNTestCode.EGRN_PLACE_FUZZY_MATCHING) {
            "нечетко сопоставлен"
        } else {
            "сопоставлен без учета инициалов"
        }
        val infoLabel = JMultilineLabel(
            "Несколько (${affectedHousenumbers.size}) зданий (номера ${affectedHousenumbers.joinToString(", ")})" +
                    "<br> получили из ЕГРН адрес с именем места:<br> <b>${egrnPlaceName}</b>, ($egrnPlaceType) " +
                    "<br>который был $reason с именем места, существующего в данных ОСМ:<br><b>${osmPlaceName}</b>" +
                    "<br>Для разрешения ошибки вы можете присвоить зданиям распознанный адрес," +
                    "<br> или повторить сопоставление после исправления названия места." +
                    "<br>В случае переименования места убедитесь в правильности наименования места по другим источникам!" +
                    "<br>Валидными источниками являются постановления местных органов власти." +
                    "<br>(Адрес зданий будет исправлен соответственно.)",
            false,
            true
        )
        infoLabel.setMaxWidth(600)

        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        var labelText = "Полученные из ЕГРН адреса: <br>"

        affectedAddresses.forEach {
            labelText += "${it.egrnAddress},<b> тип: ${if (it.isBuildingAddress()) "здание" else "участок"}</b><br>"
        }

        val egrnAddressesLabel = JMultilineLabel(labelText, false, true)
        egrnAddressesLabel.setMaxWidth(800)
        p.add(egrnAddressesLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val buttonTexts = arrayOf(
            I18n.tr("Assign address by place") + ": $osmPlaceName",
            I18n.tr("Retry match with place"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление ошибки - адрес был $reason"),
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
            val filteredPrimitives =
                testError.primitives.filter { RussiaAddressHelperPlugin.cache.contains(it) }.toMutableList()
            val doubles = RussiaAddressHelperPlugin.cleanFromDoubles(filteredPrimitives)
            RussiaAddressHelperPlugin.cache.ignoreValidator(doubles, EGRNTestCode.getByCode(testError.code)!!)
            filteredPrimitives.forEach {
                val prefferedAddress = RussiaAddressHelperPlugin.cache.responses[it]!!.addressInfo!!.getPreferredAddress()
                var tags = prefferedAddress!!.getOsmAddress().getBaseAddressTagsWithSource()
                tags = tags.plus(Pair("addr:RU:egrn", prefferedAddress.egrnAddress))
                cmds.add(ChangePropertyCommand(listOf(it), tags))
            }
        }

        if (answer == 2) {
            val dataSet: DataSet = OsmDataManager.getInstance().editDataSet ?: return null
            dataSet.setSelected(testError.primitives)
            RussiaAddressHelperPlugin.selectAction.actionPerformed(ActionEvent(this, 0, ""))
        }

        if (cmds.isNotEmpty()) {
            val c: Command = SequenceCommand(
                I18n.tr("Added tags from RussiaAddressHelper Place has Fuzzy or Initials Match validator"),
                cmds
            )
            return c
        }

        return null
    }

    override fun endTest() {
        parsedPlaceToPrimitiveMap = mutableMapOf()
        super.endTest()
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNFuzzyOrInitialsPlaceMatchTest
    }

}