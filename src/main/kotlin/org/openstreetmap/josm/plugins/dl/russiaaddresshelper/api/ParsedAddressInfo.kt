package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress

data class ParsedAddressInfo(
    val addresses: List<ParsedAddress>
) {

    fun getPreferredAddress(): ParsedAddress? {
        val validAddresses = this.getValidAddresses()
        if (validAddresses.isEmpty()) {
            return null
        }
        return validAddresses.find { it.flags.contains(ParsingFlags.IS_BUILDING) } ?: validAddresses.first()
    }

    fun getValidAddresses(): List<ParsedAddress> {
        return this.addresses.filter { it.isValidAddress() }
    }

    fun getNonValidAddresses(): List<ParsedAddress> {
        return this.addresses.filter { !it.isValidAddress() }
    }
}

enum class ParsingFlags {
    IS_BUILDING, //адрес здания, иначе участка
    CANNOT_EXTRACT_STREET_NAME, //не удалось извлечь имя улицы из ЕГРН адреса, аналог
    STREET_NAME_FUZZY_MATCH, //название улицы совпало нечетко
    STREET_NAME_INITIALS_MATCH, // совпало после удаления инициалов
    MATCHED_STREET_BY_SECONDARY_TAGS, //название улицы совпало по вторичным, альтернативным именам
    CANNOT_FIND_STREET_OBJECT_IN_OSM, //удалось разобрать адрес из ЕГРН, но не получилось сопоставить с улицей в ОСМ
    STREET_HAS_NUMBERED_NAME, //улица пронумерована
    CANNOT_FIND_PLACE_TYPE, //ЕГРН адрес не совпал ни с одним регекспом для имени места
    CANNOT_FIND_PLACE_OBJECT_IN_OSM, //удалось разобрать адрес из ЕГРН, но в данных ОСМ не нашлось соответствующего обьекта
    UNSUPPORTED_ADDRESS_TYPE, //адрес разобран но, результат не может быть (автоматически) назначен обьекту ОСМ
    PLACE_HAS_NUMBERED_NAME,
    PLACE_NAME_INITIALS_MATCH,
    PLACE_NAME_FUZZY_MATCH,
    HOUSENUMBER_HAS_FLATS, //в адресе присутствуют номера квартир
    HOUSENUMBER_CANNOT_BE_PARSED, //номер не распознан, его скорее всего нет совсем
    HOUSENUMBER_CANNOT_BE_PARSED_BUT_CONTAINS_NUMBERS, //не удалось распознать номер дома, но можно попробовать руками
    HOUSENUMBER_TOO_BIG, //регулярка распознала цифры, но их слишком много для номера дома



}