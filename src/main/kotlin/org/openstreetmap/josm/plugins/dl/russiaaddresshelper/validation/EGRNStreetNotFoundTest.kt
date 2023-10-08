package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitive
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
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JPanel


class EGRNStreetNotFoundTest : Test(
    I18n.tr("EGRN street not found"),
    I18n.tr("EGRN test for not found OSM street")
) {

    private var parsedStreetToPrimitiveMap: Map<String, Set<OsmPrimitive>> = mutableMapOf()

    override fun visit(w: Way) {
        if (!w.isUsable) return
        if (parsedStreetToPrimitiveMap.isNotEmpty()) return
        //собираем в мапу домики которые не сопоставились, ключ - распознанное имя улицы
        RussiaAddressHelperPlugin.egrnResponses.forEach { entry ->
            val primitive = entry.key
            val addressInfo = entry.value.third
            val addresses = addressInfo.addresses
            addresses.forEach {
                if (it.flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM)
                    && (StringUtils.isNotBlank(it.parsedHouseNumber.housenumber))
                    && (!RussiaAddressHelperPlugin.isIgnored(primitive,EGRNTestCode.EGRN_NOT_MATCHED_OSM_STREET))
                    && !primitive.hasTag("addr:street")
                ) {
                    val parsedStreetName = it.parsedStreet.extractedType?.name + " " + it.parsedStreet.extractedName
                    var affectedPrimitives = parsedStreetToPrimitiveMap.getOrDefault(parsedStreetName, mutableSetOf())
                    affectedPrimitives = affectedPrimitives.plus(primitive)
                    parsedStreetToPrimitiveMap = parsedStreetToPrimitiveMap.plus(
                        Pair(parsedStreetName, affectedPrimitives)
                    )
                }
            }
        }

        parsedStreetToPrimitiveMap.forEach { parsedName, primitives ->
            primitives.forEach{RussiaAddressHelperPlugin.markAsProcessed(it, EGRNTestCode.EGRN_NOT_MATCHED_OSM_PLACE)}
            errors.add(
                TestError.builder(
                    this, Severity.ERROR,
                    EGRNTestCode.EGRN_NOT_MATCHED_OSM_STREET.code
                )
                    .message(I18n.tr("EGRN street not found") + ": $parsedName")
                    .primitives(primitives)
                    .highlight(primitives)
                    .build()
            )
        }
    }

    override fun fixError(testError: TestError): Command? {
        var egrnStreetName = ""
        val affectedHousenumbers = mutableListOf<String>()
        val affectedAddresses = mutableListOf<ParsedAddress>()
        testError.primitives.forEach {
            if (RussiaAddressHelperPlugin.egrnResponses[it] != null) {
                val addressInfo = RussiaAddressHelperPlugin.egrnResponses[it]?.third
                val addresses =
                    addressInfo?.addresses!!.filter { it.flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM) }
                val prefferedAddress: ParsedAddress = addresses.first()
                affectedAddresses.addAll(addresses)
                egrnStreetName =
                    "${prefferedAddress.parsedStreet.extractedType?.name} ${prefferedAddress.parsedStreet.extractedName}"
                prefferedAddress.parsedHouseNumber.housenumber.let { it1 -> affectedHousenumbers.add(it1) }
            }
        }

        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        affectedHousenumbers.sort()
        val infoLabel = JMultilineLabel(
            "Несколько (${affectedHousenumbers.size}) зданий (номера ${affectedHousenumbers.joinToString(", ")})" +
                    "<br> получили из ЕГРН адрес с именем улицы: <b>${egrnStreetName}</b>,<br>" +
                    "который не был сопоставлен с улицей, существующей в данных ОСМ.<br>" +
                    "Для разрешения ошибки вы можете присвоить линии улицы имя согласно соответственно полученными из ЕГРН данным и " +
                    "<a href =https://wiki.openstreetmap.org/wiki/RU:%D0%A0%D0%BE%D1%81%D1%81%D0%B8%D1%8F/%D0%A1%D0%BE%D0%B3%D0%BB%D0%B0%D1%88%D0%B5%D0%BD%D0%B8%D0%B5_%D0%BE%D0%B1_%D0%B8%D0%BC%D0%B5%D0%BD%D0%BE%D0%B2%D0%B0%D0%BD%D0%B8%D0%B8_%D0%B4%D0%BE%D1%80%D0%BE%D0%B3>правилам именования улиц в ОСМ</a>" +
                    "<br>Если в ЕГРН содержится некорректное имя, не сопоставляемое плагином с именем улицы, " +
                    "<br>можно присвоить линии улицы тэг <b>egrn_name = $egrnStreetName</b> чтобы форсировать сопоставление",
            false,
            true
        )
        infoLabel.setMaxWidth(800)

        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        var labelText = "Полученные из ЕГРН адреса: <br>"
        affectedAddresses.forEach {
            labelText += "${it.egrnAddress},<b> тип: ${if (it.isBuildingAddress()) "здание" else "участок"}</b><br>"
        }
        val egrnAddressesLabel = JMultilineLabel(labelText, false, true)
        egrnAddressesLabel.setMaxWidth(800)
        p.add(egrnAddressesLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val buttonTexts = arrayOf(
            I18n.tr("Retry address matching"),
            I18n.tr("Ignore error"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление адресов, для которых не найдена улица в ОСМ"),
            *buttonTexts
        )
        dialog.setContent(p, false)
        dialog.setButtonIcons("dialogs/edit", "dialogs/edit", "cancel")
        dialog.showDialog()

        val answer = dialog.value
        if (answer == 3) {
            return null
        }
        if (answer == 2) {
            testError.primitives.forEach {
                RussiaAddressHelperPlugin.ignoreValidator(
                    it,
                    EGRNTestCode.getByCode(testError.code)!!
                )
            }

            return null
        }

        if (answer == 1) {
            val dataSet: DataSet = OsmDataManager.getInstance().editDataSet ?: return null
            dataSet.setSelected(testError.primitives)
            RussiaAddressHelperPlugin.selectAction.actionPerformed(ActionEvent(this, 0, ""))
        }

        return null
    }

    override fun endTest() {
        parsedStreetToPrimitiveMap = mutableMapOf()
        super.endTest()
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNStreetNotFoundTest
    }

}