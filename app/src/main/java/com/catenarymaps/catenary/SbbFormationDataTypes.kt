package com.catenarymaps.catenary

import kotlinx.serialization.Serializable

@Serializable
data class SbbFormationData(
    val vehicleJourneyType: String? = null,
    val formations: List<SbbFormation> = emptyList(),
    val formationsAtScheduledStops: List<SbbFormationAtScheduledStop> = emptyList(),
    val lastUpdate: String? = null,
    val journeyMetaInformation: SbbJourneyMetaInformation? = null,
    val trainMetaInformation: SbbTrainMetaInformation? = null
)

@Serializable
data class SbbFormation(
    val formationVehicles: List<SbbFormationVehicle> = emptyList(),
    val metaInformation: SbbFormationMetaInformation? = null
)

@Serializable
data class SbbFormationVehicle(
    val formationVehicleAtScheduledStops: List<SbbFormationVehicleAtScheduledStop> = emptyList(),
    val number: Int? = null,
    val position: Int? = null,
    val vehicleIdentifier: SbbVehicleIdentifier? = null,
    val vehicleProperties: SbbVehicleProperties? = null
)

@Serializable
data class SbbFormationVehicleAtScheduledStop(
    val accessToPreviousVehicle: Boolean? = null,
    val sectors: String? = null,
    val stopPoint: SbbStopPoint? = null,
    val stopTime: SbbStopTime? = null,
    val track: String? = null
)

@Serializable
data class SbbStopPoint(
    val name: String? = null,
    val uic: Long? = null
)

@Serializable
data class SbbStopTime(
    val arrivalTime: String? = null,
    val departureTime: String? = null
)

@Serializable
data class SbbVehicleIdentifier(
    val buildTypeCode: String? = null,
    val checkNumber: String? = null,
    val countryCode: String? = null,
    val evn: String? = null,
    val parentEvn: String? = null,
    val typeCode: Int? = null,
    val typeCodeName: String? = null,
    val vehicleNumber: String? = null
)

@Serializable
data class SbbVehicleProperties(
    val accessibilityProperties: SbbAccessibilityProperties? = null,
    val bikePlatform: Boolean? = null,
    val climated: Boolean? = null,
    val closed: Boolean? = null,
    val emergencyCallSystem: Boolean? = null,
    val fromStop: SbbStopPoint? = null,
    val length: Double? = null,
    val lowFloorTrolley: Boolean? = null,
    val number1class: Int? = null,
    val number2class: Int? = null,
    val numberBeds: Int? = null,
    val numberBikeHooks: Int? = null,
    val numberRestaurantSpace: Int? = null,
    val pictoProperties: SbbPictoProperties? = null,
    val toStop: SbbStopPoint? = null,
    val trolleyStatus: String? = null,
    val vehicleWillBePutAway: Boolean? = null
)

@Serializable
data class SbbAccessibilityProperties(
    val disabledCompartment: Boolean? = null,
    val numberWheelchairSpaces: Int? = null,
    val numberWheelchairSpaces1class: Int? = null,
    val numberWheelchairSpaces2class: Int? = null,
    val wheelchairAccessibleRestaurant: Boolean? = null,
    val wheelchairToilet: Boolean? = null
)

@Serializable
data class SbbPictoProperties(
    val bikePicto: Boolean? = null,
    val businessZonePicto: Boolean? = null,
    val familyZonePicto: Boolean? = null,
    val strollerPicto: Boolean? = null,
    val wheelchairPicto: Boolean? = null
)

@Serializable
data class SbbJourneyMetaInformation(
    val SJYID: String? = null,
    val operationDate: String? = null
)

@Serializable
data class SbbTrainMetaInformation(
    val trainNumber: Int? = null,
    val toCode: String? = null,
    val runs: String? = null
)

@Serializable
data class SbbFormationMetaInformation(
    val length: Int? = null,
    val numberAxis: Int? = null,
    val numberSeats: Int? = null,
    val numberVehicles: Int? = null
)

@Serializable
data class SbbFormationAtScheduledStop(
    val formationShort: SbbFormationShort? = null,
    val scheduledStop: SbbScheduledStop? = null
)

@Serializable
data class SbbFormationShort(
    val formationShortString: String? = null,
    val vehicleGoals: List<SbbVehicleGoal> = emptyList()
)

@Serializable
data class SbbVehicleGoal(
    val destinationStopPoint: SbbStopPoint? = null,
    val fromVehicleAtPosition: Int? = null,
    val toVehicleAtPosition: Int? = null
)

@Serializable
data class SbbScheduledStop(
    val stopModifications: Int? = null,
    val stopPoint: SbbStopPoint? = null,
    val stopTime: SbbStopTime? = null,
    val stopType: String? = null,
    val track: String? = null
)
