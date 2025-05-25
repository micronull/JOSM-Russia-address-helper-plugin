package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.ListProperty
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model.MapStringListProperty

class TagSettingsReader {
    companion object {
        /**
         * @since 0.2
         */
        val EGRN_ADDR_RECORD = BooleanProperty("dl.russiaaddresshelper.tag.egrn_addr_record", true)

        val EGRN_BUILDING_TYPES_SETTINGS = MapStringListProperty(
            "dl.russiaaddresshelper.tag.building_types_settings",
            mapOf(
                "apartments" to listOf<String>("многоквартир"),
                "school" to listOf<String>("школа", "школьное", "лицей", "гимназия"),
                "house" to listOf<String>("жилой дом"),
                "kindergarten" to listOf<String>("дошкольное", "ДДУ", "детский сад", "дошкольная"),
                "commercial" to listOf<String>(
                    "торговый комплекс", "торговый центр",
                    "коммерческий комплекс"
                ),
                "retail" to listOf<String>("магазин"),
                "garage" to listOf<String>("гараж"),
                "chapel" to listOf<String>("часовня"),
            )
        )

        val ADDRESS_STOP_WORDS = ListProperty(
            "dl.russiaaddresshelper.tag.stop_words_list",
            listOf("вне границ", "направлению", "на север", "на юг", "на запад", "на восток", "в районе", "вблизи", "прилегающий к", "за пределами")
        )

        /**
         * @since 0.9.6.4
         * Enables overwrite for housenumber, street and place even if they already exist
         */
        val OVERWRITE_ADDRESS = BooleanProperty("dl.russiaaddresshelper.tag.force_overwrite_address", true)
    }
}