package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Relation
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ValidationSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.Geometry.*
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import javax.swing.JPanel
import kotlin.math.min
import kotlin.math.roundToLong

class EGRNStreetOrPlaceTooFarTest : Test(
    I18n.tr("EGRN street/place too far"),
    I18n.tr("EGRN street/place way too far from street/place in OSM")
) {
    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    private fun visitForPrimitive(p: OsmPrimitive) {
        //Валидация не учитывает вторичные (не предпочитаемые) валидные адреса
        if (!p.isUsable) return
        val response = RussiaAddressHelperPlugin.cache.get(p)
        if (response == null || response.addressInfo?.getPreferredAddress() == null
        ) {
            return
        }
        val preferredAddress = response.addressInfo.getPreferredAddress()!!

        if (preferredAddress.isMatchedByStreet() && !response.isIgnored(EGRNTestCode.EGRN_STREET_FOUND_TOO_FAR)) {
            val parsedStreet = preferredAddress.parsedStreet

            val matchingPrimitives = parsedStreet.getMatchingPrimitives()
            if (matchingPrimitives.isEmpty()) {
                Logging.warn("EGRN PLUGIN: Parent too far validator got street name ${parsedStreet.name}, but have no matched primitives! ")
                return
            }
            val centroidNode = Node(GeometryHelper.getPrimitiveCentroid(p))
            val closestStreet = getClosestPrimitive(centroidNode, matchingPrimitives)
            //существуют ли улицы, заданные отношениями?
            val closestStreetNode = getClosestPrimitive(centroidNode, (closestStreet as Way).nodes)
            val distanceToWay = getDistance(centroidNode, closestStreet)
            val distanceToNode = getDistance(
                centroidNode,
                closestStreetNode
            )
            val distance = min(distanceToNode, distanceToWay)
            if (distance > ValidationSettingsReader.DISTANCE_FOR_STREET_WAY_SEARCH.get()
            ) {
                val streetName = parsedStreet.name
                RussiaAddressHelperPlugin.cache.markProcessed(p, EGRNTestCode.EGRN_STREET_FOUND_TOO_FAR)
                val highlightPrimitive = GeometryHelper.getBiggestPoly(p)
                errors.add(
                    TestError.builder(this, Severity.ERROR, EGRNTestCode.EGRN_STREET_FOUND_TOO_FAR.code)
                        .message(I18n.tr(EGRNTestCode.EGRN_STREET_FOUND_TOO_FAR.message) + ": " + streetName + " (${distance.roundToLong()} м)")
                        .primitives(p)
                        .highlight(listOf(highlightPrimitive, closestStreet))
                        .build()
                )
            }

        }

        if (preferredAddress.isMatchedByPlace()) {
            val severity = if (preferredAddress.isMatchedByStreet()) Severity.WARNING else Severity.ERROR

            //если не по улице, значит по месту
            val parsedPlace = preferredAddress.parsedPlace
            var placeNodeTooFar = false
            var distanceToPlaceNode = 0.0
            val matchedPrimitives = parsedPlace.getMatchingPrimitives()
            if (matchedPrimitives.isEmpty()) {
                Logging.warn("EGRN PLUGIN: Parent too far validator got matched place ${parsedPlace.name}, but have no matched primitives! ")
                return
            }
            val matchedNodes = matchedPrimitives.filterIsInstance<Node>()
            val matchedWays = matchedPrimitives.filterIsInstance<Way>()
            val matchedRelations = matchedPrimitives.filterIsInstance<Relation>()
            val centroidNode = Node(GeometryHelper.getPrimitiveCentroid(p))
            if (matchedNodes.isNotEmpty() && !RussiaAddressHelperPlugin.cache.isIgnored(
                    p,
                    EGRNTestCode.EGRN_PLACE_FOUND_TOO_FAR
                )
            ) {
                val closestNode = getClosestPrimitive(centroidNode, matchedNodes)

                val distance = getDistance(
                    centroidNode,
                    closestNode
                )
                if (distance > ValidationSettingsReader.DISTANCE_FOR_PLACE_NODE_SEARCH.get()) {
                    placeNodeTooFar = true
                    distanceToPlaceNode = distance
                }
            }

            if (matchedWays.isNotEmpty()) { //граница места задана полигоном
                if (matchedWays.size > 1) {
                    Logging.warn("EGRN PLUGIN: Parent too far validator got more than one (${matchedWays.size}) place boundary for ${parsedPlace.name}!")
                }
                //TODO как отловить случай, что мы проверяем пересечение битого, незамкнутого мультика здания с границей места?
                //возможно, тут надо использовать другие функции - что-то из класса мультиполигона или Geometry
                val containingBoundary =
                    matchedWays.find {
                        it.isArea && (polygonIntersection(
                            getArea(GeometryHelper.getBiggestPoly(p)!!.nodes),
                            getArea(it.nodes)
                        )
                            .equals(PolygonIntersection.FIRST_INSIDE_SECOND))
                    }
                if (containingBoundary == null
                    && !RussiaAddressHelperPlugin.cache.isIgnored(p, EGRNTestCode.EGRN_ADDRESS_NOT_INSIDE_PLACE_POLY)
                ) {
                    val placeName = parsedPlace.name
                    val primitives = matchedWays.plus(p)
                    errors.add(
                        TestError.builder(this, severity, EGRNTestCode.EGRN_ADDRESS_NOT_INSIDE_PLACE_POLY.code)
                            .message(I18n.tr(EGRNTestCode.EGRN_ADDRESS_NOT_INSIDE_PLACE_POLY.message) + ": " + placeName)
                            .primitives(p)
                            .highlight(primitives)
                            .build()
                    )
                } else {
                    //нашелся полигон места, гасим ошибку "точка слишком далеко"
                    placeNodeTooFar = false
                }
            }

            if (matchedRelations.isNotEmpty()) {
                if (matchedRelations.size > 1) {
                    Logging.warn("EGRN PLUGIN: ParentTooFar validator got more than one (${matchedRelations.size}) place boundary relation for ${parsedPlace.name}!")
                }
                val placeName = parsedPlace.name
                val primitives = matchedRelations.plus(p)
                var insideOfSomePlaceBoundary = false
                val outsideOfBoundaries = mutableListOf<Relation>()
                matchedRelations.forEach { relation ->
                    if (relation.hasIncompleteMembers() && !RussiaAddressHelperPlugin.cache.isIgnored(
                            p,
                            EGRNTestCode.EGRN_PLACE_BOUNDARY_INCOMPLETE
                        )
                    ) {
                        errors.add(
                            TestError.builder(this, severity, EGRNTestCode.EGRN_PLACE_BOUNDARY_INCOMPLETE.code)
                                .message(I18n.tr(EGRNTestCode.EGRN_PLACE_BOUNDARY_INCOMPLETE.message) + ": " + placeName)
                                .primitives(p)
                                .highlight(primitives)
                                .build()
                        )
                    } else {
                        //TODO как отловить случай, что мы проверяем пересечение битого, незамкнутого мультика здания с границей места?
                        if (!isPolygonInsideMultiPolygon(GeometryHelper.getBiggestPoly(p)!!.nodes, relation, null)
                            && !RussiaAddressHelperPlugin.cache.isIgnored(
                                p,
                                EGRNTestCode.EGRN_ADDRESS_NOT_INSIDE_PLACE_POLY
                            )
                        ) {
                            outsideOfBoundaries.add(relation)
                        } else {
                            insideOfSomePlaceBoundary = true
                            placeNodeTooFar = false
                        }

                    }
                }
                if (!insideOfSomePlaceBoundary) {
                    outsideOfBoundaries.forEach { boundary ->
                        val highlightPrimitives = listOf<OsmPrimitive>(boundary, p)
                        errors.add(
                            TestError.builder(
                                this,
                                severity,
                                EGRNTestCode.EGRN_ADDRESS_NOT_INSIDE_PLACE_POLY.code
                            )
                                .message(I18n.tr(EGRNTestCode.EGRN_ADDRESS_NOT_INSIDE_PLACE_POLY.message) + ": " + placeName)
                                .primitives(p)
                                .highlight(highlightPrimitives)
                                .build()
                        )
                    }
                }
            }

            if (placeNodeTooFar) {
                val placeName = parsedPlace.name
                val primitives = matchedNodes.plus(p)
                errors.add(
                    TestError.builder(this, severity, EGRNTestCode.EGRN_PLACE_FOUND_TOO_FAR.code)
                        .message(I18n.tr(EGRNTestCode.EGRN_PLACE_FOUND_TOO_FAR.message) + ": " + placeName + " (${distanceToPlaceNode.roundToLong()} м)")
                        .primitives(p)
                        .highlight(primitives)
                        .build()
                )
            }
        }
    }

    override fun fixError(testError: TestError): Command? {
        val primitive = testError.primitives.first() //должен быть в каждой ошибке только 1 примитив
        val parsedResponse = RussiaAddressHelperPlugin.cache.get(primitive)
        if (parsedResponse == null) {
            Logging.error("EGRN PLUGIN trying to fix building without EGRN response")
            return null
        }
        val parsedAddress = parsedResponse.addressInfo!!

        val egrnTestCode = EGRNTestCode.getByCode(testError.code)
        val errorMessage = testError.message
        val errorText: String
        val osmObjectName: String
        when (egrnTestCode) {
            EGRNTestCode.EGRN_STREET_FOUND_TOO_FAR -> {
                osmObjectName = parsedAddress.getPreferredAddress()!!.parsedStreet.name
                errorText =
                    "$errorMessage<br>Здание получило из ЕГРН адрес с именем улицы: <b>${osmObjectName}</b>," +
                            "<br>линия которой в ОСМ находится слишком далеко (более заданного в настройках расстояния в ${ValidationSettingsReader.DISTANCE_FOR_STREET_WAY_SEARCH.get()} метров)" +
                            "<br>Присвойте это имя более близко расположенной линии проезжей части," +
                            " или, если ее действительно нет рядом - проигнорируйте эту ошибку."
            }
            EGRNTestCode.EGRN_PLACE_FOUND_TOO_FAR -> {
                osmObjectName = parsedAddress.getPreferredAddress()!!.parsedPlace.name
                errorText =
                    "$errorMessage<br>Здание получило из ЕГРН адрес по месту: <b>${osmObjectName}</b>," +
                            "<br>точка которого в ОСМ находится слишком далеко от здания (более заданного в настройках расстояния в ${ValidationSettingsReader.DISTANCE_FOR_PLACE_NODE_SEARCH.get()} метров)" +
                            "<br>Убедитесь что сопоставление происходит с верным местом, проверьте что адрес не должен быть на самом деле по улице, или," +
                            "<br>проигнорируйте эту ошибку."
            }
            EGRNTestCode.EGRN_ADDRESS_NOT_INSIDE_PLACE_POLY -> {
                osmObjectName = parsedAddress.getPreferredAddress()!!.parsedPlace.name
                errorText =
                    "Здание получило из ЕГРН адрес с именем места: <b>${osmObjectName}</b>," +
                            "<br>при этом здание находится вне полигона границы места в ОСМ" +
                            "<br>Скорректируйте границу места, сверяясь с официальными источниками, или" +
                            "<br>проигнорируйте эту ошибку."
            }
            EGRNTestCode.EGRN_PLACE_BOUNDARY_INCOMPLETE -> {
                osmObjectName = parsedAddress.getPreferredAddress()!!.parsedPlace.name
                errorText =
                    "Здание получило из ЕГРН адрес с именем места: <b>${osmObjectName}</b>," +
                            "<br>при этом мультиполигон границы места - неполный и не может быть проверен." +
                            "<br>Докачайте отсутствующих участников и повторите валидацию, или" +
                            "<br>проигнорируйте эту ошибку."
            }
            else -> errorText = "Валидатор сработал на ошибке с невалидным кодом: ${egrnTestCode!!.name}"

        }

        val p = JPanel(GridBagLayout())
        val label1 = JMultilineLabel(description)
        label1.setMaxWidth(800)
        p.add(label1, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val infoLabel = JMultilineLabel(
            errorText,
            false,
            true
        )
        infoLabel.setMaxWidth(800)

        p.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        var labelText = "Полученный из ЕГРН адрес: <br>"
        val prefferedAddress = parsedAddress.getPreferredAddress()
        labelText += "${prefferedAddress?.egrnAddress},<b> тип: ${if (prefferedAddress!!.isBuildingAddress()) "здание" else "участок"}</b><br>"

        val egrnAddressesLabel = JMultilineLabel(labelText, false, true)
        egrnAddressesLabel.setMaxWidth(800)
        p.add(egrnAddressesLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))

        val buttonTexts = arrayOf(
            I18n.tr("Ignore error"),
            I18n.tr("Cancel")
        )
        val dialog = ExtendedDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Исправление ошибки валидации геометрии"),
            *buttonTexts
        )
        dialog.setContent(p, false)
        dialog.setButtonIcons("dialogs/edit", "cancel")
        dialog.showDialog()

        val answer = dialog.value
        if (answer == 2) {
            return null
        }
        if (answer == 1) {
            RussiaAddressHelperPlugin.cache.ignoreValidator(
                testError.primitives,
                EGRNTestCode.getByCode(testError.code)!!
            )
            return null
        }
        return null
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNStreetOrPlaceTooFarTest
    }
}