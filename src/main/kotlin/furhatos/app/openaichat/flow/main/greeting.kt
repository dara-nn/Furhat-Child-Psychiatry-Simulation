package furhatos.app.openaichat.flow

import furhatos.app.openaichat.flow.chatbot.GeneratedPersona
import furhatos.app.openaichat.flow.chatbot.GenerationFailed
import furhatos.app.openaichat.flow.chatbot.MainChat
import furhatos.app.openaichat.flow.chatbot.NeedsClarification
import furhatos.app.openaichat.flow.chatbot.PersonaGenerationResult
import furhatos.app.openaichat.flow.chatbot.generatePersonaFromDescription
import furhatos.app.openaichat.setting.Persona
import furhatos.app.openaichat.setting.hostPersona
import furhatos.app.openaichat.setting.personas
import furhatos.flow.kotlin.*
import furhatos.nlu.common.*
import furhatos.records.Location

var currentPersona: Persona = hostPersona
var askedToStartSimulation = false

// ── Shared helper ─────────────────────────────────────────────────────────────

private fun FlowControlRunner.startPersona(persona: Persona) {
    val intro = listOf(
        "Okay, I will let you talk to ${persona.name}.",
        "Okay, let's have a chat with ${persona.name}.",
        "Sure, we can talk to ${persona.name}."
    ).random()
    val outro = listOf(
        "When you want to end the conversation, just say goodbye, stop, or that's enough.",
        "To end the conversation, just say goodbye or stop."
    ).random()
    furhat.say("$intro $outro")
    currentPersona = persona
    goto(MainChat)
}

// ── Initial greeting ──────────────────────────────────────────────────────────

val InitialInteraction: State = state(Parent) {

    fun FlowControlRunner.reengageGreeting(): String {
        return listOf(
            "Hello.",
            "Hello there.",
            "Hi there.",
            "Are you still there?"
        ).random()
    }

    onEntry {
        askedToStartSimulation = false
        furhat.say("Hello")
        reentry()
    }

    onReentry {
        furhat.listen()
    }

    fun FlowControlRunner.askToStartSimulation() {
        askedToStartSimulation = true
        furhat.say(
            "I'm a child-psychiatry training assistant. " +
                "I can role-play as different child patients so you can practise clinical interviews. " +
                "Would you like to start?"
        )
        reentry()
    }

    onResponse<Yes> {
        if (askedToStartSimulation) goto(ChoosePersona()) else askToStartSimulation()
    }

    onResponse("yes", "yeah", "yep", "sure", "ok", "okay") {
        if (askedToStartSimulation) goto(ChoosePersona()) else askToStartSimulation()
    }

    onResponse<No> {
        if (askedToStartSimulation) {
            furhat.say("Okay, let me know if you change your mind.")
            goto(Idle)
        } else {
            askToStartSimulation()
        }
    }

    onResponse("no", "nope", "not now") {
        if (askedToStartSimulation) {
            furhat.say("Okay, let me know if you change your mind.")
            goto(Idle)
        } else {
            askToStartSimulation()
        }
    }

    onResponse {
        if (askedToStartSimulation) goto(ChoosePersona()) else askToStartSimulation()
    }

    onNoResponse {
        if (askedToStartSimulation) {
            furhat.ask("${reengageGreeting()} Would you like to start?")
        } else {
            furhat.say(reengageGreeting())
            askToStartSimulation()
        }
    }
}

// ── Choose Persona — initial two-way branch ───────────────────────────────────

fun ChoosePersona() = state(Parent) {

    val listKeywords     = listOf("list", "available", "show", "pre", "existing", "case", "pick", "choose", "select")
    val describeKeywords = listOf("describe", "create", "generate", "custom", "make", "my own", "design", "build")

    onEntry {
        furhat.attend(users.random)
        furhat.ask(
            "Would you like to choose from the available cases, " +
            "or describe what you'd like to practise and I'll create a custom one for you?"
        )
    }

    onReentry {
        furhat.ask(
            "Say 'list cases' to hear the available cases, " +
            "or 'describe' to tell me what you'd like to practise."
        )
    }

    onResponse("list cases", "show cases", "available cases", "pick a case", "choose a case",
               "the list", "existing cases", "show me the cases", "what cases are there") {
        goto(BrowsePersonas())
    }

    onResponse("describe", "describe a case", "create a case", "generate a case",
               "custom case", "my own", "my own case", "make a case") {
        goto(DescribeCase())
    }

    onResponse {
        val text = it.text.lowercase()
        when {
            listKeywords.any     { kw -> text.contains(kw) } -> goto(BrowsePersonas())
            describeKeywords.any { kw -> text.contains(kw) } -> goto(DescribeCase())
            else -> reentry()
        }
    }

    onNoResponse { reentry() }
}

