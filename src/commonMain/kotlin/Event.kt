import kotlinx.serialization.Serializable


@Serializable
data class Counts(val counts: Map<Int, Long> = mapOf()) {
    companion object {
        const val path = "/counts"
    }

    fun getRunIDs(): Set<Int> = counts.keys
}

@Serializable
data class EventIDs(val runID: Int, val eventID: List<Int>) {
    companion object {
        const val path = "/eventIDs"
    }
}

@Serializable
data class EventEnergyPerVolume(val runID: Int, val eventID: Int, val energyPerVolume: Map<String?, Double?>?) {
    companion object {
        const val path = "/eventEnergy"
    }
}

@Serializable
data class EventFull(
    val runID: Int,
    val eventID: Int,
    val trackID: List<Int>? = null,
    val stepID: List<Int>? = null,
    val trackParentID: List<Int>? = null,
    val volumeID: List<Int>? = null,
    val particleID: List<Int>? = null,
    val processID: List<Int>? = null,
    val x: List<Double>? = null,
    val y: List<Double>? = null,
    val z: List<Double>? = null,
    val eDep: List<Double>? = null,
    val kineticE: List<Double>? = null,
    val timeGlobal: List<Double>? = null,
    val volumeName: List<String>? = null,
    val processName: List<String>? = null,
    val particleName: List<String>? = null,
    val materialName: List<String>? = null,
    val px: List<Double>? = null,
    val py: List<Double>? = null,
    val pz: List<Double>? = null,
    val charge: List<Double>? = null,
    val steplength: List<Double>? = null,
    val tracklength: List<Double>? = null,
) {
    companion object {
        const val path = "/eventFull"
    }
}

@Serializable
data class Message(val volume: String, val energy: List<Double>) {

}