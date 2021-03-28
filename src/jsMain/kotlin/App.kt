import io.ktor.client.fetch.*
import kotlinext.js.*
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.html.*
import kotlinx.html.js.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.*
import react.dom.*
import space.kscience.plotly.*
import space.kscience.plotly.models.BarMode
import space.kscience.plotly.models.HistFunc
import kotlin.math.max

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
            button(classes = "btn btn-outline-primary") {
                +command
                attrs.id = command
                attrs.onClickFunction = {
                    GlobalScope.launch(Dispatchers.Default) {
                        setState {
                            commandsSent.add(command)
                        }
                        // TODO: debug why this gives error
                        try {
                            sendCommand(command)
                        } catch (e: Exception) {
                            println("Exception while sending command after pressing button': $e")
                        }
                    }
                }
            }
        }
        br {}
        button(classes = "btn btn-outline-danger", type = ButtonType.reset) {
            +"Clear Database"
            attrs.id = "clear-database-button"
            attrs.onClickFunction = {
                GlobalScope.launch(Dispatchers.Default) {
                    clearDatabase()
                }
            }
        }
        div {
            attrs.id = "user-defined-commands-div"

            h1 { +"Arbitrary Commands" }
            val userDefinedCommandsTextAreaID: String = "user-defined-commands"
            textArea {
                attrs {
                    id = userDefinedCommandsTextAreaID
                    readonly = false
                }
            }
            br {}
            button(classes = "btn btn-warning") {
                +"Send User Defined Commands"
                attrs.id = "user-defined-commands-send"
                attrs.onClickFunction = {
                    val element = document.getElementById(userDefinedCommandsTextAreaID) as HTMLTextAreaElement
                    val commands = element.value
                    GlobalScope.launch(Dispatchers.Default) {
                        for (command in commands.split("\n")) {
                            if (command == "") {
                                continue
                            }
                            setState {
                                commandsSent.add(command)
                            }
                            sendCommand(command)
                        }
                    }
                }
            }
        }
        h1 { +"Commands Sent" }
        val commandsDisplayTextAreaID = "commands-sent-display"
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

external interface CountsTableState : RState {
    var toggle: Boolean
};

external interface CountsTableProps : RProps {
    var counts: Counts
};

@ExperimentalJsExport
@JsExport
class CountsTableComponent : RComponent<CountsTableProps, CountsTableState>() {
    override fun CountsTableState.init(props: CountsTableProps) {
        toggle = true
    }

