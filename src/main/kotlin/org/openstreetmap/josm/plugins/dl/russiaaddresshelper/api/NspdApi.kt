package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Request
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.BBox
import org.openstreetmap.josm.data.projection.Projections
import org.openstreetmap.josm.io.OsmTransferException
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ClickActionSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.LayerShiftSettingsReader
import org.openstreetmap.josm.tools.Logging
import java.net.MalformedURLException
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NspdApi(private val url: String, private val userAgent: String, private val referer: String) {
    fun request(coordinate: EastNorth, layer: NSPDLayer, bbox: BBox?): Request {
        // Глушим проверку SSL, пока у ППК и/или у JOSM проблемы с сертификатом.
        // https://stackoverflow.com/questions/47460211/kotlin-library-that-can-do-https-connection-without-certificate-verification-li
        val manager: FuelManager
        if (EgrnSettingsReader.EGRN_DISABLE_SSL_FOR_REQUEST.get()) {
            manager = FuelManager().apply {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                })

                socketFactory = SSLContext.getInstance("SSL").apply {
                    init(null, trustAllCerts, java.security.SecureRandom())
                }.socketFactory

                hostnameVerifier = HostnameVerifier { _, _ -> true }

            }
        } else {
            manager = FuelManager()
        }

        return manager.request(Method.GET, makeUrl(coordinate, layer, bbox).toString()).header(
            mapOf(
                Headers.ACCEPT to "application/json",
                Headers.CONTENT_TYPE to "application/json",
                Headers.USER_AGENT to userAgent,
                "Referer" to referer
            )
        ).timeout(3000)
    }

    private fun getFullUrl(coordinate: EastNorth, layer: String, boundary: BBox?): String {
        val boundsMargin: Int = EgrnSettingsReader.REQUEST_BOUNDS_MARGIN.get()
        val boundsExtension = ClickActionSettingsReader.EGRN_CLICK_BOUNDS_EXTENSION.get()
        val pixelPerMeter = EgrnSettingsReader.REQUEST_PIXEL_RESOLUTION.get()
        var minx = coordinate.east() - boundsExtension
        var miny = coordinate.north() - boundsExtension
        var maxx = coordinate.east() + boundsExtension
        var maxy = coordinate.north() + boundsExtension
        if (boundary != null) {
            val mercator = Projections.getProjectionByCode("EPSG:3857")
            val topLeftEN = getLayerShift(boundary.topLeft.getEastNorth(mercator))
            val bMinx = topLeftEN.east()
            val bMaxy = topLeftEN.north()
            val bottomRightEN = getLayerShift(boundary.bottomRight.getEastNorth(mercator))
            val bMaxx = bottomRightEN.east()
            val bMiny = bottomRightEN.north()
            minx = bMinx - boundsMargin
            miny = bMiny - boundsMargin
            maxy = bMaxy + boundsMargin
            maxx = bMaxx + boundsMargin
        }
        if (CommonSettingsReader.ENABLE_DEBUG_GEOMETRY_CREATION.get()) {
            val coords: ArrayList<ArrayList<Double>> = arrayListOf(
                arrayListOf(minx, miny),
                arrayListOf(minx, maxy),
                arrayListOf(maxx, maxy),
                arrayListOf(maxx, miny)
            )
            RussiaAddressHelperPlugin.createDebugObject(coords, coordinate)
        }

        val pixelWidth: Int = ((maxx - minx) * pixelPerMeter).toInt()
        val pixelHeight: Int = ((maxy - miny) * pixelPerMeter).toInt()
        val x: Int = pixelWidth / 2
        val y: Int = pixelHeight / 2
        val url = url.replace("{layer}", layer)
            .replace("{minx}", minx.toString()).replace("{miny}", miny.toString())
            .replace("{maxx}", maxx.toString()).replace("{maxy}", maxy.toString())
            .replace("{width}", pixelWidth.toString()).replace("{height}", pixelHeight.toString())
            .replace("{x}", x.toString()).replace("{y}", y.toString())
        Logging.info("RequestURL $url")
        return url
    }

    private fun makeUrl(coordinate: EastNorth, layer: NSPDLayer, bbox: BBox?): URL {
        return try {
            URL(getFullUrl(getLayerShift(coordinate), layer.layerId.toString(), bbox))
        } catch (e: MalformedURLException) {
            throw OsmTransferException(e)
        }
    }

    private fun getLayerShift(coordinate: EastNorth): EastNorth {
        return LayerShiftSettingsReader.correctCoordinate(coordinate)
    }
}