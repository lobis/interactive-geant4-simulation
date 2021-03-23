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

fun main() {
    window.onload = {

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

        render(document.getElementById(CommandsComponent.id)) {
            h1 { +"Commands" }
            child(CommandsComponent::class) {
                attrs {
                    commands = setOf(
                        "/control/execute macros/scintillationNaI.mac",
                        "/run/beamOn 1000",
                        "/run/beamOn 10000"
                    )
                }
            }
        }

        render(document.getElementById(EventSelectorComponent.id)) {
            h1 { +"Event Selector" }
            child(EventSelectorComponent::class) {
                attrs.changes = channel
            }
        }
    }
    //
    document.addEventListener("DOMContentLoaded", {
        val element = document.getElementById("canvas") as? HTMLElement
            ?: error("Element with id 'app' not found on page")

        console.log("element loaded")
        element.plot {
            scatter {
                x(1, 2, 3, 4)
                y(10, 15, 13, 17)
                mode = ScatterMode.markers
                type = TraceType.scatter
            }
            scatter {
                x(2, 3, 4, 5)
                y(10, 15, 13, 17)
                mode = ScatterMode.lines
                type = TraceType.scatter
                marker.apply {
                    GlobalScope.launch {
                        while (isActive) {
                            delay(500)
                            if (Random.nextBoolean()) {
                                color("magenta")
                            } else {
                                color("blue")
                            }
                        }
                    }
                }
            }
            scatter {
                x(1, 2, 3, 4)
                y(12, 5, 2, 12)
                mode = ScatterMode.`lines+markers`
                type = TraceType.scatter
                marker {
                    color("red")
                }
            }
            layout {
                title = "Line and Scatter Plot"
            }
        }
    })
}