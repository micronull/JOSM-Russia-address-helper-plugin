package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.DoubleProperty
import org.openstreetmap.josm.data.preferences.IntegerProperty
import org.openstreetmap.josm.data.preferences.StringProperty

class EgrnSettingsReader {
    companion object {
        /**
         * Property for current EGRN server.
         * @since 0.0.1
         */
        val EGRN_URL_REQUEST = StringProperty(
            "dl.russiaaddresshelper.ppk.url",
            "https://pkk.rosreestr.ru/api/features/?text={lat}%20{lon}&tolerance=1&types=[{type}]"
        )

        /**
         * Property for limiting concurrent requests.
         * @since 0.0.1
         */
        val REQUEST_LIMIT = IntegerProperty("dl.russiaaddresshelper.ppk.request.limit", 2)

        /**
         * Property for delay between requests in seconds.
         * @since 0.1.3
         */
        val REQUEST_DELAY = IntegerProperty("dl.russiaaddresshelper.ppk.request.delay", 1)

        /**
         * Property for request timeout in milliseconds
         * @since 0.9.6.4
         */
        val REQUEST_TIMEOUT = IntegerProperty("dl.russiaaddresshelper.ppk.request.timeout", 3000)

        val EGRN_REQUEST_USER_AGENT = StringProperty(
            "dl.russiaaddresshelper.ppk.useragent",
            "JOSM/%s JOSM-RussiaAddressHelper/%s"
        )

        val EGRN_DISABLE_SSL_FOR_REQUEST = BooleanProperty( "dl.russiaaddresshelper.ppk.disable_ssl",
            true
        )

        /**
         * Property for limiting selection size for mass request.
         * @since 0.8.5.7
         */
        val REQUEST_LIMIT_PER_SELECTION = IntegerProperty("dl.russiaaddresshelper.ppk.requestselection.limit", 100)

        val NSPD_GET_FEATURE_REQUEST_URL = StringProperty(
            "dl.russiaaddresshelper.nspd.getfeature.url",
            "https://nspd.gov.ru/api/aeggis/v3/{layer}/wms?REQUEST=GetFeatureInfo&QUERY_LAYERS={layer}&SERVICE=WMS&VERSION=1.3.0&FORMAT=image/png&STYLES=&TRANSPARENT=true&LAYERS={layer}&INFO_FORMAT=application/json&FEATURE_COUNT=10&I={x}&J={y}&WIDTH={width}&HEIGHT={height}&CRS=EPSG:3857&BBOX={minx},{miny},{maxx},{maxy}"
        )

        val NSPD_GET_MAP_REQUEST_URL = StringProperty(
            "dl.russiaaddresshelper.nspd.getfeature.url",
            "wms:{site}/api/aeggis/v3/{layer}/wms?REQUEST=GetMap&SERVICE=WMS&VERSION=1.3.0&FORMAT=image/png&STYLES=&TRANSPARENT=true&LAYERS={layer}&WIDTH={width}&HEIGHT={height}&CRS={proj}&BBOX={bbox}"
        )

        val NSPD_SITE_URL = StringProperty(
            "dl.russiaaddresshelper.nspd.getfeature.site_url",
            "https://nspd.gov.ru"
        )

        val LOCALHOST_PROXY_URL = StringProperty(
            "dl.russiaaddresshelper.nspd.getfeature.localhost_proxy",
            "http://localhost:8081"
        )

        /**
         * @since 0.9.4
         * Coefficient to calculate request pixel resolution from boundaries. Pixels per meter
         */

        val REQUEST_PIXEL_RESOLUTION = DoubleProperty("dl.russiaaddresshelper.nspd.request_pixel_resolution", 5.0)

        /**
         * @since 0.9.4
         * When request is made with boundary of object known, add these margins
         */
        val REQUEST_BOUNDS_MARGIN = IntegerProperty("dl.russiaaddresshelper.nspd.request_boundary_margin", 30)
    }
}