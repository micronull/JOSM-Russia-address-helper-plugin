package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable

@Serializable
data class NSPDProperties(
    val cadastralDistrictsCode: Int? = null,
    val category: Int? = null,
    val categoryName: String? = null,
    val descr: String? = null,
    val externalKey: String? = null,
    val interactionId: Int? = null,
    val label: String? = null,
    val options: NSPDOptions? = NSPDOptions(),
    val subcategory: Int? = null,
    val systemInfo: NSPDSystemInfo? = NSPDSystemInfo()
) {
    fun getExtTags(prefix: String = "nspd:", filter: Set<String> = setOf()): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        if (!descr.isNullOrBlank() && !filter.contains("descr")) {
            result[prefix + "descr"] = descr
        }
        if (!categoryName.isNullOrBlank() && !filter.contains("categoryName")) {
            result[prefix + "categoryName"] = categoryName
        }

        if (!label.isNullOrBlank() && !filter.contains("label")) {
            result[prefix + "label"] = label
        }
        val optionsTags = options?.getExtTags(prefix, filter)
        if (!optionsTags.isNullOrEmpty()) {
            result.putAll(optionsTags)
        }

        return result
    }
}

@Serializable
data class NSPDOptions(
    //options for land plot
    @JsonProperty("cad_num") val cadNum: String? = null,
    @JsonProperty("cost_application_date") val costApplicationDate: String? = null,
    @JsonProperty("cost_approvement_date") val costApprovementDate: String? = null,
    @JsonProperty("cost_determination_date") val costDeterminationDate: String? = null,
    @JsonProperty("cost_index") val costIndex: Double? = null,
    @JsonProperty("cost_registration_date") val costRegistrationDate: String? = null,
    @JsonProperty("cost_value") val costValue: Double? = null,
    @JsonProperty("determination_couse") val determinationCouse: String? = null,
    @JsonProperty("land_record_category_type") val landRecordCategoryType: String? = null,
    @JsonProperty("land_record_reg_date") val landRecordRegDate: String? = null,
    @JsonProperty("land_record_subtype") val landRecordSubtype: String? = null,
    @JsonProperty("land_record_type") val landRecordType: String? = null,
    @JsonProperty("ownership_type") val ownershipType: String? = null,
    @JsonProperty("permitted_use_established_by_document") val permittedUseEstablishedByDocument: String? = null,
    @JsonProperty("quarter_cad_number") val quarterCadNumber: String? = null,
    @JsonProperty("readable_address") val readableAddress: String? = null,
    @JsonProperty("specified_area") val specifiedArea: String? = null,
    @JsonProperty("status") val status: String? = null,
    //options for buildings
    @JsonProperty("build_record_area") val buildRecordArea: Double? = null,
    @JsonProperty("build_record_registration_date") val buildRecordRegistrationDate: String? = null,
    @JsonProperty("build_record_type_value") val buildRecordTypeValue: String? = null,
    @JsonProperty("building_name") val buildingName: String? = null,
    @JsonProperty("cultural_heritage_val") val culturalHeritageVal: String? = null,
    @JsonProperty("floors") val floors: String? = null,
    @JsonProperty("intersected_cad_numbers") val intersectedCadNumbers: String? = null,
    @JsonProperty("materials") val materials: String? = null,
    @JsonProperty("permitted_use_name") val permittedUseName: String? = null,
    @JsonProperty("purpose") val purpose: String? = null,
    @JsonProperty("underground_floors") val undergroundFloors: String? = null,
    @JsonProperty("united_cad_numbers") val unitedCadNumbers: String? = null,
    @JsonProperty("year_built") val yearBuilt: String? = null,
    @JsonProperty("year_commisioning") val yearCommissioning: String? = null,
    @JsonProperty("right_type") val rightType: String? = null

) {
    fun getExtTags(prefix: String = "nspd:", filter: Set<String> = setOf()): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        if (!cadNum.isNullOrBlank() && !filter.contains("cad_num")) {
            result[prefix + "cad_num"] = cadNum
        }
        if (!floors.isNullOrBlank() && !filter.contains("levels")) {
            val undergroundLevels: Int = undergroundFloors?.toIntOrNull() ?: 0
            val levels: Int = floors.toIntOrNull() ?: 0
            var resultLevels: String = floors
            if (levels > 0) {
                resultLevels = (levels - undergroundLevels).toString()
            }
            result[prefix + "levels"] = resultLevels
        }
        if (!materials.isNullOrBlank() && !filter.contains("materials")) {
            result[prefix + "materials"] = materials
        }
        if (!purpose.isNullOrBlank() && !filter.contains("purpose")) {
            result[prefix + "purpose"] = purpose
        }
        if (!buildRecordTypeValue.isNullOrBlank() && !filter.contains("buildRecordTypeValue")) {
            result[prefix + "buildRecordTypeValue"] = buildRecordTypeValue
        }

        if (!permittedUseEstablishedByDocument.isNullOrBlank() && !filter.contains("permittedUseEstablishedByDocument")) {
            result[prefix + "permittedUseEstablishedByDocument"] = permittedUseEstablishedByDocument
        }

        if (!permittedUseName.isNullOrBlank() && !filter.contains("permittedUseName")) {
            result[prefix + "permittedUseName"] = permittedUseName
        }

        if (!ownershipType.isNullOrBlank() && !filter.contains("ownershipType")) {
            result[prefix + "ownershipType"] = ownershipType
        }

        if (!yearBuilt.isNullOrBlank() && !filter.contains("year_built")) {
            result[prefix + "year_built"] = yearBuilt

        }

        if (!yearCommissioning.isNullOrBlank() && !filter.contains("year_comissioning")) {
            result[prefix + "year_comissioning"] = yearCommissioning
        }

        return result
    }
}

@kotlinx.serialization.Serializable
data class NSPDSystemInfo(
    @JsonProperty("inserted") var inserted: String? = null,
    @JsonProperty("insertedBy") var insertedBy: String? = null,
    @JsonProperty("updated") var updated: String? = null,
    @JsonProperty("updatedBy") var updatedBy: String? = null
)


