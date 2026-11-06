package dev.johnoreilly.confetti.livery

import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteImageBitmap
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteBoolean
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteColor
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteString
import androidx.compose.remote.creation.compose.state.rememberRemoteImageBitmap
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The contract a template is being captured for, and what it touched: the inputs it reads and the
 * actions it emits. Livery wants both in the template's `<target>.json` (`reads`, `emits`);
 * recording them at capture time means they can't drift from the document, and anything outside
 * the contract fails the capture.
 */
class TemplateScope(val contract: ConfettiContract, val example: Map<Input, Any>) {
    val reads = linkedSetOf<String>()
    val emits = linkedSetOf<String>()

    fun read(input: Input) {
        check(contract.inputs[input.name] === input) { "${input.name} is not an input of ${contract.ref}" }
        reads += input.name
    }

    fun emit(action: Action) {
        check(contract.actions[action.name] === action) { "${action.name} is not an action of ${contract.ref}" }
        emits += action.name
    }
}

val LocalTemplateScope = staticCompositionLocalOf<TemplateScope> { error("not capturing a template") }

/**
 * A contract string input, as a `USER` named value (PROTOCOL §4.2). Its initial value, what a
 * player shows before the host pushes anything, is the design's example.
 */
@Composable
fun input(input: Input): RemoteString {
    val scope = LocalTemplateScope.current
    require(input.type == InputType.String) { "${input.name} is ${input.type}" }
    scope.read(input)
    return rememberNamedRemoteString(input.name, scope.example[input] as? String ?: "")
}

@Composable
fun inputBool(input: Input): RemoteBoolean {
    val scope = LocalTemplateScope.current
    require(input.type == InputType.Bool) { "${input.name} is ${input.type}" }
    scope.read(input)
    val initial = scope.example[input] as? Boolean ?: input.default?.toString()?.toBooleanStrictOrNull() ?: false
    return rememberNamedRemoteBoolean(input.name, initial)
}

/** A colour input: a theme role (`theme.primary`) a theme pack can override; the brand is the initial value. */
@Composable
fun inputColor(input: Input, initial: Color): RemoteColor {
    require(input.type == InputType.Color) { "${input.name} is ${input.type}" }
    LocalTemplateScope.current.read(input)
    return rememberNamedRemoteColor(input.name, initial)
}

/** An image input, drawn from `livery:value/<input>` (PROTOCOL §6.1); the client resolves it. */
@Composable
fun inputImage(input: Input): RemoteImageBitmap {
    require(input.type == InputType.Image) { "${input.name} is ${input.type}" }
    LocalTemplateScope.current.read(input)
    return rememberRemoteImageBitmap("livery:value/${input.name}")
}

/** A contract action with no argument. */
@Composable
fun emit(action: Action): RemoteAction {
    require(action.arg == null) { "${action.name} takes ${action.arg}" }
    LocalTemplateScope.current.emit(action)
    return hostAction(action.name.rs)
}

/**
 * A contract action with its one string argument. Livery maps a bare text value only to the
 * argument `value`, so any other argument is sent as a JSON object built in the document.
 */
@Composable
fun emit(action: Action, value: RemoteString): RemoteAction {
    val arg = requireNotNull(action.arg) { "${action.name} takes no argument" }
    LocalTemplateScope.current.emit(action)
    return hostAction(action.name.rs, if (arg == "value") value else "{\"$arg\":\"".rs + value + "\"}")
}
