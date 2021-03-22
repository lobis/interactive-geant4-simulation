import io.ktor.client.fetch.*
import react.*
import react.dom.*
import kotlinext.js.*
import kotlinx.html.js.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.html.*

interface CounterProps : RProps {
    var counts: Counts
    var changes: ReceiveChannel<Change>
}

interface State : RState {
    var counts: Counts
}

@ExperimentalJsExport
@JsExport
class Counter(props: CounterProps) : RComponent<CounterProps, State>(props) {
    companion object {
        const val id: String = "counter"
    }

    init {
        GlobalScope.launch {
            for (change in this@Counter.props.changes) {
                setState(change)
            }
        }
    }

    override fun State.init(props: CounterProps) {
        counts = props.counts
    }

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
                state.counts.counts.map {
                    tr {
                        td { +"${it.key}" }
                        td { +"${it.value}" }
                    }
                }
            }
        }

    }
}

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

interface PlotProps : RProps {
}

interface PlotState : RState {
    var runIDs: Set<Int>
    var eventIDs: Set<Int>
}

@JsExport
class PlotComponent(props: PlotProps) : RComponent<PlotProps, PlotState>(props) {
    companion object {
        const val id: String = "plot-options"
    }

    override fun PlotState.init(props: PlotProps) {
        runIDs = setOf<Int>(1, 2, 10, 23)
        eventIDs = setOf<Int>(2, 2, 21, 2)
    }

    override fun RBuilder.render() {
        div {
            label { +"choose one" }
        }
        input {
            attrs {
                id = "runID-choice"
                name = "runID-choice"
                list = "runID-values"
                onClickFunction = {
                    println("Entered on click!")
                    setState { runIDs = setOf<Int>(22) }
                }
            }
        }
        dataList {
            attrs.id = "runID-values"
            for (runID in state.runIDs) {
                option {
                    attrs.value = runID.toString()
                }
            }
        }
    }
}



