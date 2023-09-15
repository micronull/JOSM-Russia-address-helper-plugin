package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel


class EGRNCantParseAddressTest : Test(
    I18n.tr("EGRN address cannot be parsed"),
    I18n.tr("EGRN information cannot be fully parsed into address")
) {
    private val osmStreetNameEditBox = JosmTextField("")
    private val osmPlaceNameEditBox = JosmTextField("")
    private val osmNumberEditBox = JosmTextField("")

    override fun visit(w: Way) {
        if (!w.isUsable) return
        val egrnResult = RussiaAddressHelperPlugin.egrnResponses[w]

        if (egrnResult != null && egrnResult.third.getNonValidAddresses().isNotEmpty()) {
            val addressInfo = egrnResult.third
            val severity = if (addressInfo.getValidAddresses().isNotEmpty()) Severity.OTHER else Severity.WARNING

            addressInfo.getNonValidAddresses().forEach {
                val flags = it.flags
                var code: EGRNTestCode? = null
                var message = "EGRN parse error"
                var message2 = it.egrnAddress
                var finalSeverity = severity
                if (flags.contains(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED_BUT_CONTAINS_NUMBERS)) {
                    code = EGRNTestCode.EGRN_NOT_PARSED_HOUSENUMBER
                    message = I18n.tr("EGRN cant get housenumber")
                    finalSeverity = Severity.WARNING
                } else
                    if (flags.contains(ParsingFlags.HOUSENUMBER_TOO_BIG)) {
                        code = EGRNTestCode.EGRN_NOT_PARSED_HOUSENUMBER
                        message = "EGRN housenumber too big"
                    } else
                        if (flags.contains(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED)) {
                            code = EGRNTestCode.EGRN_NOT_PARSED_HOUSENUMBER
                            message = "EGRN cant get housenumber"
                        } else
                            if (flags.contains(ParsingFlags.CANNOT_FIND_PLACE_TYPE)
                                    .and(flags.contains(ParsingFlags.CANNOT_EXTRACT_STREET_NAME))
                            ) {
                                code = EGRNTestCode.EGRN_NOT_PARSED_STREET_AND_PLACE
                                message = "EGRN cant get street or place name"
                            }

                if (code != null && !RussiaAddressHelperPlugin.isIgnored(w, code)) {
                    RussiaAddressHelperPlugin.markAsProcessed(w, code)
                    errors.add(
                        TestError.builder(this, finalSeverity, code.code)
                            .message(I18n.tr(message), message2)
                            .primitives(w)
                            .build()
                    )
                }
            }
        }
    }


    override fun fixError(testError: TestError): Command? {
        val primitive = testError.primitives.iterator().next()
        val affectedAddresses = mutableListOf<ParsedAddress>()

        if (RussiaAddressHelperPlugin.egrnResponses[primitive] != null) {
            val addressInfo = RussiaAddressHelperPlugin.egrnResponses[primitive]?.third
            affectedAddresses.addAll(addressInfo!!.getNonValidAddresses())
        }

        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        val infoLabel = JMultilineLabel(
            "Запрос в ЕГРН вернул адрес, который не удалось корректно распознать.<br>" +
                    "Вы можете попытаться разобрать адрес вручную, или проигнорировать нераспознанные данные.<br>" +
                    "<b>Не вносите в ОСМ данные основанные на интерполяции!" +
                    "<br>Не вносите в ОСМ данные, взятые из неразрешенных источников " +
                    "<br>(другие карты, панорамы, сайты, которые ЯВНО не дали разрешение на использование)</b>"
        )
        infoLabel.setMaxWidth(800)
        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        osmStreetNameEditBox.text = ""
        osmPlaceNameEditBox.text = ""
        osmNumberEditBox.text = ""
        var labelText = ""
        affectedAddresses.forEach {
            labelText += "${it.egrnAddress},<b> тип: ${if (it.isBuildingAddress()) "здание" else "участок"}</b><br>"
            if (StringUtils.isNotBlank(it.parsedStreet.name)) {
                osmStreetNameEditBox.text = it.parsedStreet.name
            }
            if (StringUtils.isNotBlank(it.parsedPlace.name)) {
                osmPlaceNameEditBox.text = it.parsedPlace.name
            }
            if (StringUtils.isNotBlank(it.parsedHouseNumber.housenumber)) {
                osmNumberEditBox.text = it.parsedHouseNumber.housenumber
            }
        }
        val egrnAddressesLabel = JMultilineLabel(labelText, false, true)
        egrnAddressesLabel.setMaxWidth(800)
        p.add(egrnAddressesLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))


        p.add(JLabel("addr:street"), GBC.std())
        p.add(osmStreetNameEditBox, GBC.eop().fill(GBC.HORIZONTAL))
        p.add(JLabel("addr:place"), GBC.std())
        p.add(osmPlaceNameEditBox, GBC.eop().fill(GBC.HORIZONTAL))
        p.add(JLabel("addr:housenumber"), GBC.std())
        p.add(osmNumberEditBox, GBC.eop().fill(GBC.HORIZONTAL))

        val buttonTexts = arrayOf(
            I18n.tr("Ignore error"),
            I18n.tr("Assign address to building"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление ошибки нераспознанного адреса"),
            *buttonTexts
        )
        dialog.setContent(p, false)
        dialog.setButtonIcons("dialogs/edit", "dialogs/edit", "cancel")
        dialog.showDialog()

        val answer = dialog.value
        if (answer == 1) {
            RussiaAddressHelperPlugin.ignoreValidator(primitive, EGRNTestCode.getByCode(testError.code)!!)
            return null
        }

        if (answer == 3) {
            return null
        }

        val cmds: MutableList<Command> = mutableListOf()
        if (answer == 2) {
            val streetName = osmStreetNameEditBox.text
            val placeName = osmPlaceNameEditBox.text
            val number = osmNumberEditBox.text

            if (StringUtils.isNotBlank(number) && StringUtils.isNotBlank(streetName) || StringUtils.isNotBlank(placeName)) {
                val tags: MutableMap<String, String> = mutableMapOf(
                    "addr:housenumber" to number,
                    "source:addr" to "ЕГРН",
                    "note" to "адрес из ЕГРН разобран вручную"
                )
                if (StringUtils.isNotBlank(streetName)) {
                    tags.put("addr:street", streetName)
                } else {
                    tags.put("addr:place", placeName)
                }
                cmds.add(ChangePropertyCommand(listOf(primitive), tags))

            } else {
                Notification(I18n.tr("Address not complete and was not added to building")).setIcon(JOptionPane.WARNING_MESSAGE)
                    .show()
                return null
            }
        }

        if (cmds.isNotEmpty()) {
            val c: Command =
                SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper CantParseAddress validator"), cmds)
            testError.primitives.forEach {
                RussiaAddressHelperPlugin.egrnResponses.remove(it)
            }

            return c
        }

        return null
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNCantParseAddressTest
    }

}