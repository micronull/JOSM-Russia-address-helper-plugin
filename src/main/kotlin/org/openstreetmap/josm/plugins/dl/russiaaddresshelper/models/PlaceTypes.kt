package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

@Serializable data class PlaceTypes(val types: List<PlaceType>) {
    companion object {
        /**
         * Парсим yml файл и возвращаем экземпляр класса PlaceTypes
         *
         * @param finePath путь до yml файла
         */
        fun byYml(finePath: String): PlaceTypes {
            val rawStr = this::class.java.getResource(finePath)!!.readText()

            return Yaml.default.decodeFromString(serializer(), rawStr)
        }
    }
}