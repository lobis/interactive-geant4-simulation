import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import react.dom.h1
import react.dom.render

typealias Change = EventSelectorState.() -> Unit

fun main() {

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

    window.addEventListener("DOMContentLoaded", {

        render(
            document.getElementById(CommandsComponent.id)
        ) {
            h1 { +"Commands" }
            commandsComponent {
                commands = setOf(
                    "/control/execute macros/scintillationNaI.mac",
                    "/run/beamOn 1000",
                    "/run/beamOn 10000",
                    "/run/beamOn 50000",
                )
            }
        }

        render(document.getElementById(EventSelectorComponent.id)) {
            h1 { +"Event Selector" }
            child(EventSelectorComponent::class) {
                attrs.changes = channel
            }
        }
    })
}