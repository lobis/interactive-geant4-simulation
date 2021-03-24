import io.ktor.client.fetch.*
import react.*
import react.dom.*
import kotlinext.js.*
import kotlinx.browser.document
import kotlinx.html.js.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.html.*
import org.w3c.dom.HTMLInputElement
//
import kscience.plotly.Plot
import kscience.plotly.Plotly
import kscience.plotly.layout
import kscience.plotly.models.Trace
import kscience.plotly.models.invoke
import kscience.plotly.plot
import org.w3c.dom.HTMLElement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


external interface CommandsProps : RProps {
    var commands: Set<String>
}

external interface CommandsState : RState {
    var commandsSent: MutableList<String>
}

@ExperimentalJsExport
@JsExport
class CommandsComponent(props: CommandsProps) : RComponent<CommandsProps, CommandsState>(props) {
    companion object {
        const val id: String = "commands"
    }

    override fun CommandsState.init(props: CommandsProps) {
        commandsSent = mutableListOf()
    }

    override fun RBuilder.render() {
        props.commands.map {
            val command: String = it
            button {
                a { +command }
                attrs.id = command
                attrs.onClickFunction = {
                    GlobalScope.launch(Dispatchers.Main) {
                        sendCommand(command)
                        setState {
                            commandsSent.add(command)
                        }
                    }
                }
            }
        }
        button {
            a { +"Clear Database" }
            attrs.id = "clear-database-button"
            attrs.onClickFunction = {
                GlobalScope.launch(Dispatchers.Main) {
                    clearDatabase()
                }
            }
        }
        h1 { +"Commands Sent" }
        val commandsDisplayTextAreaID: String = "commands-sent-display"
        textArea {
            attrs {
                id = commandsDisplayTextAreaID
                readonly = true
                value = state.commandsSent.joinToString(separator = "\n")
            }
        }
    }
}

@ExperimentalJsExport
fun RBuilder.commandsComponent(handler: CommandsProps.() -> Unit): ReactElement {
    return child(CommandsComponent::class) {
        this.attrs(handler)
    }
}

external interface EventSelectorState : RState {
    var counts: Counts
};
external interface EventSelectorProps : RProps {
    var changes: ReceiveChannel<EventSelectorState.() -> Unit>
};

@ExperimentalJsExport
@JsExport
class EventSelectorComponent(props: EventSelectorProps) : RComponent<EventSelectorProps, EventSelectorState>(props) {
    companion object {
        const val id: String = "event-selector"
    }

    override fun EventSelectorState.init() {
        counts = Counts()
    }

    init {
        state.init()
        GlobalScope.launch {
            for (change in this@EventSelectorComponent.props.changes) {
                setState(change)
            }
        }
    }

    override fun RBuilder.render() {
        div {
            attrs.id = "counts-selector"
            countsTableComponent {
                counts = state.counts
            }
            div {
                attrs.id = "run-event-selector"
                SelectorComponent {
                    runIDs = state.counts.getRunIDs()
                    eventIDs = setOf()
                }
            }
        }
    }
}

external interface CountsTableProps : RProps {
    var counts: Counts
};

@JsExport
class CountsTableComponent : RComponent<CountsTableProps, RState>() {

    override fun RBuilder.render() {
        table(classes = "table table-hover table-dark table-sm") {
            attrs.id = "table-counts"
            thead(classes = "thead-dark") {
                tr {
                    th(scope = ThScope.col) { +"runID" }
                    th(scope = ThScope.col) { +"events" }
                }
            }
            tbody {
                props.counts.counts.map {
                    tr {
                        td { +"${it.key}" }
                        td { +"${it.value}" }
                    }
                }
            }
        }
    }
}

fun RBuilder.countsTableComponent(handler: CountsTableProps.() -> Unit): ReactElement {
    return child(CountsTableComponent::class) {
        this.attrs(handler)
    }
}

external interface SelectorProps : RProps {
    var runIDs: Set<Int>
    var eventIDs: Set<Int>
}

external interface SelectorState : RState {
    var eventIDs: Set<Int>
    var currentEnergyPerVolume: Map<String, Double>
}

