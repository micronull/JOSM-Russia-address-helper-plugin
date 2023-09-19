package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

enum class EGRNTestCode(val code: Int) {
    EGRN_RETURNED_EMPTY_RESPONSE(8502), // не ошибка, инфо
    EGRN_NOT_PARSED_STREET_AND_PLACE(8503),
    EGRN_NOT_PARSED_HOUSENUMBER(8504),
    EGRN_NOT_MATCHED_OSM_STREET(8505),
    EGRN_HAS_MULTIPLE_VALID_ADDRESSES(8506),
    EGRN_VALID_ADDRESS_ADDED(8507), // не ошибка, инфо
    EGRN_STREET_FUZZY_MATCHING(8507),
    EGRN_STREET_MATCH_WITHOUT_INITIALS(8508),
    EGRN_ADDRESS_HAS_FLATS(8509),
    EGRN_NOT_MATCHED_OSM_PLACE(8510),
    EGRN_ADDRESS_DOUBLE_FOUND(8511),
    EGRN_PLACE_FUZZY_MATCHING(8512),
    EGRN_PLACE_MATCH_WITHOUT_INITIALS(8513);

    //распознанный адрес уже присутствует в ОСМ или в загруженных данных (по удаленности?)

    //нарушение порядка нумерации
    //надо различать пустой ответ и просто сбой связи (conn refused). Для этого случая надо повторять запрос.
    //сопоставление по вторичным тэгам, а не основным. выносить в предупреждения
    companion object {
        fun getByCode(code: Int) = values().firstOrNull { it.code == code }
    }
}