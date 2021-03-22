import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.sql.Database
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.request.*
//
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
//
import kotlinx.html.*

fun initDB() {
    try {
        val config = HikariConfig("/hikari.properties")
        val ds = HikariDataSource(config)
        Database.connect(ds)
    } catch (e: Exception) {
        println("Exception in 'initDB': $e")
    }
}

object Events : Table() {
    val runID = integer("runid")
    val eventID = integer("eventid")
    val trackID = integer("trackid")
    val stepID = integer("stepid")

    //
    val trackParentID = integer("trackparentid")
    val volumeID = integer("volumeid")
    val particleID = integer("particleid")
    val processID = integer("processid")

    // data
    val x = double("x")
    val y = double("y")
    val z = double("z")
    val eDep = double("edep")
    val kineticE = double("kinetice")
    val timeGlobal = double("timeglobal")

    // these can be null!
    val px = double("px")
    val py = double("py")
    val pz = double("pz")
    val charge = double("charge")
    val steplength = double("steplength")
    val tracklength = double("tracklength")

    // strings
    val volumeName = text("volumename")
    val processName = text("processname")
    val particleName = text("particlename")
    val materialName = text("materialname")

}


fun retrieveCounts(): Counts {
    var result: Counts = Counts(mutableMapOf<Int, Long>())
    try {
        transaction {
            val queryResult =
                Events.slice(Events.runID, Events.eventID.countDistinct()).selectAll().withDistinct()
                    .groupBy(Events.runID)
                    .map {
                        it[Events.runID] to it[Events.eventID.countDistinct()]
                    }
            val m = mutableMapOf<Int, Long>()
            queryResult.map {
                m[it.first] = it.second
            }
            result = Counts(m)

        }
    } catch (e: Exception) {
        println("Exception on 'retrieveCounts': $e")
    }
    return result
};
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
};
fun retrieveNumberOfEventIDs(runID: Int): Long {
    return retrieveNumberOfEventIDs(listOf(runID))
};
fun retrieveEventIDs(runID: Int = 0): List<Int>? {
    initDB()
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
};
fun retrieveEnergyPerVolume(eventIDsToProcess: List<Int>? = null): MutableList<EventEnergyPerVolume> {

    val eventEnergyPerVolumeList: MutableList<EventEnergyPerVolume> = mutableListOf()
    initDB()
    try {
        transaction {
            val runID: Int = 0
            var eventIDs: List<Int>
            if (eventIDsToProcess == null) {
                eventIDs =
                    Events.slice(Events.eventID).select { Events.runID eq runID }.withDistinct().map {
                        it[Events.eventID]
                    }
            } else {
                eventIDs = eventIDsToProcess
            }
            println("EventIDs: $eventIDs")

            val volumeNames =
                Events.slice(Events.volumeName).select { Events.runID eq runID }.withDistinct().map {
                    it[Events.volumeName]
                }
            println("VolumeNames: $volumeNames")

            for (eventID in eventIDs) {
                // compute the energy in each volume
                val Energy = Events
                    .slice(Events.eDep.sum(), Events.volumeName)
                    .select { Events.eventID eq eventID }
                    .groupBy(Events.volumeName)
                //println("EventID: $eventID Energy:")
                val energyPerVolume = mutableMapOf<String?, Double?>();
                Energy.forEach {
                    val volumeName: String? = it[Events.volumeName]
                    val eDep: Double? = it[Events.eDep.sum()]
                    energyPerVolume[volumeName] = eDep
                }
                val eventEnergyPerVolume: EventEnergyPerVolume = EventEnergyPerVolume(runID, eventID, energyPerVolume)
                eventEnergyPerVolumeList.add(eventEnergyPerVolume)
            }
        }
    } catch (e: Exception) {
        println("EXCEPTION: $e")
    }
    return eventEnergyPerVolumeList
};
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
};

fun HTML.index() {
    head {
        title("Interactive Geant4 Simulation")
    }
    body {
        div {
            +"Interactive Geant4 Simulation"
        }
        div {
            id = "root"
        }
        div {
            iframe {
                name = "hiddenFrame"
                width = "0"
                height = "0"
                style = "display: none"
            }
            for (command in listOf(
                "/control/execute macros/scintillationNaI.mac",
                "/run/beamOn 1000",
                "/run/beamOn 10000"
            )) {
                form {
                    action = "http://localhost:9080/send/"
                    method = FormMethod.post
                    encType = FormEncType.textPlain
                    target = "hiddenFrame"
                    div {
                        input {
                            id = "command"; name = "command"; type = InputType.text;value = command
                        }
                    }
                    div {
                        input {
                            type = InputType.submit; value = "Send"
                        }
                    }
                }
            }
        }
        div {
            textArea { id = "area"; rows = "5"; cols = "100" }
        }
        div {
            id = "sentCommands"
        }
        script(src = "/static/output.js") {}
    }
}

fun main() {
    initDB()
    embeddedServer(Netty, 8080) {
        install(ContentNegotiation) {
            json()
        }
        install(CORS) {
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Delete)
            anyHost()
        }
        install(Compression) {
            gzip()
        }
        routing {
            post("/command") {
                val postParameters: Parameters = call.receiveParameters()
                val command: String = postParameters["command"]!!
                // maybe this is useless
            }
            get("/numberOfEvents") {
                val runIDsRequested: List<String>? = call.request.queryParameters.getAll("runID")
                if (runIDsRequested == null) {
                    // default run ID = 0
                    call.respond(retrieveNumberOfEventIDs())
                } else {
                    var number: Long = 0
                    for (runID in runIDsRequested) {
                        number += retrieveNumberOfEventIDs(runID = runID.toInt())
                    }
                    call.respond(number)
                }
            }
            route(Counts.path) {
                get {
                    call.respond(retrieveCounts())
                }
            }
            route(EventEnergyPerVolume.path) {
                get {
                    val eventIDs = retrieveEventIDs()
                    call.respond(1)
                }
            }
            route(EventEnergyPerVolume.path) {
                get {
                    //val queryString: String = call.request.queryString()
                    val eventIDsRequested: List<String>? = call.request.queryParameters.getAll("eventID")
                    val eventIDsAsInt = eventIDsRequested?.map {
                        it.toInt()
                    }

                    val data = retrieveEnergyPerVolume(eventIDsAsInt)
                    call.respond(data)
                }
            }
            route(EventFull.path) {
                get {
                    val eventIDsRequested: List<String>? = call.request.queryParameters.getAll("eventID")
                    println("IDS REQUESTED: $eventIDsRequested")
                    if (eventIDsRequested == null) {
                        call.respond("No EventID was requested") // give some standarized error
                    } else {
                        val eventIDsAsInt = eventIDsRequested.map { it.toInt() }
                        val eventFullList = mutableListOf<EventFull>()
                        for (eventID in eventIDsAsInt) {
                            println("eventID requested: $eventID")
                            val data: EventFull? = retrieveEventByID(eventID)
                            if (data != null) eventFullList.add(data)

                        }
                        call.respond(eventFullList)
                    }
                }
            }
            get("/") {
                call.respondText(
                    this::class.java.classLoader.getResource("index.html")!!.readText(),
                    ContentType.Text.Html
                )
            }
            static("/") {
                resources("")
            }
        }
    }.start(wait = true)
}