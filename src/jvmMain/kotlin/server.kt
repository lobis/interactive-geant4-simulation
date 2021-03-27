import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.post
import io.ktor.features.CORS
import io.ktor.features.Compression
import io.ktor.features.ContentNegotiation
import io.ktor.features.gzip
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets


fun main() {
    initDB()
    val httpClient = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
            accept(ContentType.Application.Json)
        }
    }
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
                if (runIDs?.size == 1 && runIDs[0].toIntOrNull() != null) {
                    val runID: Int = runIDs[0].toInt()
                    val eventIDs = retrieveEventIDs(runID)
                    if (eventIDs != null) {
                        println("EVENTIDS: $eventIDs")
                        fail = false
                        call.respond(eventIDs)
                    }
                }
                if (fail) call.respond(HttpStatusCode.NotFound)
            }
            get("/retrievePositionsAndEnergies") {
                val runID: Int? = call.request.queryParameters["runID"]?.toIntOrNull()
                val volumeName: String? = call.request.queryParameters["volumeName"]
                if (runID != null) {
                    val result = retrievePositionsAndEnergies(runID = runID, volume = volumeName)
                    call.respond(result)
                }
                call.respond(HttpStatusCode.NotFound)

            }
            get("/volumeNamesFromRunID") {
                var fail = true
                val runIDs = call.request.queryParameters.getAll("runID")
                if (runIDs?.size == 1 && runIDs[0].toIntOrNull() != null) {
                    val runID: Int = runIDs[0].toInt()
                    val volumeNames = retrieveVolumeNames(runID)
                    if (volumeNames != null) {
                        println("VOLUMENAMES: $volumeNames")
                        fail = false
                        call.respond(volumeNames)
                    }
                }
                if (fail) call.respond(HttpStatusCode.NotFound)
            }
            get("/energyInVolumeForRun") {
                val runID: Int? = call.request.queryParameters["runID"]?.toIntOrNull()
                val volume: String? = call.request.queryParameters["volume"]
                // energy resolution is a temporary thing for demonstration
                val energyResolution: Double? = call.request.queryParameters["energyResolution"]?.toDoubleOrNull()
                if (runID == null || volume == null) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    if (energyResolution == null) {
                        val result = retrieveEnergyInVolumeForRun(runID = runID, volume = volume)
                        call.respond(result)
                    } else {
                        val result = retrieveEnergyInVolumeForRun(
                            runID = runID,
                            volume = volume,
                            energyResolution = energyResolution
                        )
                        call.respond(result)
                    }
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
            post(Command.path) {
                val command = call.receive<Command>().command
                val host: String = System.getenv("SIMULATION_HOST") ?: "localhost"
                println("Sending command: '$command' to '$host'")
                httpClient.post<String>("http://$host:9080/send/") {
                    println("send post command: $command")
                    body = TextContent(
                        text = "command=$command",
                        contentType = ContentType.Text.Plain
                    )
                }
                call.respond(HttpStatusCode.OK)
            }
            static("/") {
                resources("")
            }
        }
    }.start(wait = true)


}
