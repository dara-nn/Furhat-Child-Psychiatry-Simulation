package furhatos.app.openaichat.flow

import furhatos.flow.kotlin.*
import furhatos.gestures.ARKitParams
import furhatos.gestures.BasicParams
import furhatos.gestures.Gestures
import furhatos.gestures.defineGesture
import furhatos.app.openaichat.flow.chatbot.classifyIntent
import kotlin.random.Random


fun FlowControlRunner.askForAnything(text: String) {

    call(state {
        onEntry {
            furhat.ask(text)
        }
        onResponse {
            terminate()
        }
    })

}

val random = Random(0)

private const val EXPRESSION_SPEECH_DELAY_MS = 1500L

// ── Shared facial-expression timing ───────────────────────────────────────────
// All expression paths use the same rule: start the expression, wait 500 ms,
// then allow speech to begin while the gesture can continue naturally.

fun FlowControlRunner.performExpression(gesture: furhatos.gestures.Gesture?) {
    if (gesture == null) return
    furhat.gesture(gesture, async = true)
    delay(EXPRESSION_SPEECH_DELAY_MS)
}

private fun FlowControlRunner.intentLabel(text: String): String {
    val normalized = text.lowercase().trim()
    return when {
        normalized.matchesKeyword(startYesKeywords) ->
            "yes"
        normalized.matchesKeyword(startNoKeywords) ->
            "no"
        normalized.matchesKeyword(exitKeywords) ->
            "exit"
        normalized.matchesKeyword(confusedKeywords) ->
            "help"
        normalized.matchesKeyword(helpKeywords) ->
            "help"
        normalized.matchesKeyword(goBackKeywords) ->
            "back"
        normalized.matchesKeyword(skipKeywords) ->
            "skip"
        normalized.matchesKeyword(nextPageKeywords) ->
            "next"
        normalized.matchesKeyword(switchToCustomKeywords) ->
            "custom"
        normalized.matchesKeyword(listCasesKeywords) ->
            "browse"
        normalized.startsWith("select:") ->
            "select"
        normalized.startsWith("direct_description:") ->
            "custom"
        normalized in setOf(
            "yes",
            "no",
            "exit",
            "browse",
            "custom",
            "help",
            "unclear",
            "back",
            "skip",
            "next",
            "select"
        ) ->
            normalized
        else ->
            "unknown"
    }
}

private fun FlowControlRunner.gestureFromLabel(label: String): furhatos.gestures.Gesture {
    val normalized = label.lowercase().trim()
    return when {
        normalized == "yes" ||
        normalized == "no" ||
        normalized == "exit" ||
        normalized == "browse" ||
        normalized == "custom" ||
        normalized == "back" ||
        normalized.startsWith("select:") ||
        normalized.startsWith("direct_description:") ->
            Gestures.Nod(duration = 1.5)
        normalized == "next" ->
            Gestures.Smile(duration = 1.5)
        normalized == "help" ->
            Gestures.Smile(duration = 1.5)
        normalized == "skip" ->
            Gestures.Nod(duration = 1.5)
        normalized == "unclear" ->
            Gestures.BrowFrown(duration = 1.5)
        else ->
            listOf(
                Gestures.Nod(duration = 1.5),
                Gestures.Smile(duration = 1.5),
                Gestures.BrowRaise(duration = 1.5),
                Gestures.Thoughtful(duration = 1.5)
            ).random()
    }
}

fun FlowControlRunner.hostAck(text: String, gesture: furhatos.gestures.Gesture? = null) {
    performExpression(gesture ?: gestureFromLabel(intentLabel(text)))
}

// Overload for unambiguous built-in NLU responses (onResponse<Yes> / onResponse<No>)
fun FlowControlRunner.hostAckYes() { hostAck("yes", Gestures.Nod(duration = 1.5)) }
fun FlowControlRunner.hostAckNo()  { hostAck("no", Gestures.Nod(duration = 1.5)) }

// Used before any "I didn't catch that" / error reprompt in host states
fun FlowControlRunner.hostError() {
    performExpression(Gestures.BrowFrown(duration = 1.5))
}


