package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions

import com.github.kittinunf.result.success
import org.apache.commons.text.StringEscapeUtils
import org.openstreetmap.josm.actions.mapmode.MapMode
import org.openstreetmap.josm.command.AddCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNFeatureType
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

        val ds = layerManager.editDataSet
        val cmds: MutableList<Command> = mutableListOf()
        val mouseEN = mapView.getEastNorth(e.x, e.y)
        val n = Node(mouseEN)

        RussiaAddressHelperPlugin.getEgrnClient().request(mouseEN, EGRNFeatureType.BUILDING)
            .responseString { _, response, result ->
                if (response.statusCode == 200) {
                    result.success {
                        val match = Regex("""address":\s"(.+?)",\s"cn"""").find(StringEscapeUtils.unescapeJson(it))
                        if (match == null) {
                            Logging.error("Parse EGRN response error.")
                            Logging.error("EGRN response was: ${StringEscapeUtils.unescapeJson(it)}")
                        } else {
                            val address = match.groupValues[1]

                            n.put("addr:RU:egrn", address)
                            n.put("fixme", "yes")

                            val streetParser = StreetParser()
                            val houseNumberParser = HouseNumberParser()
                            val streetParse = streetParser.parse(address)
                            val houseNumberParse = houseNumberParser.parse(address)
                            if (streetParse.name != "") {
                                if (houseNumberParse != "") {
                                    n.put("addr:housenumber", houseNumberParse)
                                    n.put("addr:street", streetParse.name)
                                    n.put("source:addr", "ЕГРН")
                                }
                            } else {
                                if (streetParse.extractedName != "") {
                                    Notification("EGRN-PLUGIN Cannot match street with OSM : ${streetParse.extractedName}, ${streetParse.extractedType}").setIcon(
                                        JOptionPane.WARNING_MESSAGE
                                    ).show()
                                    Logging.warn("EGRN-PLUGIN Cannot match street with OSM : ${streetParse.extractedName}, ${streetParse.extractedType}")
                                }
                            }

                            cmds.add(AddCommand(ds, n))

                            val c: Command = SequenceCommand(I18n.tr("Added node from RussiaAddressHelper"), cmds)
                            UndoRedoHandler.getInstance().add(c)

                            ds.setSelected(n)
                        }
                    }
                }
            }
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