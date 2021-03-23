import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import hep.dataforge.meta.invoke
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.Compression
import io.ktor.features.ContentNegotiation
import io.ktor.features.gzip
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.http.cio.websocket.send
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kscience.plotly.Plotly
import kscience.plotly.ResourceLocation
import kscience.plotly.makeFile
import kscience.plotly.trace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.PI
import kotlin.math.sin

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

fun clearDatabase() {
    try {
        transaction {
            Events.deleteAll()
        }
    } catch (e: Exception) {
        println("Exception on server-side 'clearDatabase': $e")
    }
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
}

fun retrieveEnergyInVolumeForRun(runID: Int, volume: String): Map<Int, Double> {
    val result = mutableMapOf<Int, Double>()
    try {
        transaction {
            for (pair in Events
                .slice(Events.eDep.sum(), Events.eventID)
                .select { (Events.volumeName eq volume) and (Events.runID eq runID) }
                .groupBy(Events.eventID).map {
                    //println("EventID: ${it[Events.eventID]} Energy: ${it[Events.eDep.sum()]}")
                    it[Events.eventID] to it[Events.eDep.sum()]
                }) {
                result[pair.first] = pair.second!!
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
            //println("EventID: $eventID Energy:")
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

fun plotStuff() {
    val x1 = (0..100).map { it.toDouble() / 100.0 }
    val y1 = x1.map { sin(2.0 * PI * it) }

    val plot = Plotly.plot {
        trace {
            x.set(x1)
            y.set(y1)
            name = "for a single trace in graph its name would be hidden"
        }
        layout {
            title = "Graph name"
            xaxis {
                title = "x axis"
            }
            yaxis {
                title = "y axis"
            }
        }
    }

    plot.makeFile(resourceLocation = ResourceLocation.EMBED)
}

fun main() {

    //plotStuff()
    //

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
        install(WebSockets)

        routing {
            /*
            webSocket("/") {
                val message = Message("world", listOf<Double>(12231.231, 231321.0, 223.2))
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText = frame.readText()
                    val result = retrieveEnergyInVolumeForRun(runID = 105, volume = "world")

                    send(Json.encodeToString(message))
                }
            }
             */
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
            get("/eventIDsFromRunID") {
                var fail = true
                val runIDs = call.request.queryParameters.getAll("runID")
                println("runIDS: $runIDs")
                if (runIDs?.size == 1 && runIDs[0].toIntOrNull() != null) {
                    val runID: Int = runIDs[0].toInt()
                    println("runID: $runID")
                    val eventIDs = retrieveEventIDs(runID)
                    if (eventIDs != null) {
                        println("EVENTIDS: $eventIDs")
                        fail = false
                        call.respond(eventIDs)
                    }
                }
                if (fail) call.respond(HttpStatusCode.NotFound)
            }
            get("/energyInVolumeForRun") {
                val runID: Int? = call.request.queryParameters["runID"]?.toIntOrNull()
                val volume: String? = call.request.queryParameters["volume"]
                if (runID == null || volume == null) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    val result = retrieveEnergyInVolumeForRun(runID = runID, volume = volume)
                    call.respond(result)
                }
            }
            route(Counts.path) {
                get {
                    call.respond(retrieveCounts())
                }
            }
            get("/energyPerVolume") {
                val runID: Int? = call.request.queryParameters["runID"]?.toIntOrNull()
                val eventID: Int? = call.request.queryParameters["eventID"]?.toIntOrNull()
                if (runID == null || eventID == null) {
                    // cannot continue
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    call.respond(retrieveEnergyPerVolumeForEvent(runID = runID, eventID = eventID))
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
            get("/clear") {
                clearDatabase()
                call.respond(HttpStatusCode.OK)
            }
            static("/") {
                resources("")
            }
        }
    }.start(wait = true)
}
