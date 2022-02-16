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
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.HouseNumberParser
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.StreetParser
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import org.openstreetmap.josm.tools.Logging
import org.openstreetmap.josm.tools.Shortcut
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
        val defaultTagsForNode: Map<String, String> = mapOf("source:addr" to "ЕГРН", "fixme" to "yes")
        val ds = layerManager.editDataSet
        val cmds: MutableList<Command> = mutableListOf()
        val mouseEN = mapView.getEastNorth(e.x, e.y)
        //  val mapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        //     .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
        RussiaAddressHelperPlugin.getEgrnClient()
            .request(mouseEN, listOf(EGRNFeatureType.PARCEL, EGRNFeatureType.BUILDING))
            .responseObject<EGRNResponse>(jacksonDeserializerOf()) { request, response, result ->
                if (response.statusCode == 200) {
                    result.success { egrnResponse ->
                        if (egrnResponse.total == 0) {
                            Logging.info("EGRN PLUGIN empty response for request ${request.url}")
                            Logging.info("$egrnResponse")
                        } else if (egrnResponse.results.all { it.attrs.address.isBlank() }) {
                            Logging.info("EGRN PLUGIN no addresses found for for request ${request.url}")
                            Logging.info("$egrnResponse")
                        } else {

                            val streetParser = StreetParser()
                            val houseNumberParser = HouseNumberParser()

                            var addresses: Map<String, Triple<Int, OSMAddress, String>> = mutableMapOf()
                            egrnResponse.results.forEach { res ->
                                val egrnAddress = res.attrs.address
                                val streetParse = streetParser.parse(egrnAddress)
                                val houseNumberParse = houseNumberParser.parse(egrnAddress)
                                //не забыть добавить flats & rooms
                                val flat = ""
                                if (streetParse.name != "") {
                                    if (houseNumberParse != "") {
                                        val parsedOsmAddress = OSMAddress(streetParse.name, houseNumberParse, flat)
                                        if (!addresses.containsKey(parsedOsmAddress.getInlineAddress())) {
                                            addresses = addresses.plus(
                                                Pair(
                                                    parsedOsmAddress.getInlineAddress(),
                                                    Triple(res.type, parsedOsmAddress, egrnAddress)
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    if (streetParse.extractedName != "") {
                                        Notification("EGRN-PLUGIN Cannot match street with OSM : ${streetParse.extractedName}, ${streetParse.extractedType}").setIcon(
                                            JOptionPane.WARNING_MESSAGE
                                        ).show()
                                        Logging.warn("EGRN-PLUGIN Cannot match street with OSM : ${streetParse.extractedName}, ${streetParse.extractedType}")
                                    }
                                }
                            }
                            var nodes: List<Node> = listOf()
                            //генерим "облако" точек вокруг места клика с адресами
                            addresses.values.forEachIndexed { index, addr ->
                                val n = Node(getNodePlacement(mouseEN, index))
                                addr.second.getTags().forEach { (tagKey, tagValue) -> n.put(tagKey, tagValue) }
                                defaultTagsForNode.forEach { (tagKey, tagValue) -> n.put(tagKey, tagValue) }
                                n.put("addr:egrn:full", addr.third)
                                n.put("addr:egrn:type", EGRNFeatureType.fromInt(addr.first).name)
                                nodes = nodes.plus(n)
                                cmds.add(AddCommand(ds, n))
                            }

                            val c: Command = SequenceCommand(I18n.tr("Added node from RussiaAddressHelper"), cmds)
                            UndoRedoHandler.getInstance().add(c)

                            ds.setSelected(nodes)
                        }
                    }
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