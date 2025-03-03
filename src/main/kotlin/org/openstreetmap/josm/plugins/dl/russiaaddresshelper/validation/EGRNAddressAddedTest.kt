package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.apache.commons.lang3.StringUtils
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
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel


class EGRNAddressAddedTest : Test(
    I18n.tr("EGRN address added to OSM"),
    I18n.tr("EGRN information for address parsed and added")
) {
    private val osmStreetNameEditBox = JosmTextField("")
    private val osmPlaceNameEditBox = JosmTextField("")
    private val osmNumberEditBox = JosmTextField("")

    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    private fun visitForPrimitive(p: OsmPrimitive) {
        if (!p.isUsable) return
        val egrnResult = RussiaAddressHelperPlugin.cache.get(p)
        if (egrnResult != null && egrnResult.addressInfo?.getPreferredAddress() != null) {
            val preferredAddress = egrnResult.addressInfo.getPreferredAddress()!!
            if (preferredAddress.getOsmAddress().getBaseAddressTagsWithSource().all { p.hasTag(it.key, it.value) }) {
                RussiaAddressHelperPlugin.cache.markProcessed(p, EGRNTestCode.EGRN_VALID_ADDRESS_ADDED)
                errors.add(
                    TestError.builder(
                        this, Severity.WARNING,
                        EGRNTestCode.EGRN_VALID_ADDRESS_ADDED.code
                    )
                        .message(I18n.tr( EGRNTestCode.EGRN_VALID_ADDRESS_ADDED.message), preferredAddress.egrnAddress)
                        .primitives(p)
                        .build()
                )
            }
        }
    }

    override fun fixError(testError: TestError): Command? {
        val primitive = testError.primitives.iterator().next()

        val preferredAddress = RussiaAddressHelperPlugin.cache.get(primitive)?.addressInfo!!.getPreferredAddress()!!

        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        val infoLabel = JMultilineLabel(
            "Запрос в ЕГРН вернул адрес" +
                    "<br>${preferredAddress.egrnAddress}, <br><b> тип: ${if (preferredAddress.isBuildingAddress()) "здание" else "участок"}</b>" +
                    "<br> который был распознан и добавлен в ОСМ.<br>" +
                    "Если адрес был распознан некорректно, вы можете попытаться разобрать адрес вручную, или удалить некорректные тэги.<br>" +
                    "<b>Не вносите в ОСМ данные основанные на интерполяции!" +
                    "<br>Не вносите в ОСМ данные, взятые из неразрешенных источников " +
                    "<br>(другие карты, панорамы, сайты, которые ЯВНО не дали разрешение на использование)</b>" +
                    "<br><br>Распознанный адрес:",
        false,
        true)
        infoLabel.setMaxWidth(800)
        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        osmStreetNameEditBox.text = ""
        osmPlaceNameEditBox.text = ""
        osmNumberEditBox.text = ""

        if (StringUtils.isNotBlank(preferredAddress.parsedStreet.name)) {
            osmStreetNameEditBox.text = preferredAddress.parsedStreet.name
        }
        if (StringUtils.isNotBlank(preferredAddress.parsedPlace.name)) {
            osmPlaceNameEditBox.text = preferredAddress.parsedPlace.name
        }
        if (StringUtils.isNotBlank(preferredAddress.parsedHouseNumber.houseNumber)) {
            osmNumberEditBox.text = preferredAddress.parsedHouseNumber.houseNumber
        }

        p.add(JLabel("addr:street"), GBC.std())
        p.add(osmStreetNameEditBox, GBC.eop().fill(GBC.HORIZONTAL))
        p.add(JLabel("addr:place"), GBC.std())
        p.add(osmPlaceNameEditBox, GBC.eop().fill(GBC.HORIZONTAL))
        p.add(JLabel("addr:housenumber"), GBC.std())
        p.add(osmNumberEditBox, GBC.eop().fill(GBC.HORIZONTAL))

        val buttonTexts = arrayOf(
            I18n.tr("Редактировать адрес"),
            I18n.tr("Удалить адресные поля"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Валидация распознанного адреса"),
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
            val streetName = osmStreetNameEditBox.text
            val placeName = osmPlaceNameEditBox.text
            val number = osmNumberEditBox.text

            if (StringUtils.isNotBlank(number) && StringUtils.isNotBlank(streetName) || StringUtils.isNotBlank(placeName)) {
                val tags: MutableMap<String, String> = mutableMapOf(
                    "addr:housenumber" to number,
                    "source:addr" to "ЕГРН",
                    "note" to "адрес из ЕГРН разобран вручную",
                    "addr:RU:egrn" to preferredAddress.egrnAddress
                )
                if (StringUtils.isNotBlank(streetName)) {
                    tags["addr:street"] = streetName
                } else {
                    tags["addr:place"] = placeName
                }
                cmds.add(ChangePropertyCommand(listOf(primitive), tags))
                RussiaAddressHelperPlugin.cache.ignoreValidator(primitive, EGRNTestCode.getByCode(testError.code)!!)
            } else {
                Notification(I18n.tr("Address not complete and was not added to building")).setIcon(JOptionPane.WARNING_MESSAGE)
                    .show()
                return null
            }
        }

        if (answer == 2) {
            val tagsToRemove = mutableMapOf<String, String?>("addr:place" to null,"addr:street" to null,
                "addr:housenumber" to null ,"source:addr" to null, "addr:RU:egrn" to null)
            cmds.add(ChangePropertyCommand(listOf(primitive), tagsToRemove))
            RussiaAddressHelperPlugin.cache.ignoreAllValidators(primitive)
        }

        if (cmds.isNotEmpty()) {
            return SequenceCommand(I18n.tr("Modified tags from RussiaAddressHelper AddressAdded validator"), cmds)
        }

        return null
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNAddressAddedTest
    }

}