@JsExport
class SelectorComponent(props: SelectorProps) : RComponent<SelectorProps, SelectorState>(props) {
    override fun SelectorState.init(props: SelectorProps) {
        eventIDs = setOf()
        currentEnergyPerVolume = mapOf<String, Double>()
    }

    override fun RBuilder.render() {
        div {
            label { +"runID" }
        }
        input {
            attrs {
                id = "runID-choice"
                name = "runID-choice"
                list = "runID-values"
                onChangeFunction = {
                    val element = document.getElementById("runID-choice") as HTMLInputElement
                    val runID = element.value.toIntOrNull()
                    if (runID != null && runID in props.runIDs) {
                        GlobalScope.launch(Dispatchers.Main) {
                            val response: Set<Int> = getEventIDsFromRunID(runID)
                            setState {
                                eventIDs = response
                            }
                        }
                    }
                }
            }
        }
        dataList {
            attrs.id = "runID-values"
            for (runID in props.runIDs) {
                option {
                    attrs.value = runID.toString()
                }
            }
        }
        div {
            label { +"eventID" }
        }
        input {
            attrs {
                id = "eventID-choice"
                name = "eventID-choice"
                list = "eventID-values"
            }
        }
        dataList {
            attrs.id = "eventID-values"
            for (eventID in state.eventIDs) {
                option {
                    attrs.value = eventID.toString()
                }
            }
        }
        div {
            attrs.id = "energy-per-volume"
            button {
                a { +"Compute energy per volume" }
                attrs.onClickFunction = {
                    var element = document.getElementById("runID-choice") as HTMLInputElement
                    val runID = element.value.toIntOrNull()
                    element = document.getElementById("eventID-choice") as HTMLInputElement
                    val eventID = element.value.toIntOrNull()
                    if (runID != null && eventID != null) {
                        GlobalScope.launch(Dispatchers.Main) {
                            val response: Map<String, Double> =
                                getEventEnergyPerVolume(runID = runID, eventID = eventID)
                            setState {
                                currentEnergyPerVolume = response
                            }
                        }
                    }
                }
            }
            div {
                attrs.id = "energy-per-volume-result"
                +state.currentEnergyPerVolume.toString()
            }
        }
    }
}

fun RBuilder.SelectorComponent(handler: SelectorProps.() -> Unit): ReactElement {
    return child(SelectorComponent::class) {
        this.attrs(handler)
    }
}

external interface HistogramProps : RProps {
    var divID: String
}

external interface HistogramState : RState {
    var trace: Trace
    var dt: Double
}

@JsExport
class HistogramComponent(props: HistogramProps) : RComponent<HistogramProps, HistogramState>(props) {
    override fun HistogramState.init(props: HistogramProps) {
        console.log("state init")
        dt = 0.0
        val x = (0..100).map { it.toDouble() / 100.0 }.toDoubleArray()
        val y = x.map { sin(2.0 * PI * (it - dt)) }.toDoubleArray()
        trace = Trace(x, y) { name = "f" }
    }

    init {
        state.init()
        GlobalScope.launch {
            while (isActive) {
                state.dt = state.dt + 0.1
                console.log("<update state to: ${state.dt}")
                val x = (0..10000).map { it.toDouble() / 10000.0 }.toDoubleArray()
                val y = x.map { sin((2.0 * PI + state.dt) * it) }.toDoubleArray()
                state.trace = Trace(x, y) { name = "f" }
                render()
                delay(200)
            }
        }
    }

    override fun RBuilder.render() {
        val element = document.getElementById(props.divID) as? HTMLElement
            ?: error("Element with id '${props.divID}' not found on page")
        console.log("element loaded on '${props.divID}'")
        element.plot {
            traces(state.trace)
            layout {
                title = "Graph"
                xaxis { title = "x" }
                yaxis { title = "y" }
            }
        }
    }
}

fun RBuilder.histogramComponent(handler: HistogramProps.() -> Unit): ReactElement {
    return child(HistogramComponent::class) {
        this.attrs(handler)
    }

}
