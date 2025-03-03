package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

@Serializable data class AddressParts(val parts: List<AddressPartMatcher>) {
    companion object {
        /**
         * Парсим yml файл и возвращаем экземпляр класса AddressParts
         *
         * @param finePath путь до yml файла
         */
        fun byYml(finePath: String): AddressParts {
            val rawStr = this::class.java.getResource(finePath)!!.readText()

            return Yaml.default.decodeFromString(serializer(), rawStr)
        }
    }
}