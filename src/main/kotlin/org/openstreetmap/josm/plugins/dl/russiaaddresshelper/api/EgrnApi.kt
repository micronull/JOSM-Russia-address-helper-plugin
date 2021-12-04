package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.projection.Projections
import org.openstreetmap.josm.io.OsmTransferException
import org.openstreetmap.josm.tools.HttpClient
import java.net.MalformedURLException
import java.net.URL

class EgrnApi(private val url: String, private val userAgent: String) {
    fun request(coordinate: EastNorth): HttpClient.Response {
        val httpClient = HttpClient.create(makeUrl(coordinate), "GET")

        httpClient.setAccept("application/json")
        httpClient.setHeader("Content-Type", "application/json")
        httpClient.setHeader("User-Agent", userAgent)

        return httpClient.connect()
    }

    private fun getUrlWithLanLon(coordinate: EastNorth): String {
        val mercator = Projections.getProjectionByCode("EPSG:3857")
        val projected = mercator.eastNorth2latlonClamped(coordinate)

        val result = url

        result.replace("{lat}", projected.lat().toString())
        result.replace("{lon}", projected.lon().toString())

        return result
    }

    private fun makeUrl(coordinate: EastNorth): URL {
        return try {
            URL(getUrlWithLanLon(coordinate).replace(" ", "%20"))
        } catch (e: MalformedURLException) {
            throw OsmTransferException(e)
        }
    }
}