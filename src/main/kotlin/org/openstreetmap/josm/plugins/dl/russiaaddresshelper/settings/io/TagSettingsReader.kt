package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.BooleanProperty
import org.openstreetmap.josm.data.preferences.ListProperty
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.model.MapStringListProperty

class TagSettingsReader {
    companion object {

        val EGRN_BUILDING_TYPES_SETTINGS = MapStringListProperty(
            "dl.russiaaddresshelper.tag.building_types_settings",
            mapOf(
                "apartments" to listOf<String>("многоквартир"),
                "school" to listOf<String>("школа", "школьное", "лицей", "гимназия"),
                "house" to listOf<String>("жилой дом"),
                "kindergarten" to listOf<String>("дошкольн", "ДДУ", "детский сад"),
                "commercial" to listOf<String>(
                    "торговый комплекс", "торговый центр",
                    "коммерческий комплекс"
                ),
                "retail" to listOf<String>("магазин"),
                "garage" to listOf<String>("гараж"),
                "chapel" to listOf<String>("часовня"),
                "parking" to listOf<String>("паркинг","автостоянка","парковка"),
                "dormitory" to listOf<String>("общежити"),
            )
        )

        val ADDRESS_STOP_WORDS = ListProperty(
            "dl.russiaaddresshelper.tag.stop_words_list",
            listOf("вне границ", "направлению", "на север", "на юг", "на запад", "на восток", "в районе", "вблизи",
                "прилегающий к", "за пределами", "примыкает к", "за границами", "позиция", "поз." )
        )

        /**
         * @since 0.9.6.4
         * Enables overwrite for housenumber, street and place even if they already exist
         */
        val OVERWRITE_ADDRESS = BooleanProperty("dl.russiaaddresshelper.tag.force_overwrite_address", true)

        /**
         * @since 0.9.7.3
         * Enables calculation of building:levels = floors - underground floors.
         */
        val CALCULATE_LEVELS = BooleanProperty("dl.russiaaddresshelper.tag.force_overwrite_address", false)

        /**
         * @since 0.9.7.5
         * Enables parsing of cultural_heritage tag
         */
        val PARSE_CULTURAL_HERITAGE = BooleanProperty("dl.russiaaddresshelper.tag.parse_cultural_heritage", true)
    }
}