fun GazeAversion(duration: Double = 1.0, direction: Int = random.nextInt(4)) = defineGesture("GazeAversion") {
    val dur = duration.coerceAtLeast(0.2)
    frame(0.05, dur-0.05) {
        when (direction) {
            0 -> {
                ARKitParams.EYE_LOOK_DOWN_LEFT to 0.5
                ARKitParams.EYE_LOOK_DOWN_RIGHT to 0.5
                ARKitParams.EYE_LOOK_OUT_LEFT to 0.5
                ARKitParams.EYE_LOOK_IN_RIGHT to 0.5
            }
            1 -> {
                ARKitParams.EYE_LOOK_UP_LEFT to 0.5
                ARKitParams.EYE_LOOK_UP_RIGHT to 0.5
                ARKitParams.EYE_LOOK_OUT_LEFT to 0.5
                ARKitParams.EYE_LOOK_IN_RIGHT to 0.5
            }
            2 -> {
                ARKitParams.EYE_LOOK_DOWN_LEFT to 0.5
                ARKitParams.EYE_LOOK_DOWN_RIGHT to 0.5
                ARKitParams.EYE_LOOK_OUT_RIGHT to 0.5
                ARKitParams.EYE_LOOK_IN_LEFT to 0.5
            }
            else -> {
                ARKitParams.EYE_LOOK_UP_LEFT to 0.5
                ARKitParams.EYE_LOOK_UP_RIGHT to 0.5
                ARKitParams.EYE_LOOK_OUT_RIGHT to 0.5
                ARKitParams.EYE_LOOK_IN_LEFT to 0.5
            }
        }
    }
    reset(dur)
}

// ── Patient chat expression system ───────────────────────────────────────────
// LLM embeds [EXPR:tag] markers inline in patient responses. Each marker
// triggers a gesture before the text segment it precedes.

data class ExprSegment(
    val tag: String?,
    val gesture: furhatos.gestures.Gesture?,
    val text: String
)

private val EXPR_TAG_RE = Regex("""\[EXPR:([a-z_]+)]""")

fun parseExprSegments(raw: String): List<ExprSegment> {
    val parts = EXPR_TAG_RE.split(raw)
    val tags  = EXPR_TAG_RE.findAll(raw).map { it.groupValues[1] }.toList()
    val result = mutableListOf<ExprSegment>()
    parts.forEachIndexed { i, part ->
        val text    = part.trim()
        val tag = if (i == 0) null else tags[i - 1]
        val gesture = tag?.let(::tagToGesture)
        if (text.isNotEmpty()) result.add(ExprSegment(tag, gesture, text))
    }
    return if (result.isEmpty()) listOf(ExprSegment(null, null, raw.trim())) else result
}

fun tagToGesture(tag: String): furhatos.gestures.Gesture? = when (tag) {
    "sad"         -> Gestures.ExpressSad(duration = 2.0)
    "fear"        -> Gestures.ExpressFear(duration = 2.0)
    "anger"       -> Gestures.ExpressAnger(duration = 2.0)
    "disgust"     -> Gestures.ExpressDisgust(duration = 2.0)
    "frown"       -> Gestures.BrowFrown(duration = 2.0)
    "thoughtful"  -> Gestures.Thoughtful(duration = 2.0)
    "surprise"    -> Gestures.Surprise(duration = 2.0)
    "oh"          -> Gestures.Oh(duration = 2.0)
    "gaze_away"   -> Gestures.GazeAway(duration = 2.0)
    "eyes_closed" -> Gestures.CloseEyes(duration = 1.5)
    "shake"       -> Gestures.Shake(duration = 1.5)
    "nod"         -> Gestures.Nod(duration = 1.5)
    "smile"       -> Gestures.Smile(duration = 2.0)
    else          -> null
}

// ── LLM-suggested gestures ────────────────────────────────────────────────────
// Ask the LLM to suggest an appropriate facial expression from Furhat's available gestures

fun FlowControlRunner.getGestureFromLLM(text: String): Any {
    val availableGestures = """
- Nod
- Smile
- BrowRaise
- Thoughtful
""".trimIndent()

    val label = classifyIntent(
        "The user just said something to you, and you need to respond with a facial expression.",
        text,
        "Which of these gestures would be most appropriate to perform before responding?\n$availableGestures"
    )

    return when (label.lowercase().trim()) {
        "nod" -> Gestures.Nod
        "smile" -> Gestures.Smile
        "browraise", "brow raise" -> Gestures.BrowRaise(duration = 1.5)
        "thoughtful" -> Gestures.Thoughtful(duration = 1.5)
        else -> listOf(Gestures.Nod, Gestures.Smile, Gestures.BrowRaise(duration = 1.5), Gestures.Thoughtful(duration = 1.5)).random()
    }
}
