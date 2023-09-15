package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.osm.OsmPrimitive
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
        if (!w.isUsable) return
        if (parsedStreetToPrimitiveMap.isNotEmpty()) return
        RussiaAddressHelperPlugin.egrnResponses.forEach { entry ->
            val primitive = entry.key
            val addressInfo = entry.value.third
            val addresses = addressInfo.addresses
            addresses.forEach {
                if (addressInfo.getPreferredAddress() == it) {
                    if (it.flags.contains(ParsingFlags.STREET_NAME_FUZZY_MATCH)) {
                        val parsedStreetName = it.parsedStreet.extractedType + " " + it.parsedStreet.extractedName
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
            errorPrimitives.forEach{RussiaAddressHelperPlugin.markAsProcessed(it, EGRNTestCode.EGRN_STREET_FUZZY_MATCHING)}
            errors.add(
                TestError.builder(
                    this, Severity.ERROR,
                    EGRNTestCode.EGRN_STREET_FUZZY_MATCHING.code
                )
                    .message(I18n.tr("EGRN fuzzy match") + ": ${entry.key} " + " -> " + entry.value.second)
                    .primitives(errorPrimitives)
                    .highlight(errorPrimitives)
                    .build()
            )
        }
    }

    override fun fixError(testError: TestError): Command? {

        val affectedHousenumbers = mutableSetOf<String>()
        val affectedHighways = mutableSetOf<OsmPrimitive>()
        var egrnStreetName = ""
        var osmStreetName = ""
        testError.primitives.forEach {
            if (RussiaAddressHelperPlugin.egrnResponses[it] != null) {
                val addressInfo = RussiaAddressHelperPlugin.egrnResponses[it]?.third
                val prefferedAddress = addressInfo?.getPreferredAddress()
                egrnStreetName =
                    "${prefferedAddress!!.parsedStreet.extractedType} ${prefferedAddress.parsedStreet.extractedName}"
                osmStreetName = prefferedAddress.parsedStreet.name
                prefferedAddress.parsedHouseNumber.housenumber.let { it1 -> affectedHousenumbers.add(it1) }
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
            testError.primitives.forEach {
                val egrnResult = RussiaAddressHelperPlugin.egrnResponses[it]
                if (egrnResult != null) {
                    var tags = egrnResult.third.getPreferredAddress()!!.getOsmAddress().getBaseAddressTagsWithSource()
                    tags = tags.plus(Pair("addr:RU:egrn", egrnResult.third.getPreferredAddress()!!.egrnAddress))
                    cmds.add(ChangePropertyCommand(mutableListOf(it), tags))
                }
            }
        }

        if (answer == 2) {
            editedOsmStreetName = osmStreetNameEditBox.text
            val highways = testError.primitives.filter { it.hasTag("highway") }
            highways.forEach {
                cmds.add(ChangePropertyCommand(it, "name", editedOsmStreetName))
                cmds.add(ChangePropertyCommand(it, "source:name", "ЕГРН"))
            }
            val buildings = testError.primitives.filter { !it.hasTag("highway") }
            buildings.forEach {
                val egrnResult = RussiaAddressHelperPlugin.egrnResponses[it]
                if (egrnResult != null) {
                    var tags = egrnResult.third.getPreferredAddress()!!.getOsmAddress().getBaseAddressTagsWithSource()
                    tags = tags.plus(Pair("addr:street", editedOsmStreetName))
                    cmds.add(ChangePropertyCommand(mutableListOf(it), tags))
                }
            }
        }

        if (cmds.isNotEmpty()) {
            val c: Command = SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper FuzzyMatch validator"), cmds)
            testError.primitives.forEach {
                RussiaAddressHelperPlugin.egrnResponses.remove(it)
            }

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