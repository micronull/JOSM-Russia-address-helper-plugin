package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions

import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.failure
import com.github.kittinunf.result.success
import org.openstreetmap.josm.actions.mapmode.MapMode
import org.openstreetmap.josm.command.AddCommand
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.*
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.*
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ClickActionSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.LayerFilterSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper.Companion.generateBuildingMultiPolygon
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper.Companion.getAddressTagsForClickAction
import org.openstreetmap.josm.tools.*
import java.awt.Cursor
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.Pair

class ClickAction : MapMode(
    ACTION_NAME, ICON_NAME, null, Shortcut.registerShortcut(
        "data:egrn_click", I18n.tr("Data: {0}", I18n.tr(ACTION_NAME)), KeyEvent.KEY_LOCATION_UNKNOWN, Shortcut.NONE
    ), ImageProvider.getCursor("crosshair", "create_note")
), KeyPressReleaseListener {

    companion object {
        val ACTION_NAME = I18n.tr("By click")
        val ICON_NAME = "click.svg"
    }

    override fun enterMode() {
        super.enterMode()
        val map = MainApplication.getMap()
        map.mapView.addMouseListener(this)
        map.keyDetector.addKeyListener(this)
    }

    override fun exitMode() {
        super.exitMode()
        val map = MainApplication.getMap()
        map.mapView.removeMouseListener(this)
        map.keyDetector.removeKeyListener(this)
    }

    override fun updateEnabledState() {
        isEnabled = MainApplication.isDisplayingMapView() && MainApplication.getMap().mapView.isActiveLayerDrawable
    }

    override fun mouseClicked(e: MouseEvent) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return
        }
        val exportGeometry = ClickActionSettingsReader.EGRN_CLICK_ENABLE_GEOMETRY_IMPORT.get()

        val map = MainApplication.getMap()
        map.selectMapMode(map.mapModeSelect)

        val mapView = map.mapView
        if (!mapView.isActiveLayerDrawable) {
            return
        }
        val placeBoundariesMode = e.isAltDown
        mapView.setNewCursor(Cursor(Cursor.WAIT_CURSOR), this)
        val defaultTagsForNode: Map<String, String> = mapOf("source:addr" to "ЕГРН", "fixme" to "REMOVE ME!")
        val ds = layerManager.editDataSet
        val cmds: MutableList<Command> = mutableListOf()
        val mouseEN = mapView.getEastNorth(e.x, e.y)
        var index = 0
        var nodes: Set<Node> = setOf()
        val layersToRequest =
            if (placeBoundariesMode) setOf(NSPDLayer.PLACES_BOUNDARIES) else LayerFilterSettingsReader.getClickActionEnabledLayers()
        val projectionBounds = MainApplication.getMap().mapView.state.viewClipRectangle.cornerBounds
        val requestBBox = if (placeBoundariesMode) projectionBounds.toBBox() else null
        val errorMessages: MutableSet<String> = mutableSetOf()
        var buildingPrimitive: OsmPrimitive? = null
        val fullResponse = NSPDResponse()
        val primitivesToValidate = mutableListOf<OsmPrimitive>()
        val mergeDataOnSingleNode = ClickActionSettingsReader.EGRN_CLICK_MERGE_FEATURES.get()
        val nodeTags: MutableMap<Pair<NSPDLayer, Int>, MutableMap<String, String>> = mutableMapOf()
        var repeatsExhausted = false
        layersToRequest.forEach { requestLayer ->
            if (repeatsExhausted) {
                val msg = I18n.tr("Data downloading failed, reason: too much request errors, interrupting")
                val notification = Notification("$msg").setIcon(JOptionPane.WARNING_MESSAGE)
                notification.duration = Notification.TIME_LONG
                notification.show()
                return@forEach
            }
            var needToRepeat = true
            val clickRetries = 1
            val clickDelay = 1000L
            var retries = clickRetries
            while (needToRepeat) {
                val (request, response, result) = RussiaAddressHelperPlugin.getNSPDClient()
                    .request(mouseEN, requestLayer, requestBBox)
                    .responseObject<GetFeatureInfoResponse>(jacksonDeserializerOf())
                RussiaAddressHelperPlugin.totalRequestsPerSession++
                if (response.statusCode == 200) {
                    needToRepeat = false
                    result.success { nspdResponse ->
                        RussiaAddressHelperPlugin.totalSuccessRequestsPerSession++
                        fullResponse.addResponse(nspdResponse, requestLayer)
                        if (nspdResponse.features.isEmpty()) {
                            Logging.info("EGRN PLUGIN empty response for request ${request.url}")
                            Logging.info("$nspdResponse")
                        } else {
                            val features = nspdResponse.features
                            features.forEachIndexed { localIndex, feature ->
                                val tagsForNode: MutableMap<String, String> = mutableMapOf()
                                tagsForNode.putAll(defaultTagsForNode)
                                val address = feature.parseAddress(mouseEN)

                                tagsForNode.putAll(getAddressTagsForClickAction(address))
                                tagsForNode.putAll(feature.getTags())
                                if (!placeBoundariesMode) {
                                    nodeTags[Pair(requestLayer, localIndex)] = tagsForNode
                                }
                                if (feature.geometry != null) {
                                    if (exportGeometry && (requestLayer == NSPDLayer.BUILDING || requestLayer == NSPDLayer.UNFINISHED)) {
                                        val buildTags: MutableMap<String, String> =
                                            TagHelper.getBuildingTags(feature, requestLayer)
                                        val generatedBuilding =
                                            generateBuildingMultiPolygon(feature.geometry, ds, buildTags)
                                        cmds.addAll(generatedBuilding.first)
                                        buildingPrimitive = generatedBuilding.second
                                    } else if (e.isControlDown) {
                                        val geometryTags = mutableMapOf<String, String>(
                                            "fixme" to "REMOVE ME!",
                                            "source:geometry" to "ЕГРН",
                                            "autoremove:source:geometry" to requestLayer.name
                                        )
                                        geometryTags.putAll(TagHelper.getLotTags(feature))

                                        val generatedGeometry =
                                            generateBuildingMultiPolygon(feature.geometry, ds, geometryTags)
                                        cmds.addAll(generatedGeometry.first)
                                    } else if (requestLayer == NSPDLayer.PLACES_BOUNDARIES && placeBoundariesMode) {
                                        val geometryTags = mutableMapOf<String, String>(
                                            "fixme" to "REMOVE ME!",
                                            "autoremove:source:geometry" to requestLayer.name,
                                        )
                                        val placeTags = TagHelper.getPlaceTags(feature).toMutableMap()
                                        placeTags["source:geometry"] = "ЕГРН"
                                        geometryTags.putAll(placeTags)
                                        val generatedGeometry =
                                            generateBuildingMultiPolygon(feature.geometry, ds, geometryTags, placeTags)
                                        cmds.addAll(generatedGeometry.first)
                                    }
                                }
                            }
                        }
                    }

                    result.failure {
                        needToRepeat = false
                        retries = 0
                        val errorMessage = (result as Result.Failure).error.message
                        errorMessages.plusAssign(errorMessage ?: "")
                        Logging.error("EGRN-Plugin $errorMessage")
                    }
                } else {
                    result.failure {
                        needToRepeat = if (retries > 0) {
                            true
                        } else {
                            repeatsExhausted = true
                            val errorMessage = (result as Result.Failure).error.message
                            errorMessages.plusAssign(errorMessage ?: "")
                            Logging.error("EGRN-Plugin $errorMessage")
                            false
                        }
                        if (response.statusCode == -1) {
                            Logging.warn("EGRN-Plugin Error connection refused, retries $retries")
                        } else {
                            Logging.warn("EGRN-Plugin Error on request: ${response.statusCode}")
                        }
                        retries--
                        Thread.sleep(clickDelay)
                    }
                }
            }
            index++
        }

        //generate nodes
        if (nodeTags.isNotEmpty()) {
            if (mergeDataOnSingleNode) {
                nodes = nodes.plus(Node(GeometryHelper.getNodePlacement(mouseEN, 0)))
                nodes.first().putAll(getMergedTags(nodeTags))
            } else {
                nodes = nodes.plus(getAllNodesWithTags(mouseEN, nodeTags))
            }
        }

        nodes.forEach { node -> cmds.add(AddCommand(ds, node)) }

        if (buildingPrimitive != null) {
            val parsedAddressInfo = fullResponse.parseAddresses(mouseEN)
            RussiaAddressHelperPlugin.cache.add(
                buildingPrimitive!!,
                mouseEN,
                fullResponse,
                parsedAddressInfo
            )
            val buildingsToCheck: MutableList<OsmPrimitive> = mutableListOf(buildingPrimitive!!)
            primitivesToValidate.add(buildingPrimitive!!)
            RussiaAddressHelperPlugin.cleanFromDoubles(buildingsToCheck)
            if (buildingsToCheck.isNotEmpty() && parsedAddressInfo.canAssignAddress()) {
                val addressTags = mutableMapOf<String, String>()
                val preferredAddress = parsedAddressInfo.getPreferredAddress()!!
                if (TagSettingsReader.EGRN_ADDR_RECORD.get()) {
                    addressTags["addr:RU:egrn"] = preferredAddress.egrnAddress
                }
                addressTags.putAll(preferredAddress.getOsmAddress().getBaseAddressTagsWithSource())
                cmds.add(
                    ChangePropertyCommand(
                        ds,
                        listOf(buildingPrimitive),
                        addressTags
                    )
                )
            }
        }

        errorMessages.forEach { err ->
            val msg = I18n.tr("Data downloading failed, reason:")
            val notification = Notification("$msg $err").setIcon(JOptionPane.WARNING_MESSAGE)
            notification.duration = Notification.TIME_LONG
            notification.show()
        }

        if (cmds.isNotEmpty()) {
            val c: Command =
                SequenceCommand(I18n.tr("Added node from RussiaAddressHelper"), cmds)
            UndoRedoHandler.getInstance().add(c)
        }

        val simplifyCommands = GeometryHelper.simplifyWays(buildingPrimitive)
        if (simplifyCommands.isNotEmpty()) {
            UndoRedoHandler.getInstance().add(SequenceCommand(I18n.tr("Simplify imported geometry"), simplifyCommands))
            val msg = I18n.tr("Imported geometry was simplified, nodes removed")
            val notification = Notification(msg + ": ${simplifyCommands.size}").setIcon(JOptionPane.INFORMATION_MESSAGE)
            notification.duration = Notification.TIME_LONG
            notification.show()
        }

        if (primitivesToValidate.isNotEmpty()) {
            RussiaAddressHelperPlugin.runEgrnValidation(RussiaAddressHelperPlugin.cache.responses.keys)
        }

        if (buildingPrimitive != null) {
            ds.setSelected(buildingPrimitive)
        } else {
            ds.setSelected(nodes)
        }
    }

    //выглядит очень неэффективно, нужен рефакторинг
    private fun getMergedTags(nodeTags: MutableMap<Pair<NSPDLayer, Int>, MutableMap<String, String>>): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        val tagsByKeyMap = mutableMapOf<String, MutableSet<Pair<String, Pair<NSPDLayer, Int>>>>()
        nodeTags.forEach { (info, tags) ->
            tags.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    if (tagsByKeyMap.containsKey(key)) {
                        tagsByKeyMap[key]?.add(Pair(value, info))
                    } else {
                        tagsByKeyMap[key] = mutableSetOf(Pair(value, info))
                    }
                }
            }
        }

        tagsByKeyMap.forEach { (key, setOfValues) ->
            if (setOfValues.size == 1 || setOfValues.distinctBy { it.first }.size == 1) {
                result[key] = setOfValues.first().first
            } else {
                if (setOfValues.distinctBy { it.second.second }.size > 1) {
                    setOfValues.forEach { entry ->
                        result["$key:${entry.second.first.name.lowercase()}:${entry.second.second}"] = entry.first
                    }
                } else {
                    setOfValues.forEach { entry ->
                        result["$key:${entry.second.first.name.lowercase()}"] = entry.first
                    }
                }
            }
        }

        return result
    }

    private fun getAllNodesWithTags(
        mouseEN: EastNorth,
        nodeTags: MutableMap<Pair<NSPDLayer, Int>, MutableMap<String, String>>
    ): List<Node> {
        val result = mutableListOf<Node>()
        var index = 0
        nodeTags.forEach { (info, tags) ->
            val n = Node(GeometryHelper.getNodePlacement(mouseEN, index))
            n.putAll(tags)
            n.put("addr:RU:layer", info.first.name)
            result.add(n)
            index++
        }

        return result
    }

    override fun doKeyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ESCAPE) {
            val map = MainApplication.getMap()
            map.selectMapMode(map.mapModeSelect)
        }
    }

    override fun doKeyReleased(e: KeyEvent?) {
        // Do nothing
    }
}