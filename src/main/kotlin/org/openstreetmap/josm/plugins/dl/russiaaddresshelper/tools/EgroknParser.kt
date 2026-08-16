package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.tools.Logging

data class EgroknData(
    val regionCode: String,           // СС - код субъекта РФ
    val year: String,                 // ГГ - год внесения записи
    val objectType: String,           // В - вид объекта
    val objectTypeName: String,       // Название вида объекта
    val serialNumber: String,         // ХХХХХХ - порядковый номер
    val objectComposition: String,    // ШШ - пообъектный состав
    val category: String,             // К - категория значения
    val categoryName: String,         // Название категории
    val fullNumber: String            // Полный номер

) {
    private val egroknToOsmLevelMapping = mapOf(
        "4" to "6",
        "5" to "4",
        "6" to "2"
    )

    fun getOsmHeritageCategory() : String {
        return egroknToOsmLevelMapping[category] ?: "yes"
    }
}

object EgroknParser {

    // Справочник видов объектов
    private val objectTypes = mapOf(
        '1' to "Памятник",
        '2' to "Ансамбль",
        '3' to "Достопримечательное место",
        '4' to "Объект археологического наследия"
    )

    // Справочник категорий значения
    private val categories = mapOf(
        '4' to "Местного (муниципального) значения",
        '5' to "Регионального значения",
        '6' to "Федерального значения",
        '7' to "Включенный в Список всемирного наследия",
        '8' to "Федерального значения, признанный особо ценным объектом культурного наследия народов РФ",
        '9' to "Федерального значения, признанный особо ценным и включенный в Список всемирного наследия"
    )

    fun parse(egroknCode: String): EgroknData {
        // Валидация: длина 15 символов и все цифры
        require(egroknCode.length == 15) {
            "Код ЕГРОКН должен содержать ровно 15 цифр, получено: ${egroknCode.length}"
        }
        require(egroknCode.all { it.isDigit() }) {
            "Код ЕГРОКН должен состоять только из цифр"
        }

        val regionCode = egroknCode.substring(0, 2)        // СС
        val year = egroknCode.substring(2, 4)              // ГГ
        val objectTypeChar = egroknCode[4]                 // В
        val serialNumber = egroknCode.substring(5, 11)     // ХХХХХХ
        val composition = egroknCode.substring(11, 13)     // ШШ
        val categoryChar = egroknCode[14]                  // К

        val objectTypeName = objectTypes[objectTypeChar]
            ?: throw IllegalArgumentException("Неизвестный вид объекта: '$objectTypeChar'")

        val categoryName = categories[categoryChar]
            ?: throw IllegalArgumentException("Неизвестная категория: '$categoryChar'")

        return EgroknData(
            regionCode = regionCode,
            year = year,
            objectType = objectTypeChar.toString(),
            objectTypeName = objectTypeName,
            serialNumber = serialNumber,
            objectComposition = composition,
            category = categoryChar.toString(),
            categoryName = categoryName,
            fullNumber = egroknCode
        )
    }


    /**
     * Безопасная версия, возвращает null при ошибке
     */
    fun parseOrNull(egroknCode: String): EgroknData? {
        return try {
            parse(egroknCode)
        } catch (e: IllegalArgumentException) {
            Logging.warn("EGRN PLUGIN: Heritage index value $egroknCode cannot be parsed: " + e.message )
            null
        }
    }


}