    override fun RBuilder.render() {
        button(classes = "btn btn-outline-light") {
            attrs.id = "table-counts-toggle"
            +"Toggle Couns Table"
            attrs.onClickFunction = {
                setState {
                    toggle = !toggle
                }
            }
        }
        if (!state.toggle) {
            table(classes = "table table-hover table-striped table-sm") {
                attrs.id = "table-counts"
                thead {
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
}

@ExperimentalJsExport
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

@ExperimentalJsExport
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
                type = InputType.number
                onChangeFunction = {
                    val runID = getSelectedRunID()
                    if (runID != null && runID in props.runIDs) {
                        GlobalScope.launch(Dispatchers.Default) {
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
                type = InputType.number
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
            button(classes = "btn btn-outline-primary") {
                +"Compute energy per volume for selected event"
                attrs.onClickFunction = {
                    var element = document.getElementById("runID-choice") as HTMLInputElement
                    val runID = element.value.toIntOrNull()
                    element = document.getElementById("eventID-choice") as HTMLInputElement
                    val eventID = element.value.toIntOrNull()
                    if (runID != null && eventID != null) {
                        GlobalScope.launch(Dispatchers.Default) {
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
            /*
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
             */
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

@ExperimentalJsExport
@JsExport
external interface HistogramProps : RProps {
    var id: String // id of div
}

@ExperimentalJsExport
@JsExport
external interface HistogramState : RState {
    var runID: Int
    var volumeName: String
    var values: List<Double>
    var energyPerEvent: Map<Int, Double>
    var xMin: Double
    var xMax: Double
    var nBinsX: Int
}

@ExperimentalJsExport
@JsExport
class HistogramComponent(props: HistogramProps) : RComponent<HistogramProps, HistogramState>(props) {
    override fun HistogramState.init(props: HistogramProps) {
        runID = 0
        volumeName = "target"
        values = listOf()
        energyPerEvent = mapOf()
        xMin = 0.0
        xMax = 800.0
        nBinsX = 120
    }

    init {
        state.init()
        GlobalScope.launch {
            while (isActive) {
                var element = document.getElementById("runID-choice") as HTMLInputElement
                val runIDorNull = element.value.toIntOrNull()
                element = document.getElementById("volumeName-choice") as HTMLInputElement
                val theVolumeName: String = element.value
                var wait: Long = 2000
                if (runIDorNull != null && theVolumeName != "") {
                    val energyResolution: Double =
                        (document.getElementById("energy-resolution") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
                    // we don't request the values we already have
                    val eventIDMax: Int = state.energyPerEvent.maxByOrNull { it.key }?.key?.plus(1) ?: 0

                    val m =
                        getRunEnergyForVolume(
                            runID = runIDorNull,
                            volume = theVolumeName,
                            eventIDStart = eventIDMax,
                            energyResolution = energyResolution / 100.0 // in %
                        )
                    if (m.isNotEmpty()) wait = 100 // if we are still recieving data, don't wait to send another request
                    println("Received Energies per event (eventID>=$eventIDMax) size: ${m.size}")
                    setState {
                        runID = runIDorNull
                        volumeName = theVolumeName
                        energyPerEvent += m
                        values += m.map { it.value }
                    }
                }
                delay(wait)
            }
        }
    }

    override fun RBuilder.render() {
        val element = document.getElementById(props.id) as? HTMLElement
            ?: error("Element with id '${props.id}' not found on page")

        div {
            div {
                +"Number of bins: ${state.nBinsX}"
            }
            div {
                input {
                    attrs {
                        id = "nbinsx"
                        name = "nbinsx"
                        type = InputType.range
                        min = "10"
                        max = "400"
                        step = "5"
                        value = state.nBinsX.toString()
                        onChangeFunction = {
                            val e = document.getElementById("nbinsx") as HTMLInputElement
                            val n = e.value.toInt()
                            setState {
                                nBinsX = n
                            }
                        }
                    }

                }
            }
            div {
                div {
                    +"X axis range: (${state.xMin}, ${state.xMax})"
                }
                input {
                    attrs {
                        id = "xaxisrange"
                        name = "xaxisrange"
                        type = InputType.range
                        min = "200"
                        max = "2000"
                        step = "50"
                        value = state.xMax.toString()
                        onChangeFunction = {
                            val e = document.getElementById("xaxisrange") as HTMLInputElement
                            val n = e.value.toDouble()
                            setState {
                                xMin = 0.0
                                xMax = n
                            }
                        }
                    }
                }
            }
        }

        element.plot {
            histogram {
                x.numbers = state.values
                name = "Random data"
                xbins {
                    end = state.xMax
                    start = state.xMin
                    size = (state.xMax - state.xMin) / state.nBinsX
                }
                histfunc = HistFunc.count
                marker {
                    color(0, 0, 255, 0.8)
                }
                opacity = 1
            }
            layout {
                width = (document.getElementById("canvas") as HTMLElement).offsetWidth
                height = (document.getElementById("event-selector") as HTMLElement).offsetHeight
                bargap = 0.05
                bargroupgap = 0.2
                barmode = BarMode.overlay
                title {
                    text = "Energy deposited in volume \"${state.volumeName}\" for runID: ${state.runID}"
                    font {
                        size = 25
                        color("black")
                        family = "sans-serif"
                    }
                }
                xaxis {
                    title {
                        text = "Energy (keV)"
                        font {
                            size = 20
                            family = "sans-serif"
                        }
                    }
                    autorange = false
                    this.range = state.xMin..state.xMax
                }
                yaxis {
                    title {
                        text = "Counts"
                        font {
                            size = 20
                            family = "sans-serif"
                        }
                    }
                }
            }
        }
    }
}

@ExperimentalJsExport
@JsExport
fun RBuilder.histogramComponent(handler: HistogramProps.() -> Unit): ReactElement {
    return child(HistogramComponent::class) {
        this.attrs(handler)
    }
}
