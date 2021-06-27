package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.coor.LatLon
import org.openstreetmap.josm.data.projection.Projections
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.io.OsmTransferException
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.EgrnReader
import org.openstreetmap.josm.tools.HttpClient
import java.net.MalformedURLException
import java.net.URL

class EgrnQuery(private val coordinate: EastNorth) {
    val httpClient: HttpClient
        get() {
            return HttpClient.create(getUrl(), "GET")
                .setAccept("application/json")
                .setHeader("Content-Type", "application/json")
                .setHeader("User-Agent", getUserAgent())
        }

    private fun getUrl(): URL {
        val mercator = Projections.getProjectionByCode("EPSG:3857")
        val projected = mercator.eastNorth2latlonClamped(coordinate)
        val egrnUrlString = EgrnReader.EGRN_URL_REQUEST.get()
            .replace("{lat}", projected.lat().toString())
            .replace("{lon}", projected.lon().toString())

        return try {
            URL(egrnUrlString.replace(" ", "%20"))
        } catch (e: MalformedURLException) {
            throw OsmTransferException(e)
        }
    }

    private fun getUserAgent(): String {
        return String.format(
            "%s. Loading addresses for OpenStreetMaps.",
            RussiaAddressHelperPlugin.versionInfo
        );
    }
}