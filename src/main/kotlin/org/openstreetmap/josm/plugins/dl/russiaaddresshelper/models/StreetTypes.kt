package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

@Serializable data class StreetTypes(val types: List<StreetType>) {
    companion object {
        /**
         * Парсим yml файл и возвращаем экземпляр класса StreetTypes
         *
         * @param finePath путь до yml файла
         */
        fun byYml(finePath: String): StreetTypes {
            val rawStr = this::class.java.getResource(finePath)!!.readText()

            return Yaml.default.decodeFromString(serializer(), rawStr)
        }
    }
}