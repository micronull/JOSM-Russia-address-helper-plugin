package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io

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
            "https://pkk.rosreestr.ru/api/features/?text={lat}%20{lon}&tolerance=1&types=[1]"
        )

        /**
         * Property for limiting concurrent requests.
         * @since 0.0.1
         */
        val REQUEST_LIMIT = IntegerProperty("dl.russiaaddresshelper.ppk.request.limit", 1)

        /**
         * Property for delay between requests in seconds.
         * @since 0.1.3
         */
        val REQUEST_DELAY = IntegerProperty("dl.russiaaddresshelper.ppk.request.delay", 3)
    }
}