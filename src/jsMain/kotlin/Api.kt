import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import kotlinx.browser.window

val endpoint = window.location.origin // only needed until https://github.com/ktorio/ktor/issues/1695 is resolved

val jsonClient = HttpClient {
    install(JsonFeature)
    {
        serializer = KotlinxSerializer()
    }
}

suspend fun getEventFull(eventID: Int): List<EventFull> {
    return jsonClient.get(endpoint + EventFull.path + "?eventID=$eventID")
}

suspend fun getNumberOfEvents(runID: Int = 0): Long {
    return jsonClient.get("$endpoint/numberOfEvents?runID=$runID")
}

suspend fun getCounts(): Counts {
    return jsonClient.get(endpoint + Counts.path)
}

suspend fun sendCommand(command: String): String {
    return jsonClient.post(endpoint + Command.path) {
        println("send post command: $command")
        body = TextContent(
            contentType = ContentType.Application.Json,
            text = "{ \"command\": \"$command\"}" // should use the serializer to convert to json
        )
        println("command '$command' sent")
    }
}

suspend fun clearDatabase() {
    return jsonClient.get("$endpoint/clear")
}

suspend fun getEventIDsFromRunID(runID: Int): Set<Int> {
    return jsonClient.get("$endpoint/eventIDsFromRunID?runID=$runID")
}

suspend fun getVolumeNamesFromRunID(runID: Int): Set<String> {
    return jsonClient.get("$endpoint/volumeNamesFromRunID?runID=$runID")
}

suspend fun getEventEnergyPerVolume(runID: Int, eventID: Int): Map<String, Double> {
    return jsonClient.get("$endpoint/energyPerVolume?runID=$runID&eventID=$eventID")
}

suspend fun getRunEnergyForVolume(
    runID: Int,
    volume: String,
    eventIDStart: Int = 0,
    energyResolution: Double = 0.0
): Map<Int, Double> {
    return jsonClient.get("$endpoint/energyInVolumeForRun?runID=$runID&volume=$volume&eventIDStart=$eventIDStart&energyResolution=$energyResolution")
}

suspend fun retrievePositionsAndEnergies(runID: Int, volume: String? = null): Map<String, List<Double>> {
    return jsonClient.get("$endpoint/retrievePositionsAndEnergies?runID=$runID&volume=$volume")
}
