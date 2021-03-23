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
import kotlin.math.PI
import kotlin.math.sin

interface CommandsProps : RProps {
    var commands: Set<String>
}

interface CommandsState : RState {
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


interface EventSelectorState : RState {
    var counts: Counts
};
interface EventSelectorProps : RProps {
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

interface CountsTableProps : RProps {
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

interface SelectorProps : RProps {
    var runIDs: Set<Int>
    var eventIDs: Set<Int>
}

interface SelectorState : RState {
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