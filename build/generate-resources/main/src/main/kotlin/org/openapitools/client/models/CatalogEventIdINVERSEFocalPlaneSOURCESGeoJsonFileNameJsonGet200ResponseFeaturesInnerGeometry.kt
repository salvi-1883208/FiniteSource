/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package org.openapitools.client.models


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param type The type of the GeoJson geometry.
 * @param coordinates 
 */


data class CatalogEventIdINVERSEFocalPlaneSOURCESGeoJsonFileNameJsonGet200ResponseFeaturesInnerGeometry (

    /* The type of the GeoJson geometry. */
    @Json(name = "type")
    val type: kotlin.String? = null,

    @Json(name = "coordinates")
    val coordinates: kotlin.collections.List<kotlin.collections.List<kotlin.Double>>? = null

)

