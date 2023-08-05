package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.fuel.core.requests.DefaultBody
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.coor.conversion.DecimalDegreesCoordinateFormat
import org.openstreetmap.josm.data.projection.Projections
import org.openstreetmap.josm.io.OsmTransferException
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.LayerShiftSettingsReader
import org.openstreetmap.josm.tools.Logging
import java.io.ByteArrayInputStream
import java.net.MalformedURLException
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class EgrnApi(private val url: String, private val userAgent: String) {
    fun request(coordinate: EastNorth, featureTypes: List<EGRNFeatureType>): Request {
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
        //тут надо производить подмену клиента на кэширующего
        //так же надо завести мапу примитив - координаты, чтобы проверять наличие в кэше нужного ответа
        //непонятно как делать сериализацию, не хочется тащить новые зависимости
        //решение выглядит крайне кривым - передача ключа поиска через хедеры
      /* val defaultClient = manager.client
        val cachingClient = object: Client {
            override fun executeRequest(request: Request): Response {
                if(!request.headers.contains(Headers.CONTENT_LOCATION)) return defaultClient.executeRequest(request)
                val coordinateAsString = request.headers[Headers.CONTENT_LOCATION].iterator().next()
                var eastNorth : EastNorth? = null
                try {
                    val east = coordinateAsString.split(";")[0].toDouble()
                    val north = coordinateAsString.split(";")[1].toDouble()
                    eastNorth = EastNorth(east,north)
                } catch (ex : NumberFormatException) {
                    return defaultClient.executeRequest(request)
                }

                val primitive = primitiveToCoordMap[eastNorth]
                if (primitive!=null) {
                    val egrnResponse = RussiaAddressHelperPlugin.egrnResponses[primitive]!!.second
                   egrnResponse.
                    val mapper = ObjectMapper().registerKotlinModule()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    val body : Body = DefaultBody.from({ ByteArrayInputStream(.egrnResponse) }, null)

                    return Response(request.url,200,"", Headers(),0L, body).respo
                }
                return defaultClient.executeRequest(request)
            }
        }
        manager.client = cachingClient

       */
        return manager.request(Method.GET, makeUrl(coordinate, featureTypes).toString()).header(
            mapOf(
                Headers.ACCEPT to "application/json",
                Headers.CONTENT_TYPE to "application/json",
                Headers.USER_AGENT to userAgent,
        //        Headers.CONTENT_LOCATION to coordinate.east().toString() +";"+coordinate.north().toString() //адские костыли
            )
        ).timeout(3000)


    }

    private fun getUrlWithLatLon(coordinate: EastNorth, featureTypes: List<Int>): String {
        val mercator = Projections.getProjectionByCode("EPSG:3857")
        val projected = mercator.eastNorth2latlonClamped(coordinate)

        val formatter = DecimalDegreesCoordinateFormat.INSTANCE
        val lat = formatter.latToString(projected)
        val lon = formatter.lonToString(projected)
        val typesString = featureTypes.joinToString(separator = ",")
        val url = url.replace("{lat}", lat).replace("{lon}", lon).replace("{type}", typesString)
        Logging.info("RequestURL $url")
        return url
    }

    private fun makeUrl(coordinate: EastNorth, featureTypes: List<EGRNFeatureType>): URL {
        return try {
            URL(
                getUrlWithLatLon(
                    getLayerShift(coordinate),
                    featureTypes.map { it.type }).replace(" ", "%20")
            )
        } catch (e: MalformedURLException) {
            throw OsmTransferException(e)
        }
    }

    private fun getLayerShift(coordinate: EastNorth): EastNorth {
        val shiftLayerSetting = LayerShiftSettingsReader.LAYER_SHIFT_SOURCE

        val shiftLayer = LayerShiftSettingsReader.getValidShiftLayer(shiftLayerSetting) ?: return coordinate

        return coordinate.subtract(shiftLayer.displaySettings.displacement)
    }
}