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
        table {
            attrs.id = "table-counts"
            thead {
                tr {
                    th { +"runID" }
                    th { +"events" }
                }
            }
            state.counts.counts.map {
                tbody {
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
        h1 { +"Commands Sent" }
        ul {
            for (command in state.commandsSent) {
                li { +command }
            }
        }

    }
}




