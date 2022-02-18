package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.OSMAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.HouseNumberParser
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.OSMStreet
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.StreetParser
import org.openstreetmap.josm.tools.Logging
import javax.swing.JOptionPane

data class EGRNResponse(val total: Int, val results: List<EGRNFeature>) {
    //метод возвращает мапу, ключ в которой - уникальный адрес,
    // а значения - тип адреса (участок/строение, распарсенный адрес, исходный адрес из ЕГРН)
    fun parseAddresses(): ParsedAddressInfo {
        val streetParser = StreetParser()
        val houseNumberParser = HouseNumberParser()

        val addresses: MutableMap<String, Triple<Int, OSMAddress, String>> = mutableMapOf()
        val badAddresses: MutableList<Triple<Int, OSMStreet, String>> = mutableListOf()
        this.results.forEach { res ->
            val egrnAddress = res.attrs.address
            val streetParse = streetParser.parse(egrnAddress)
            val houseNumberParse = houseNumberParser.parse(egrnAddress)
            //TO DO: не забыть добавить flats & rooms
            val flat = ""
            if (streetParse.name != "") {
                if (houseNumberParse != "") {
                    val parsedOsmAddress = OSMAddress(streetParse.name, houseNumberParse, flat)
                    var key = parsedOsmAddress.getInlineAddress()
                    if (key == "") key = "${streetParse.extractedName} ${streetParse.extractedType}"
                    if (!addresses.containsKey(key)) {
                        addresses.plusAssign(Pair(
                                key,
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
                badAddresses.add(Triple(res.type, streetParse, egrnAddress))
            }
        }
        return ParsedAddressInfo(addresses, badAddresses)
    }
}