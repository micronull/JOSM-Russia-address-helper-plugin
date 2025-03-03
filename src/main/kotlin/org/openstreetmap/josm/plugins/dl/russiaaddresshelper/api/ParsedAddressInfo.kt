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
        return this.addresses.filter { it.isMatchedByStreetOrPlace() && !it.flags.contains(ParsingFlags.STOP_LIST_WORDS) }
    }

    fun getDistinctValidAddresses(ignoreFlats: Boolean): List<ParsedAddress> {
        return this.getValidAddresses()
            .distinctBy { it.getOsmAddress().getInlineAddress(",", ignoreFlats) }
    }

    fun getNonValidAddresses(): List<ParsedAddress> {
        return this.addresses.filter { !it.isMatchedByStreetOrPlace() || it.flags.contains(ParsingFlags.STOP_LIST_WORDS) }
    }

    fun canAssignAddress(): Boolean {
        if (this.getDistinctValidAddresses(true).size != 1) return false //multiple valid addresses or no valid address
        if (this.addresses.size != 1) { //has 1 valid address and more non-valid
            val nonValidAddresses = this.getNonValidAddresses()
            if (nonValidAddresses.any { nonValidAddressCanBeFixed(it) }) return false //do we have any non-valid but potentially fixable?
        }
        val preferredAddress = this.getPreferredAddress()
        if (preferredAddress != null) {
            return checkValidAddress(preferredAddress)
        }
        return false
    }

    private fun checkValidAddress(address: ParsedAddress): Boolean {
        return !address.flags.contains(ParsingFlags.STOP_LIST_WORDS) &&
                !address.flags.contains(ParsingFlags.STREET_NAME_FUZZY_MATCH) &&
                !address.flags.contains(ParsingFlags.STREET_NAME_INITIALS_MATCH) &&
                !address.flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM) &&
                !((address.flags.contains(ParsingFlags.PLACE_NAME_INITIALS_MATCH) || address.flags.contains(
                    ParsingFlags.PLACE_NAME_FUZZY_MATCH
                ))
                        && !address.getOsmAddress().isFilledStreetAddress())
    }

    private fun nonValidAddressCanBeFixed(address: ParsedAddress): Boolean {
        return (address.flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM) ||
                address.flags.contains(ParsingFlags.CANNOT_FIND_PLACE_OBJECT_IN_OSM))
                && isHouseNumberValid(address)
    }

    private fun isHouseNumberValid(address: ParsedAddress): Boolean {
        return !address.flags.contains(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED) &&
                !address.flags.contains(ParsingFlags.HOUSENUMBER_TOO_BIG)
                && !address.flags.contains(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED_BUT_CONTAINS_NUMBERS)
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
    STOP_LIST_WORDS, //В адресе из егрн присутствуют стоп-слова


}