// ── Browse Personas — paginated listing ───────────────────────────────────────

fun BrowsePersonas(page: Int = 0) = state(Parent) {

    val chunkSize = 3

    onEntry {
        val chunk  = personas.drop(page * chunkSize).take(chunkSize)
        val isLast = (page + 1) * chunkSize >= personas.size

        val listing = chunk.joinToString(". ") { it.fullDesc }
        val prompt  = if (isLast)
            "That's all of the available cases. Say a name to choose one."
        else
            "Say a name to choose one, or say 'more' to hear the next cases."

        furhat.say(listing)
        furhat.ask(prompt)
    }

    onReentry {
        val chunk = personas.drop(page * chunkSize).take(chunkSize)
        val names = chunk.dropLast(1).joinToString(", ") { it.name } + ", or " + chunk.last().name
        furhat.ask("Which case would you like? $names. Or say 'more'.")
    }

    onResponse("more", "next", "continue", "keep going", "show more") {
        val nextPage = if ((page + 1) * chunkSize >= personas.size) 0 else page + 1
        goto(BrowsePersonas(nextPage))
    }

    onResponse("start over", "from the beginning", "go back", "restart") {
        goto(BrowsePersonas(0))
    }

    onResponse("describe", "describe a case", "create a case", "my own", "custom", "make a case") {
        goto(DescribeCase())
    }

    for (persona in personas) {
        onResponse(persona.intent) { startPersona(persona) }
    }

    onResponse {
        val text = it.text.lowercase()
        val matched = personas.find { p ->
            text.contains(p.name.lowercase()) ||
            p.otherNames.any { alias -> text.contains(alias.lowercase()) }
        }
        if (matched != null) {
            startPersona(matched)
        } else {
            reentry()
        }
    }

    onNoResponse {
        val reengage = listOf("Hello there.", "Hi there.", "Are you still there?").random()
        furhat.ask("$reengage Which case would you like?")
    }
}

// ── Describe Case — custom persona generation ─────────────────────────────────

fun DescribeCase(attempt: Int = 1) = state(Parent) {

    val nonAnswerKeywords = listOf(
        "don't know", "dont know", "not sure", "i'm thinking", "im thinking",
        "hmm", "um", "uh", "let me think", "good question", "i have no idea",
        "no idea", "skip", "never mind", "nevermind", "pass"
    )

    onEntry {
        if (attempt == 1) {
            furhat.ask(
                "Tell me what you'd like to practise. For example: " +
                "you could say 'I struggle with resistant teenagers', " +
                "'I want a Vietnamese background', or " +
                "'I find it hard to interview young boys with depression'."
            )
        } else {
            furhat.listen()
        }
    }

    onResponse {
        val text = it.text.lowercase()
        val originalText = it.text

        if (nonAnswerKeywords.any { kw -> text.contains(kw) }) {
            furhat.ask(
                "No problem. For example — you could say " +
                "'resistant teenager', 'separation anxiety in a young child', " +
                "or 'I struggle with patients who minimise their symptoms'."
            )
            return@onResponse
        }

        furhat.say("Got it. Generating your custom case, please wait a moment.")
        val result = call { generatePersonaFromDescription(originalText) } as PersonaGenerationResult

        when (result) {
            is GeneratedPersona -> {
                furhat.say(
                    "Done. Meet ${result.persona.name} — ${result.persona.desc}. " +
                    "When you want to end the conversation, just say goodbye or stop."
                )
                currentPersona = result.persona
                goto(MainChat)
            }
            is NeedsClarification -> {
                if (attempt < 2) {
                    furhat.say(result.question)
                    goto(DescribeCase(attempt = 2))
                } else {
                    furhat.say(
                        "I'm not sure I understood your training need well enough to generate a case. " +
                        "Let me show you the available cases instead."
                    )
                    goto(ChoosePersona())
                }
            }
            is GenerationFailed -> {
                furhat.say("I'm sorry, something went wrong generating the case. Let me show you the available cases.")
                goto(ChoosePersona())
            }
        }
    }

    onNoResponse {
        furhat.ask("I didn't hear anything. What would you like to practise?")
    }
}
