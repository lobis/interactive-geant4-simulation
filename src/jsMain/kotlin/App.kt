// normal distribution
import io.ktor.client.fetch.*
import kotlinext.js.*
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.html.*
import kotlinx.html.js.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.*
import space.kscience.plotly.*

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
    var volumeNames: Set<String>
    var currentEnergyPerVolume: Map<String, Double>
}

@JsExport
class SelectorComponent(props: SelectorProps) : RComponent<SelectorProps, SelectorState>(props) {
    override fun SelectorState.init(props: SelectorProps) {
        eventIDs = setOf()
        volumeNames = setOf()
        currentEnergyPerVolume = mapOf()
    }

    private val runIdChoiceID: String = "runID-choice"
    fun getSelectedRunID(): Int? {
        val element = document.getElementById(runIdChoiceID) as HTMLInputElement
        return element.value.toIntOrNull()
    }

    override fun RBuilder.render() {
        div {
            label { +"runID" }
        }
        input {
            attrs {
                id = "runID-choice"
                name = runIdChoiceID
                list = "runID-values"
                onChangeFunction = {
                    val runID = getSelectedRunID()
                    if (runID != null && runID in props.runIDs) {
                        GlobalScope.launch(Dispatchers.Main) {
                            val theEventIDs: Set<Int> = getEventIDsFromRunID(runID)
                            val theVolumeNames: Set<String> = getVolumeNamesFromRunID(runID)
                            setState {
                                eventIDs = theEventIDs
                                volumeNames = theVolumeNames
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
                a { +"Compute energy per volume for selected event" }
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
            // volume selector
            div {
                label { +"Volume" }
            }
            input {
                attrs {
                    id = "volumeName-choice"
                    name = "volumeName-choice"
                    list = "volumeName-values"
                }
            }
            dataList {
                attrs.id = "volumeName-values"
                for (volumeName in state.volumeNames) {
                    option {
                        attrs.value = volumeName
                    }
                }
            }
            histogramComponent {
                id = "canvas"
            }
            // checkbox to use all runs together
            div {
                label {
                    //attrs.htmlFor = "checkAllRunIDs"
                    +"Use all runIDs"
                }
                input {
                    attrs.type = InputType.checkBox
                    attrs.id = "checkAllRunIDs"
                    attrs.name = "checkAllRunIDs"
                }
            }
            // energy resolution
            div {
                label {
                    //attrs.htmlFor = "energy-resolution"
                    +"Energy resolution (%)"
                }
                input {
                    attrs {
                        type = InputType.number
                        defaultValue = "5" // in %
                        id = "energy-resolution"
                        name = "energy-resolution"
                    }
                }
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
    var id: String // id of div
}

external interface HistogramState : RState {
    var runID: Int
    var volumeName: String
    var values: List<Double>
    var xMin: Double
    var xMax: Double
    var nBinsX: Int
}

@JsExport
class HistogramComponent(props: HistogramProps) : RComponent<HistogramProps, HistogramState>(props) {
    override fun HistogramState.init(props: HistogramProps) {
        runID = 0
        volumeName = "target"
        values = listOf()
        xMin = 0.0
        xMax = 1000.0
        nBinsX = 100
    }

    init {
        state.init()
        GlobalScope.launch {
            while (isActive) {
                var element = document.getElementById("runID-choice") as HTMLInputElement
                val runIDorNull = element.value.toIntOrNull()
                element = document.getElementById("volumeName-choice") as HTMLInputElement
                val TheVolumeName: String = element.value
                if (runIDorNull != null && TheVolumeName != "") {
                    val energyResolution: Double =
                        (document.getElementById("energy-resolution") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
                    val energies = getRunEnergyForVolume(
                        runID = runIDorNull,
                        volume = TheVolumeName,
                        energyResolution = energyResolution / 100.0 // in %
                    ).map { it.value }

                    setState {
                        runID = runIDorNull
                        volumeName = TheVolumeName
                        values = energies
                    }
                }
                delay(1000)
            }
        }
    }

    override fun RBuilder.render() {
        val element = document.getElementById(props.id) as? HTMLElement
            ?: error("Element with id '${props.id}' not found on page")

        div {
            div {
                input {
                    attrs {
                        id = "nbinsx"
                        name = "nbinsx"
                        type = InputType.range
                        min = "5"
                        max = "200"
                        step = "1"
                        onChangeFunction = {
                            val element = document.getElementById("nbinsx") as HTMLInputElement
                            val n = element.value.toInt()
                            setState {
                                nBinsX = n
                            }
                        }
                    }

                }
                label { +"Number of bins" }
            }
            div {
                input {
                    attrs {
                        id = "xaxisrange"
                        name = "xaxisrange"
                        type = InputType.range
                        min = "0"
                        max = "2000"
                        step = "10"
                    }
                }
                label { +"X axis range" }
            }
        }

        element.plot {
            histogram {
                x.numbers = state.values
                name = "Random data"
                nbinsx = state.nBinsX
            }
            layout {
                bargap = 0.1
                title {
                    text = "Energy deposited in volume '${state.volumeName}' for runID: ${state.runID}"
                    font {
                        size = 20
                        color("black")
                    }
                }
                xaxis {
                    title {
                        text = "Energy (keV)"
                        font {
                            size = 16
                        }
                    }
                }
                yaxis {
                    title {
                        text = "Counts"
                        font {
                            size = 16
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.histogramComponent(handler: HistogramProps.() -> Unit): ReactElement {
    return child(HistogramComponent::class) {
        this.attrs(handler)
    }
}
