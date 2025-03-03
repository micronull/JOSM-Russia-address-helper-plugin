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
import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel


class EGRNFuzzyStreetMatchingTest : Test(
    I18n.tr("EGRN fuzzy street match"),
    I18n.tr("EGRN test for parsed street name fuzzy match with OSM")
) {

    private var parsedStreetToPrimitiveMap: Map<String, Pair<Set<OsmPrimitive>, String>> = mutableMapOf()
    private var editedOsmStreetName: String = ""
    private val osmStreetNameEditBox = JosmTextField("")

    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    fun visitForPrimitive(p: OsmPrimitive) {
        if (!p.isUsable) return
        if (parsedStreetToPrimitiveMap.isNotEmpty()) return
        RussiaAddressHelperPlugin.cache.responses.forEach { entry ->
            val primitive = entry.key
            val addressInfo = entry.value.addressInfo
            val addresses = addressInfo?.addresses ?: listOf()
            addresses.forEach {
                if (addressInfo?.getPreferredAddress() == it) {
                    if (it.flags.contains(ParsingFlags.STREET_NAME_FUZZY_MATCH) && !primitive.hasTag("addr:street")
                        && !entry.value.isIgnored(EGRNTestCode.EGRN_STREET_FUZZY_MATCHING)) {
                        val parsedStreetName = it.parsedStreet.extractedType?.name + " " + it.parsedStreet.extractedName
                        val osmObjName = it.parsedStreet.name
                        var affectedPrimitives =
                            parsedStreetToPrimitiveMap.getOrDefault(
                                parsedStreetName,
                                Pair(mutableSetOf(), osmObjName)
                            ).first
                        affectedPrimitives = affectedPrimitives.plus(primitive)
                        affectedPrimitives = affectedPrimitives.plus(it.parsedStreet.matchedPrimitives.toSet())
                        parsedStreetToPrimitiveMap = parsedStreetToPrimitiveMap.plus(
                            Pair(
                                parsedStreetName,
                                Pair(affectedPrimitives, osmObjName)
                            )
                        )
                    }
                }
            }
        }

        parsedStreetToPrimitiveMap.forEach { entry ->
            val errorPrimitives = entry.value.first
            errorPrimitives.forEach{RussiaAddressHelperPlugin.cache.markProcessed(it, EGRNTestCode.EGRN_STREET_FUZZY_MATCHING)}
            val highlightPrimitives: List<OsmPrimitive> = errorPrimitives.mapNotNull { p ->
                GeometryHelper.getBiggestPoly(p)
            }
            errors.add(
                TestError.builder(
                    this, Severity.ERROR,
                    EGRNTestCode.EGRN_STREET_FUZZY_MATCHING.code
                )
                    .message(I18n.tr(EGRNTestCode.EGRN_STREET_FUZZY_MATCHING.message) + ": ${entry.key} " + " -> " + entry.value.second)
                    .primitives(errorPrimitives)
                    .highlight(highlightPrimitives)
                    .build()
            )
        }
    }

    override fun fixError(testError: TestError): Command? {

        val affectedHousenumbers = mutableSetOf<String>()
        val affectedHighways = mutableSetOf<OsmPrimitive>()
        var egrnStreetName = ""
        var osmStreetName = ""
        val affectedAddresses = mutableListOf<ParsedAddress>()
        testError.primitives.forEach {
            if (RussiaAddressHelperPlugin.cache.contains(it)) {
                val addressInfo = RussiaAddressHelperPlugin.cache.get(it)?.addressInfo
                val prefferedAddress = addressInfo?.getPreferredAddress()
                affectedAddresses.add(prefferedAddress!!)
                egrnStreetName =
                    "${prefferedAddress.parsedStreet.extractedType?.name} ${prefferedAddress.parsedStreet.extractedName}"
                osmStreetName = prefferedAddress.parsedStreet.name
                prefferedAddress.parsedHouseNumber.houseNumber.let { it1 -> affectedHousenumbers.add(it1) }
            } else {
                affectedHighways.add(it)
            }
        }

        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        val infoLabel = JMultilineLabel(
            "Несколько (${affectedHousenumbers.size}) зданий (номера ${affectedHousenumbers.joinToString(", ")})<br> получили из ЕГРН адрес с именем улицы:<br> <b>${egrnStreetName}</b>, <br>" +
                    "который был нечетко сопоставлен с именем улицы, существующей в данных ОСМ:<br><b>${osmStreetName}</b> (${affectedHighways.size} линии).<br>" +
                    "Для разрешения ошибки вы можете присвоить зданиям распознанный адрес,<br> ИЛИ <b>(НЕ РЕКОМЕНДУЕТСЯ)</b>" +
                    "<br> переименовать улицу соответственно полученными из ЕГРН данным и " +
                    "<a href =https://wiki.openstreetmap.org/wiki/RU:%D0%A0%D0%BE%D1%81%D1%81%D0%B8%D1%8F/%D0%A1%D0%BE%D0%B3%D0%BB%D0%B0%D1%88%D0%B5%D0%BD%D0%B8%D0%B5_%D0%BE%D0%B1_%D0%B8%D0%BC%D0%B5%D0%BD%D0%BE%D0%B2%D0%B0%D0%BD%D0%B8%D0%B8_%D0%B4%D0%BE%D1%80%D0%BE%D0%B3>правилам именования улиц в ОСМ</a>" +
                    "<br>В случае переименования улицы убедитесь в правильности наименования улицы по другим источникам!" +
                    "<br>Валидными источниками являются постановления местных органов власти о присвоении наименований улицам." +
                    "<br>(Адрес зданий будет исправлен соответственно.)"
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

        osmStreetNameEditBox.text = egrnStreetName
        p.add(JLabel(I18n.tr("Переименовать улицу в:")), GBC.std())
        p.add(osmStreetNameEditBox, GBC.eop().fill(GBC.HORIZONTAL))
        editedOsmStreetName = osmStreetNameEditBox.text

        val buttonTexts = arrayOf(
            I18n.tr("Assign address by street")+": $osmStreetName",
            I18n.tr("Rename street"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление ошибки нечеткого сопоставления"),
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
            val doubled = RussiaAddressHelperPlugin.cleanFromDoubles(filteredPrimitives)
            RussiaAddressHelperPlugin.cache.ignoreValidator(doubled, EGRNTestCode.EGRN_STREET_FUZZY_MATCHING)
            filteredPrimitives.forEach {
                val prefferedAddress = RussiaAddressHelperPlugin.cache.get(it)!!.addressInfo?.getPreferredAddress()
                var tags = prefferedAddress!!.getOsmAddress().getBaseAddressTagsWithSource()
                tags = tags.plus(Pair("addr:RU:egrn", prefferedAddress.egrnAddress))
                cmds.add(ChangePropertyCommand(listOf(it), tags))
            }
        }

        if (answer == 2) {
            //переименование улицы.
            editedOsmStreetName = osmStreetNameEditBox.text
            val highways = testError.primitives.filter { it.hasTag("highway") }
            highways.forEach {
                cmds.add(ChangePropertyCommand(it, "name", editedOsmStreetName))
                cmds.add(ChangePropertyCommand(it, "source:name", "ЕГРН"))
            }

            val filteredPrimitives =
                testError.primitives.filter { RussiaAddressHelperPlugin.cache.contains(it) }.toMutableList()
            val doubled = RussiaAddressHelperPlugin.cleanFromDoubles(filteredPrimitives)
            RussiaAddressHelperPlugin.cache.ignoreValidator(doubled, EGRNTestCode.EGRN_STREET_FUZZY_MATCHING)
            filteredPrimitives.forEach {
                val preferredAddress = RussiaAddressHelperPlugin.cache.get(it)!!.addressInfo?.getPreferredAddress()
                var tags = preferredAddress!!.getOsmAddress().getBaseAddressTagsWithSource()
                tags = tags.plus(Pair("addr:street", editedOsmStreetName))
                tags = tags.plus(Pair("addr:RU:egrn", preferredAddress.egrnAddress))
                cmds.add(ChangePropertyCommand(listOf(it), tags))
            }
        }

        if (cmds.isNotEmpty()) {
            val c: Command = SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper FuzzyMatch validator"), cmds)
            return c
        }

        return null
    }

    override fun endTest() {
        parsedStreetToPrimitiveMap = mutableMapOf()
        super.endTest()
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNFuzzyStreetMatchingTest
    }

}