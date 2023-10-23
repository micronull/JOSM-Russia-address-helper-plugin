package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions

import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.github.kittinunf.result.success
import org.openstreetmap.josm.actions.mapmode.MapMode
import org.openstreetmap.josm.command.AddCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNFeatureType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNResponse
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import org.openstreetmap.josm.tools.Logging
import org.openstreetmap.josm.tools.Shortcut
import java.awt.Cursor
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

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

    override fun mouseClicked(e: MouseEvent) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return
        }

        val map = MainApplication.getMap()
        map.selectMapMode(map.mapModeSelect)

        val mapView = map.mapView
        if (!mapView.isActiveLayerDrawable) {
            return
        }
        mapView.setNewCursor(Cursor(Cursor.WAIT_CURSOR), this)
        val defaultTagsForNode: Map<String, String> = mapOf("source:addr" to "ЕГРН", "fixme" to "yes")
        val ds = layerManager.editDataSet
        val cmds: MutableList<Command> = mutableListOf()
        val mouseEN = mapView.getEastNorth(e.x, e.y)
        var needToRepeat = true
        val clickRetries = 5
        val clickDelay = 1000L
        var retries = clickRetries
        while (needToRepeat) {
            val (request, response, result) = RussiaAddressHelperPlugin.getEgrnClient()
                .request(mouseEN, listOf(EGRNFeatureType.PARCEL, EGRNFeatureType.BUILDING))
                .responseObject<EGRNResponse>(jacksonDeserializerOf())
            RussiaAddressHelperPlugin.totalRequestsPerSession++
            if (response.statusCode == 200) {
                needToRepeat = false
                result.success { egrnResponse ->
                    RussiaAddressHelperPlugin.totalSuccessRequestsPerSession++
                    if (egrnResponse.total == 0) {
                        Logging.info("EGRN PLUGIN empty response for request ${request.url}")
                        Logging.info("$egrnResponse")
                    } else if (egrnResponse.results.all { it.attrs?.address?.isBlank() != false }) {
                        Logging.info("EGRN PLUGIN no addresses found for for request ${request.url}")
                        Logging.info("$egrnResponse")
                    } else {

                        val allAddresses = egrnResponse.parseAddresses(mouseEN)
                        val parsedAddresses = allAddresses.addresses.filter { it.isValidAddress() }
                        //val parsedAddresses = allAddresses.addresses

                        var nodes: List<Node> = listOf()
                        //генерим "облако" точек вокруг места клика с адресами
                        parsedAddresses.forEachIndexed { index, addr ->
                            val n = Node(getNodePlacement(mouseEN, index))
                            addr.getOsmAddress().getTags().forEach { (tagKey, tagValue) -> n.put(tagKey, tagValue) }
                            defaultTagsForNode.forEach { (tagKey, tagValue) -> n.put(tagKey, tagValue) }
                            n.put("addr:RU:egrn", addr.egrnAddress)
                            var addrType = EGRNFeatureType.PARCEL
                            if (addr.isBuildingAddress()) {
                                addrType = EGRNFeatureType.BUILDING
                            }
                            n.put("addr:RU:egrn_type", addrType.name)
                            nodes = nodes.plus(n)
                            cmds.add(AddCommand(ds, n))
                        }

                        val badAddresses = allAddresses.addresses.filter { !it.isValidAddress() }
                        badAddresses.forEachIndexed { index, addr ->
                            val node = Node(getNodePlacement(mouseEN, index + parsedAddresses.size))

                            val defaultTagsForBadNode: Map<String, String> =
                                mapOf("source:addr" to "ЕГРН", "fixme" to "REMOVE ME!")

                            node.put("addr:RU:extracted_street_name", addr.parsedStreet.extractedName)
                            node.put("addr:RU:extracted_street_type", addr.parsedStreet.extractedType?.name)
                            node.put("addr:RU:extracted_place_name", addr.parsedPlace.extractedName)
                            node.put("addr:RU:extracted_place_type", addr.parsedPlace.extractedType?.name)
                            node.put("addr:RU:parsed_housenumber", addr.parsedHouseNumber.houseNumber)
                            node.put("addr:RU:parsed_flats", addr.parsedHouseNumber.flats)
                            defaultTagsForBadNode.forEach { (tagKey, tagValue) ->
                                node.put(
                                    tagKey,
                                    tagValue
                                )
                            }
                            node.put("addr:RU:egrn", addr.egrnAddress)
                            var addrType = EGRNFeatureType.PARCEL
                            if (addr.isBuildingAddress()) {
                                addrType = EGRNFeatureType.BUILDING
                            }
                            node.put("addr:RU:egrn_type", addrType.name)
                            nodes = nodes.plus(node)
                            cmds.add(AddCommand(ds, node))
                        }

                        if (cmds.isNotEmpty()) {
                            val c: Command =
                                SequenceCommand(I18n.tr("Added node from RussiaAddressHelper"), cmds)
                            UndoRedoHandler.getInstance().add(c)

                            ds.setSelected(nodes)
                        }
                    }
                }
            } else {
                if (retries > 0) {
                    needToRepeat = true
                } else {
                    val msg = I18n.tr("Data downloading failed, reason:")
                    Notification("$msg ${response.statusCode} ${response.data}").setIcon(JOptionPane.WARNING_MESSAGE)
                        .show()
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


    private fun getNodePlacement(center: EastNorth, index: Int): EastNorth {
        //радиус разброса точек относительно центра в метрах
        val radius = 5
        val angle = 55.0
        if (index == 0) return center
        val startPoint = EastNorth(center.east() - radius, center.north())
        return startPoint.rotate(center, angle * index)
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