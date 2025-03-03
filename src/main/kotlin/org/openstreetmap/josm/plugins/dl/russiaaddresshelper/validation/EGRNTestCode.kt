package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

enum class EGRNTestCode(val code: Int, val message: String) {
    EGRN_RETURNED_EMPTY_RESPONSE(8502, "EGRN returned empty response"), // не ошибка, инфо
    EGRN_NOT_PARSED_STREET_AND_PLACE(8503, "EGRN cant get street or place name"),
    EGRN_NOT_PARSED_HOUSENUMBER(8504, "EGRN cant get housenumber"),
    EGRN_NOT_MATCHED_OSM_STREET(8505, "EGRN street not found"),
    EGRN_HAS_MULTIPLE_VALID_ADDRESSES(8506, "EGRN multiple addresses"),
    EGRN_VALID_ADDRESS_ADDED(8507, "EGRN address found"), // не ошибка, инфо
    EGRN_STREET_FUZZY_MATCHING(8507, "EGRN fuzzy match"),
    EGRN_STREET_MATCH_WITHOUT_INITIALS(8508,"EGRN initials match"),
    EGRN_ADDRESS_HAS_FLATS(8509, "EGRN address has flats"),
    EGRN_NOT_MATCHED_OSM_PLACE(8510, "EGRN place not found"),
    EGRN_ADDRESS_DOUBLE_FOUND(8511, "EGRN double address"),
    EGRN_PLACE_FUZZY_MATCHING(8512, "EGRN place fuzzy match"),
    EGRN_PLACE_MATCH_WITHOUT_INITIALS(8513, "EGRN place initials match"),
    EGRN_STREET_FOUND_TOO_FAR(8514, "EGRN street found too far"),
    EGRN_PLACE_FOUND_TOO_FAR(8515, "EGRN place found too far"),
    EGRN_ADDRESS_NOT_INSIDE_PLACE_POLY(8516, "EGRN outside of place boundary"),
    EGRN_PLACE_BOUNDARY_INCOMPLETE(8517, "EGRN place boundary incomplete"),
    EGRN_CONFLICTED_DATA(8518, "EGRN conflicted data"),
    EGRN_CONTAINS_STOP_WORD(8519, "EGRN address contains stop words");

    //распознанный адрес уже присутствует в ОСМ или в загруженных данных (по удаленности?)

    //нарушение порядка нумерации
    //надо различать пустой ответ и просто сбой связи (conn refused). Для этого случая надо повторять запрос.
    //сопоставление по вторичным тэгам, а не основным. выносить в предупреждения
    companion object {
        fun getByCode(code: Int) = values().firstOrNull { it.code == code }
    }
}