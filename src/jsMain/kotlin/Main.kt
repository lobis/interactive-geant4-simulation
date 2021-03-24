import react.dom.render
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import react.dom.h1
import kotlinx.browser.window

//
import hep.dataforge.meta.invoke
import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kscience.plotly.models.ScatterMode
import kscience.plotly.models.TraceType
import kscience.plotly.plot
import kscience.plotly.scatter
import org.w3c.dom.HTMLElement
import kotlin.random.Random

typealias Change = EventSelectorState.() -> Unit

// https://itnext.io/taming-react-with-kotlin-js-and-coroutines-ef0d3f72b3ea

@ExperimentalJsExport
fun main() {
    //window.onload = {

    val channel = Channel<Change>(capacity = Channel.RENDEZVOUS)

    GlobalScope.launch(Dispatchers.Main) {
        while (isActive) {
            val c = getCounts()
            channel.send {
                counts = c
            }
            delay(500)
        }

    }

    render(
        document.getElementById(CommandsComponent.id)
    ) {
        h1 { +"Commands" }
        commandsComponent {
            commands = setOf(
                "/control/execute macros/scintillationNaI.mac",
                "/run/beamOn 1000",
                "/run/beamOn 10000"
            )
        }
    }

    render(document.getElementById(EventSelectorComponent.id)) {
        h1 { +"Event Selector" }
        child(EventSelectorComponent::class) {
            attrs.changes = channel
        }
    }


    render(document.getElementById("plots")) {
        histogramComponent {
            divID = "canvas"
        }
    }


    //}
}