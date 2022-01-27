package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.github.kittinunf.result.failure
import com.github.kittinunf.result.success
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.apache.commons.text.StringEscapeUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.openstreetmap.josm.JOSMFixture
import org.openstreetmap.josm.data.coor.LatLon
import org.openstreetmap.josm.data.projection.Projections
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.testutils.ResourceFileLoader
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.net.HttpURLConnection


internal class EgrnApiTest {
    @RegisterExtension val wmRule: WireMockExtension = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build()

    @BeforeEach fun setUpBeforeClass() {
        JOSMFixture("src/test/config/unit-josm.home").init()
    }

    @Test fun test() {
        val mockBody = ResourceFileLoader.getResourceBytes(EgrnApiTest::class.java, "response/ekb_lenina_1.json")

        wmRule.stubFor(
            WireMock.get("/?coordinates=56.8378735%2060.5802096&foo=1").withHeader("Accept", WireMock.equalTo("application/json")).willReturn(
                WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(mockBody)
            )
        )

        val mercator = Projections.getProjectionByCode("EPSG:3857")
        val en = mercator.latlon2eastNorth(LatLon(56.83787347564765, 60.58020958387835))
        val url = wmRule.baseUrl() + "/?coordinates={lat} {lon}&foo=1"
        val (_, response, result) = EgrnApi(url, "foo-user-agent").request(en).responseString()

        Assertions.assertEquals(HttpURLConnection.HTTP_OK, response.statusCode)

        result.success {
            val body = StringEscapeUtils.unescapeJson(it)
            JSONAssert.assertEquals("{results:[{attrs:{address:\"обл. Свердловская, г. Екатеринбург, ул. Ленина, дом 1\"}}]}", body, JSONCompareMode.LENIENT);
        }

        result.failure {
            Assertions.fail()
        }
    }
}