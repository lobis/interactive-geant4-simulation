import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.random.ISAACRandom
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.max

internal object Summary : Table() {
    val runID = integer("runid")
    val counts = integer("counts")
}

internal fun initDB() {
    try {
        val host: String = System.getenv("POSTGRES_HOST") ?: "localhost"
        val password: String = System.getenv("POSTGRES_PASSWORD") ?: "defaultpassword"

        val url = "jdbc:postgresql://$host:5432/postgres"
        Database.connect(url = url, user = "postgres", password = "$password")
        println("Successful connection to DB! ($url)")

    } catch (e: Exception) {
        println("$e, Exception in 'initDB', Probably host not connected")
    }
}

fun clearDatabase() {
    try {
        transaction {
            Events.deleteAll()
            Summary.deleteAll()
        }
    } catch (e: Exception) {
        println("Exception on server-side 'clearDatabase': $e")
    }
}

fun retrievePositionsAndEnergies(runID: Int, volume: String? = null): Map<String, List<Double>> {
    val result = mutableMapOf<String, MutableList<Double>>()
    result["x"] = mutableListOf<Double>()
    result["y"] = mutableListOf<Double>()
    result["z"] = mutableListOf<Double>()
    result["eDep"] = mutableListOf<Double>()

    try {
        transaction {
            Events.select { Events.runID eq runID }.forEach {
                result["x"]?.add(it[Events.x])
                result["y"]?.add(it[Events.y])
                result["z"]?.add(it[Events.z])
                result["eDep"]?.add(it[Events.eDep])
            }
        }
    } catch (e: Exception) {
        println("Exception on 'retrievePositionsAndEnergies': $e")
    }
    return result
}

fun retrieveCounts(): Counts {
    var result: Counts = Counts(mutableMapOf<Int, Int>())
    try {
        transaction {
            val queryResult = Summary.selectAll().orderBy(Summary.runID to SortOrder.ASC)
                .map { it[Summary.runID] to it[Summary.counts] }
            val m = mutableMapOf<Int, Int>()
            queryResult.map {
                m[it.first] = it.second
            }
            result = Counts(m)
        }
    } catch (e: Exception) {
        println("Exception on 'retrieveCounts': $e")
    }
    return result
}

fun retrieveNumberOfEventIDs(runIDsRequested: List<Int>? = null): Long {
    var result: Long = 0
    try {
        transaction {
            if (runIDsRequested == null) {
                // return all eventID, need to count twice same event ID if it has different runID
                result =
                    Events.slice(Events.eventID, Events.runID).selectAll().withDistinct().count()
            }
            result =
                Events.slice(Events.eventID).select { Events.runID inList runIDsRequested!! }.withDistinct().count()
        }
    } catch (e: Exception) {
        println("Exception on 'retrieveEventIDs': $e")
    }
    return result
}

fun retrieveNumberOfEventIDs(runID: Int): Long {
    return retrieveNumberOfEventIDs(listOf(runID))
}

fun retrieveEventIDs(runID: Int = 0): List<Int>? {
    var result: List<Int>? = null
    try {
        transaction {
            result =
                Events.slice(Events.eventID).select { Events.runID eq runID }.withDistinct().map { it[Events.eventID] }
        }
    } catch (e: Exception) {
        println("Exception on 'retrieveEventIDs': $e")
    }
    return result
}

fun retrieveVolumeNames(runID: Int = 0): List<String>? {
    var result: List<String>? = null
    try {
        transaction {
            result =
                Events.slice(Events.volumeName).select { Events.runID eq runID }.withDistinct()
                    .map { it[Events.volumeName] }
        }
    } catch (e: Exception) {
        println("Exception on 'retrieveEventIDs': $e")
    }
    return result
}

fun retrieveEnergyInVolumeForRun(
    runID: Int,
    volume: String,
    eventIDStart: Int = 0,
    energyResolution: Double = 0.0
): Map<Int, Double> {
    val result = mutableMapOf<Int, Double>()
    try {
        transaction {
            val rng = ISAACRandom() // random seed
            for (pair in Events
                .slice(Events.eDep.sum(), Events.eventID)
                .select { (Events.volumeName eq volume) and (Events.runID eq runID) and (Events.eventID greaterEq eventIDStart) }
                .limit(5000)
                .groupBy(Events.eventID)
                .orderBy(Events.eventID to SortOrder.ASC).map {
                    it[Events.eventID] to it[Events.eDep.sum()]
                }) {
                result[pair.first] =
                    if (energyResolution > 0.0) max(
                        0.0, NormalDistribution(
                            rng,
                            pair.second!!,
                            pair.second!! * energyResolution / 2.355
                        ).sample()
                    )
                    else pair.second!!
            }
        }
    } catch (e: Exception) {
        println("Exception in 'retrieveEnergyInVolumeForRun': $e")
    }
    return result
}

fun retrieveEnergyPerVolumeForEvent(eventID: Int, runID: Int = 0): Map<String, Double> {

    val result = mutableMapOf<String, Double>()
    try {
        transaction {
            val volumeNames =
                Events.slice(Events.volumeName).select { Events.runID eq runID }.withDistinct().map {
                    it[Events.volumeName]
                }
            println("VolumeNames: $volumeNames")
            // compute the energy in each volume
            val Energy = Events
                .slice(Events.eDep.sum(), Events.volumeName)
                .select { Events.eventID eq eventID }
                .groupBy(Events.volumeName)
            val energyPerVolume = mutableMapOf<String, Double?>()
            Energy.forEach {
                val volumeName: String = it[Events.volumeName]
                val eDep: Double? = it[Events.eDep.sum()]
                result[volumeName] = eDep!!
            }
        }

    } catch (e: Exception) {
        println("EXCEPTION: $e")
    }
    return result
}

fun retrieveEventByID(eventID: Int, runID: Int = 0): EventFull? {
    var result: EventFull? = null
    try {
        transaction {
            // we get the unique runID (if it exists) while we check if eventID is in DB
            val runIDs =
                Events.slice(Events.runID).select { Events.eventID eq eventID }.withDistinct().map {
                    it[Events.runID]
                }
            // we continue only if this is a single number (could be null or more than 1 value)
            println("RUN IDS: $runIDs")
            if ((runIDs == null) or (runIDs.size > 1)) {
                result = null
            } else {
                val runID = runIDs[0]
                val N: Long = Events.select { (Events.eventID eq eventID) and (Events.runID eq runID) }.count()

                val x: MutableList<Double> = mutableListOf() // declare a list of size N with zeroes
                val y: MutableList<Double> = mutableListOf()
                val z: MutableList<Double> = mutableListOf()
                Events.select { (Events.eventID eq eventID) and (Events.runID eq runID) }.forEach {
                    x.add(it[Events.x])
                    y.add(it[Events.y])
                    z.add(it[Events.z])
                }
                result = EventFull(runID = runID, eventID = eventID, x = x, y = y, z = z)
            }
        }
    } catch (e: Exception) {
        println("EXCEPTION: $e")
        result = null
    }
    return result
}
