import react.dom.render
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import react.dom.h1

typealias Change = EventSelectorState.() -> Unit

@ExperimentalJsExport
@JsExport
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

    render(
        document.getElementById(CommandsComponent.id)
    ) {
        h1 { +"Commands" }
        commandsComponent {
            commands = setOf(
                "/control/execute macros/scintillationNaI.mac",
                "/run/beamOn 1000",
                "/run/beamOn 10000",
                "/run/beamOn 100000",
            )
        }
    }

    render(document.getElementById(EventSelectorComponent.id)) {
        h1 { +"Event Selector" }
        child(EventSelectorComponent::class) {
            attrs.changes = channel
        }
    }
}