package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable
import org.openstreetmap.josm.tools.Logging
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    @JsonProperty("system_info") val systemInfo: NSPDSystemInfo? = NSPDSystemInfo()
) {
    fun getExtTags(prefix: String = "nspd:", filter: Set<String> = setOf()): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()

        if (!categoryName.isNullOrBlank() && !filter.contains("categoryName")) {
            result[prefix + "categoryName"] = categoryName.trim()
        }

        val optionsTags = options?.getExtTags(prefix, filter)
        if (!optionsTags.isNullOrEmpty()) {
            result.putAll(optionsTags)
        }

        if (!descr.isNullOrBlank() && !filter.contains("descr") && descr.compareTo(result[prefix+"cad_num"]?:"", true)!= 0) {
            result[prefix + "descr"] = descr.trim()
        }

        if (!label.isNullOrBlank() && !filter.contains("label") && label.compareTo(result[prefix+"cad_num"]?:"", true)!= 0 ) {
            result[prefix + "label"] = label.trim()
        }

        if (systemInfo != null && !filter.contains("created_at")) {
            val inserted = formatTimestamp(systemInfo.inserted)
            if (inserted != null) {
                result[prefix + "created_at"] = inserted
            }
            val updated = formatTimestamp(systemInfo.updated)
            if (updated != null && !filter.contains("updated_at")) {
                result[prefix + "updated_at"] = updated
            }

            if (systemInfo.version != null && !filter.contains("version")) {
                result[prefix + "version"] = systemInfo.version.toString()
            }
        }

        return result
    }

    private fun formatTimestamp(timestamp: String?): String? {
        if (timestamp == null) return null
        try {
            val parsedDateTime = LocalDateTime.parse(timestamp)
            return parsedDateTime.format(DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss"))
        } catch (ex : DateTimeException) {
            Logging.error("EGRN PLUGIN recieved malformed timestamp: $timestamp")
        }
        return null
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
    @JsonProperty("readable_address") private val readableAddress: String? = null,
    @JsonProperty("specified_area") val specifiedArea: String? = null,
    @JsonProperty("status") val status: String? = null,
    //options for buildings
    @JsonProperty("build_record_area") val buildRecordArea: Double? = null,
    @JsonProperty("build_record_registration_date") val buildRecordRegistrationDate: String? = null,
    @JsonProperty("build_record_type_value") val buildRecordTypeValue: String? = null,
    @JsonProperty("building_name") val buildingName: String? = null,
    @JsonProperty("cultural_heritage_val") val culturalHeritageVal: String? = null,
    @JsonProperty("floors") val floors: String? = null,
    @JsonProperty("intersected_cad_numbers")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val intersectedCadNumbers: ArrayList<String>? = null, //точный тип неизвестен
    @JsonProperty("materials") val materials: String? = null,
    @JsonProperty("permitted_use_name") val permittedUseName: String? = null,
    @JsonProperty("purpose") val purpose: String? = null,
    @JsonProperty("underground_floors") val undergroundFloors: String? = null,
    @JsonProperty("united_cad_numbers")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val unitedCadNumbers: ArrayList<String>? = null, //может быть как String, так и Array<String>
    @JsonProperty("year_built") val yearBuilt: String? = null,
    @JsonProperty("year_commisioning") val yearCommissioning: String? = null,
    @JsonProperty("right_type") val rightType: String? = null,
    //options for place_boundaries
    @JsonProperty("name") val name: String? = null, //заполняется и у объектов незавершенного строительства
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("loc") val loc: String? = null,
    @JsonProperty("document_date") val documentDate: String? = null,
    @JsonProperty("document_name") val documentName: String? = null,
    //options for constructions
    @JsonProperty("address_readable_address") private val constructReadableAddress: String? = null,
    @JsonProperty("params_name") val constructName: String? = null,
    @JsonProperty("params_purpose") val constructPurpose: String? = null,
    @JsonProperty("params_underground_floors") val constructUndergroundFloors: String? = null,
    @JsonProperty("params_floors") val constructFloors: String? = null,
    @JsonProperty("params_height") val constructHeight: String? = null, //пока не встречал значения !=0
    @JsonProperty("params_depth") val constructDepth: String? = null, //пока не встречал значения !=0
    @JsonProperty("params_year_commisioning") val constructYearCommissioning: String? = null,
    @JsonProperty("params_year_built") val constructYearBuilt: String? = null,


    ) {
    fun getAnyReadableAddress(): String? {
        if (!readableAddress.isNullOrBlank()) return readableAddress
        if (!constructReadableAddress.isNullOrBlank()) return constructReadableAddress
        return null
    }

    fun getExtTags(prefix: String = "nspd:", filter: Set<String> = setOf()): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        if (!cadNum.isNullOrBlank() && !filter.contains("cad_num")) {
            result[prefix + "cad_num"] = cadNum.trim()
        }
        var resultUndergroundFloors: Int? = null
        if (!undergroundFloors.isNullOrBlank() && !filter.contains("underground_floors")) {
            result[prefix + "underground_floors"] = undergroundFloors
            resultUndergroundFloors = undergroundFloors.toIntOrNull()
        } else if (!constructUndergroundFloors.isNullOrBlank() && !filter.contains("underground_floors")) {
            result[prefix + "underground_floors"] = constructUndergroundFloors
            resultUndergroundFloors = constructUndergroundFloors.toIntOrNull()
        }
        var resultFloors : Int? = null
        if (!floors.isNullOrBlank() && !filter.contains("floors")) {
            result[prefix + "floors"] = floors
            resultFloors = floors.toIntOrNull()
        } else if (!constructFloors.isNullOrBlank() && !filter.contains("underground_floors")) {
            result[prefix + "underground_floors"] = constructFloors
            resultFloors = constructFloors.toIntOrNull()
        }

        val calculatedLevels : Int = (resultFloors?: 0) - (resultUndergroundFloors?:0)

        if (calculatedLevels > 0 && !filter.contains("levels")) {
            result[prefix + "levels"] = calculatedLevels.toString()
        }

        if (!materials.isNullOrBlank() && !filter.contains("materials")) {
            result[prefix + "materials"] = materials.trim()
        }
        if (!purpose.isNullOrBlank() && !filter.contains("purpose")) {
            result[prefix + "purpose"] = purpose.trim()
        }
        if (!buildRecordTypeValue.isNullOrBlank() && !filter.contains("buildRecordTypeValue")) {
            result[prefix + "buildRecordTypeValue"] = buildRecordTypeValue.trim()
        }

        if (!permittedUseEstablishedByDocument.isNullOrBlank() && !filter.contains("permittedUseEstablishedByDocument")) {
            result[prefix + "permittedUseEstablishedByDocument"] = permittedUseEstablishedByDocument.trim()
        }

        if (!permittedUseName.isNullOrBlank() && !filter.contains("permittedUseName")) {
            result[prefix + "permittedUseName"] = permittedUseName.trim()
        }

        if (!ownershipType.isNullOrBlank() && !filter.contains("ownershipType")) {
            result[prefix + "ownershipType"] = ownershipType.trim()
        }

        if (!yearBuilt.isNullOrBlank() && !filter.contains("year_built")) {
            result[prefix + "year_built"] = yearBuilt.trim()

        }

        if (!constructYearBuilt.isNullOrBlank() && !filter.contains("year_built")) {
            result[prefix + "construct_year_built"] = constructYearBuilt.trim()
        }

        if (!yearCommissioning.isNullOrBlank() && !filter.contains("year_comissioning")) {
            result[prefix + "year_comissioning"] = yearCommissioning.trim()
        }

        if (!constructYearCommissioning.isNullOrBlank() && !filter.contains("year_comissioning")) {
            result[prefix + "construct_year_comissioning"] = constructYearCommissioning.trim()
        }

        if (!buildingName.isNullOrBlank() && !filter.contains("building_name")) {
            result[prefix + "building_name"] = buildingName.trim()
        }

        if (!constructName.isNullOrBlank() && !filter.contains("construct_name")) {
            result[prefix + "construct_name"] = constructName.trim()
        }

        if (!constructPurpose.isNullOrBlank() && !filter.contains("construct_purpose")) {
            result[prefix + "construct_purpose"] = constructPurpose.trim()
        }

        if (!culturalHeritageVal.isNullOrBlank() && !filter.contains("cultural_heritage")) {
            result[prefix + "cultural_heritage"] = culturalHeritageVal.trim()
        }

        return result
    }
}

@Serializable
data class NSPDSystemInfo(
    @JsonProperty("inserted") val inserted: String? = null,
    @JsonProperty("inserted_by") val insertedBy: String? = null,
    @JsonProperty("updated") val updated: String? = null,
    @JsonProperty("updated_by") val updatedBy: String? = null,
    @JsonProperty("version_no") val version: Int? = null
)




