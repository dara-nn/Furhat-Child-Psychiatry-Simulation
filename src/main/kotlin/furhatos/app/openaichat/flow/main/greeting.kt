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

// initial greeting state used when the first user appears
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
            "I’m a child-psychiatry training assistant. " +
                "I can role-play as different child patients so you can practise clinical interviews. " +
                "Would you like to start?"
        )
        reentry()
    }

    onResponse<Yes> {
        if (askedToStartSimulation) {
            goto(ChoosePersona())
        } else {
            askToStartSimulation()
        }
    }

    onResponse("yes", "yeah", "yep", "sure", "ok", "okay") {
        if (askedToStartSimulation) {
            goto(ChoosePersona())
        } else {
            askToStartSimulation()
        }
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

    // any response to the greeting triggers the self-introduction and question
    onResponse {
        if (askedToStartSimulation) {
            goto(ChoosePersona())
        } else {
            askToStartSimulation()
        }
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

fun ChoosePersona() = state(Parent) {

    fun FlowControlRunner.presentPersonas() {
        val listing = personas.joinToString(". ") { it.fullDesc }
        furhat.say(
            "Here are the available cases. $listing. " +
            "You can also describe what you want to practise and I will generate a custom case for you."
        )
        reentry()
    }

    onEntry {
        furhat.attend(users.random)
        presentPersonas()
    }

    onReentry {
        val names = personas.dropLast(1).joinToString(", ") { it.name } + ", or " + personas.last().name
        furhat.ask("Who would you like to talk to? Say the name: $names. Or say 'describe a case' to create your own.")
    }

    onResponse("can you present them again", "could you repeat") {
        presentPersonas()
    }

    val describeKeywords = listOf(
        "describe", "create", "generate", "custom", "make a case", "my own",
        "design", "build a case", "i want something", "can you make"
    )

    onResponse("describe a case", "create a case", "generate a case", "custom case", "make a case", "my own case") {
        goto(DescribeCase())
    }

    fun FlowControlRunner.startPersona(persona: Persona) {
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

    for (persona in personas) {
        onResponse(persona.intent) { startPersona(persona) }
    }

    onResponse {
        val text = it.text.lowercase()
        // Check for describe/generate intent first
        if (describeKeywords.any { kw -> text.contains(kw) }) {
            goto(DescribeCase())
            return@onResponse
        }
        // Substring fallback — handles "I want to talk to Noah", "Lena", etc.
        val matched = personas.find { persona ->
            text.contains(persona.name.lowercase()) ||
            persona.otherNames.any { alias -> text.contains(alias.lowercase()) }
        }
        if (matched != null) {
            startPersona(matched)
        } else {
            val names = personas.dropLast(1).joinToString(", ") { it.name } + ", or " + personas.last().name
            furhat.ask("Sorry, I didn't catch that. Please say one of the names: $names. Or say 'describe a case' to create your own.")
        }
    }

    onNoResponse {
        val names = personas.dropLast(1).joinToString(", ") { it.name } + ", or " + personas.last().name
        val reengage = listOf("Hello there.", "Hi there.", "Are you still there?").random()
        furhat.ask("$reengage Please say one of the names: $names.")
    }
}

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
