package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Request
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.coor.conversion.DecimalDegreesCoordinateFormat
import org.openstreetmap.josm.data.projection.Projections
import org.openstreetmap.josm.io.OsmTransferException
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.LayerShiftSettingsReader
import java.net.MalformedURLException
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class EgrnApi(private val url: String, private val userAgent: String) {
    fun request(coordinate: EastNorth, featureType: EGRNFeatureType): Request {
        // Глушим проверку SSL, пока у ППК и/или у JOSM проблемы с сертификатом.
        // https://stackoverflow.com/questions/47460211/kotlin-library-that-can-do-https-connection-without-certificate-verification-li
        val manager: FuelManager = FuelManager().apply {
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

        return manager.request(Method.GET, makeUrl(coordinate, featureType).toString()).header(
            mapOf(
                Headers.ACCEPT to "application/json", Headers.CONTENT_TYPE to "application/json", Headers.USER_AGENT to userAgent
            )
        )
    }

    private fun getUrlWithLatLon(coordinate: EastNorth, featureType: Int): String {
        val mercator = Projections.getProjectionByCode("EPSG:3857")
        val projected = mercator.eastNorth2latlonClamped(coordinate)

        val formatter = DecimalDegreesCoordinateFormat.INSTANCE
        val lat = formatter.latToString(projected)
        val lon = formatter.lonToString(projected)

        return url.replace("{lat}", lat).replace("{lon}", lon).replace("{type}", featureType.toString())
    }

    private fun makeUrl(coordinate: EastNorth, featureType: EGRNFeatureType): URL {
        return try {
            URL(getUrlWithLatLon(getLayerShift(coordinate, featureType), featureType.type).replace(" ", "%20"))
        } catch (e: MalformedURLException) {
            throw OsmTransferException(e)
        }
    }

    private fun getLayerShift(coordinate: EastNorth, type: EGRNFeatureType) :EastNorth {
        var shiftLayerSetting = LayerShiftSettingsReader.PARCELS_LAYER_SHIFT_SOURCE
        if (type == EGRNFeatureType.BUILDING) {
            shiftLayerSetting = LayerShiftSettingsReader.BUILDINGS_LAYER_SHIFT_SOURCE
        }

        val shiftLayer = LayerShiftSettingsReader.getValidShiftLayer(shiftLayerSetting) ?: return coordinate

        return coordinate.subtract(shiftLayer.displaySettings.displacement)
    }
}