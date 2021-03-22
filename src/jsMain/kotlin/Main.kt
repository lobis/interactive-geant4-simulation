import react.child
import react.dom.render
import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.html.dom.append
import kotlinx.html.js.*
import react.*
import react.dom.div
import react.dom.h1
import react.dom.p
import react.dom.render
import kotlinx.browser.window

typealias Change = State.() -> Unit

private val scope = MainScope()

// https://itnext.io/taming-react-with-kotlin-js-and-coroutines-ef0d3f72b3ea

fun main() {
    window.onload = {

        val channel = Channel<Change>(capacity = Channel.RENDEZVOUS)

        render(document.getElementById(Counter.id)) {
            h1 { +"Counter" }
            child(Counter::class) {
                attrs {
                    counts = Counts()
                    changes = channel
                }
            }

        }

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
    }
}
