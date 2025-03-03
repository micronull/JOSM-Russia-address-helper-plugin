package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.command.Command
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


class EGRNPlaceNotFoundTest : Test(
    I18n.tr("EGRN not found place in OSM"),
    I18n.tr("EGRN test for address not matched with place object in loaded OSM data")
) {

    private var parsedPlaceToPrimitiveMap: Map<String, Set<OsmPrimitive>> = mutableMapOf()

    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    private fun visitForPrimitive(p: OsmPrimitive) {
        if (!p.isUsable) return
        if (parsedPlaceToPrimitiveMap.isNotEmpty()) return
        //собираем в мапу домики которые не сопоставились, ключ - распознанное имя места
        RussiaAddressHelperPlugin.cache.responses.forEach { entry ->
            val primitive = entry.key
            val record = entry.value
            val addressInfo = record.addressInfo
            val addresses = addressInfo?.addresses
            if (record.isProcessed(EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND)) {
                return@forEach
            }
            addresses?.forEach {
                if (it.flags.contains(ParsingFlags.CANNOT_FIND_PLACE_OBJECT_IN_OSM)
                    && (StringUtils.isNotBlank(it.parsedHouseNumber.houseNumber)
                            && !record.isIgnored(EGRNTestCode.EGRN_NOT_MATCHED_OSM_PLACE))
                    &&(!it.flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM))
                    &&(!it.flags.contains(ParsingFlags.STREET_NAME_FUZZY_MATCH))
                    && !it.isMatchedByStreetOrPlace()
                ) {
                    val parsedPlaceName = it.parsedPlace.extractedType?.name + " " + it.parsedPlace.extractedName
                    var affectedPrimitives = parsedPlaceToPrimitiveMap.getOrDefault(parsedPlaceName, mutableSetOf())
                    affectedPrimitives = affectedPrimitives.plus(primitive)
                    parsedPlaceToPrimitiveMap = parsedPlaceToPrimitiveMap.plus(
                        Pair(parsedPlaceName, affectedPrimitives)
                    )
                }
            }
        }

        parsedPlaceToPrimitiveMap.forEach { (parsedName, primitives) ->
            RussiaAddressHelperPlugin.cache.markProcessed(primitives, EGRNTestCode.EGRN_NOT_MATCHED_OSM_PLACE)
            val highlightPrimitives: List<OsmPrimitive> = primitives.mapNotNull { p ->
                GeometryHelper.getBiggestPoly(p)
            }
            errors.add(
                TestError.builder(
                    this, Severity.ERROR,
                    EGRNTestCode.EGRN_NOT_MATCHED_OSM_PLACE.code
                )
                    .message(I18n.tr(EGRNTestCode.EGRN_NOT_MATCHED_OSM_PLACE.message) + ": $parsedName")
                    .primitives(primitives)
                    .highlight(highlightPrimitives)
                    .build()
            )
        }
    }

    override fun fixError(testError: TestError): Command? {
        var egrnPlaceName = ""
        val affectedHousenumbers = mutableListOf<String>()
        val affectedAddresses = mutableListOf<ParsedAddress>()
        testError.primitives.forEach { primitive ->
            if (RussiaAddressHelperPlugin.cache.contains(primitive)) {
                val addressInfo = RussiaAddressHelperPlugin.cache.get(primitive)?.addressInfo
                val addresses =
                    addressInfo?.addresses!!.filter { it.flags.contains(ParsingFlags.CANNOT_FIND_PLACE_OBJECT_IN_OSM) }
                affectedAddresses.addAll(addresses)
                val prefferedAddress: ParsedAddress = addresses.first()
                egrnPlaceName =
                    "${prefferedAddress.parsedPlace.extractedType?.name} ${prefferedAddress.parsedPlace.extractedName}"
                prefferedAddress.parsedHouseNumber.houseNumber.let { it1 -> affectedHousenumbers.add(it1) }
            }
        }
        affectedHousenumbers.sort()
        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val infoLabel = JMultilineLabel(
            "Несколько (${affectedHousenumbers.size}) зданий (номера ${
                affectedHousenumbers.joinToString(", ")
            })" +
                    "<br> получили из ЕГРН адрес с именем места: <b>${egrnPlaceName}</b>,<br>" +
                    "который не был сопоставлен с обьектом, существующим в данных ОСМ.<br>" +
                    "Возможно, точка или контур населенного пункта не попали в область загрузки.<br>" +
                    "Загрузите данные или создайте точку/контур обьекта.",
            false,
            true
        )
        infoLabel.setMaxWidth(800)

        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        var labelText = ""
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
            I18n.tr("Исправление адресов, для которых не найден объект в ОСМ"),
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
            RussiaAddressHelperPlugin.cache.ignoreValidator(testError.primitives, EGRNTestCode.getByCode(testError.code)!!)
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
        parsedPlaceToPrimitiveMap = mutableMapOf()
        super.endTest()
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNPlaceNotFoundTest
    }

}