package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io

import org.openstreetmap.josm.data.preferences.IntegerProperty
import org.openstreetmap.josm.data.preferences.StringProperty

class EgrnReader {
    companion object {
        /**
         * Property for current EGRN server.
         * @since 0.0.1
         */
        val EGRN_URL_REQUEST = StringProperty(
            "dl.russiaaddresshelper.egrn.url",
            "https://pkk.rosreestr.ru/api/features/?text={lat}%20{lon}&tolerance=1&types=[1]"
        )

        /**
         * Property for limiting concurrent requests.
         * @since 0.0.1
         */
        val REQUEST_LIMIT = IntegerProperty("dl.russiaaddresshelper.egrn.request.limit", 5)
    }
}