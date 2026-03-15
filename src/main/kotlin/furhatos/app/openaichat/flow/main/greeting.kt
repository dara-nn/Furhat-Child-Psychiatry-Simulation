package furhatos.app.openaichat.flow

import furhatos.app.openaichat.flow.chatbot.GeneratedPersona
import furhatos.app.openaichat.flow.chatbot.GenerationFailed
import furhatos.app.openaichat.flow.chatbot.MainChat
import furhatos.app.openaichat.flow.chatbot.NeedsClarification
import furhatos.app.openaichat.flow.chatbot.PersonaGenerationResult
import furhatos.app.openaichat.flow.chatbot.callGeminiText
import furhatos.app.openaichat.flow.chatbot.classifyIntent
import furhatos.app.openaichat.flow.chatbot.generatePersonaFromDescription
import furhatos.app.openaichat.setting.Persona
import furhatos.app.openaichat.setting.hostPersona
import furhatos.app.openaichat.setting.personas
import furhatos.flow.kotlin.*
import furhatos.nlu.common.*
import furhatos.records.Location

var currentPersona: Persona = hostPersona
var currentPersonaPage = 0

// Silence reprompt tracking — one var per state that creates new instances each call
var lastInitialInteractionSilence = ""
var lastChoosePersonaSilence      = ""
var lastDescribeCaseSilence       = ""

// ── Shared helpers ─────────────────────────────────────────────────────────────

/**
 * Pre-transition announcement (system voice, before face switch).
 * Then navigates to MainChat where the face switch happens.
 */
internal fun FlowControlRunner.startPersona(persona: Persona) {
    furhat.say("Alright, you're about to meet ${persona.name}. Say 'stop session' at any time to end.")
    currentPersona = persona
    goto(MainChat)
}

/** Shared result handler for both DescribeCase paths (normal + prefilled). */
private fun FlowControlRunner.handleGenerationResult(result: PersonaGenerationResult, attempt: Int) {
    when (result) {
        is GeneratedPersona    -> startPersona(result.persona)
        is NeedsClarification  -> {
            if (attempt < 2) {
                furhat.say(result.question)
                goto(DescribeCase(attempt = 2))
            } else {
                furhat.say("I'm not sure I understood well enough. Let me show you the available cases instead.")
                goto(ChoosePersona())
            }
        }
        is GenerationFailed -> {
            furhat.say("Something went wrong there. Let me show you the available cases.")
            goto(ChoosePersona())
        }
    }
}

// ── Initial greeting ──────────────────────────────────────────────────────────

val InitialInteraction: State = state(Parent) {

    val silencePhrases = listOf(
        "Are you still there? Would you like to give it a try?",
        "Hello? I'm here whenever you're ready.",
        "Just let me know if you'd like to get started."
    )

    onEntry {
        furhat.say("Hi there!")
        delay(900)
        furhat.ask(
            "I'm here to help you practise clinical interviews. " +
            "I can play different child patients for you to talk to. " +
            "Want to give it a try?"
        )
    }

    onReentry {
        furhat.ask("Would you like to try a practice interview?")
    }

    onResponse<Yes> { goto(ChoosePersona()) }
    onResponse<No>  { furhat.say("Okay, no worries. I'll be here if you change your mind."); goto(Idle) }

    onResponse {
        val text = it.text
        when {
            // State-specific keywords checked first
            text.matchesKeyword(startYesKeywords)  -> goto(ChoosePersona())
            text.matchesKeyword(startNoKeywords)   -> {
                furhat.say("Okay, no worries. I'll be here if you change your mind.")
                goto(Idle)
            }
            text.matchesKeyword(confusedKeywords)  -> {
                furhat.say(
                    "I'm a practice tool — I play child patients so you can rehearse interviews. " +
                    "Want to try?"
                )
                reentry()
            }
            // Global keywords
            text.matchesKeyword(exitKeywords)      -> { furhat.say("Okay, goodbye."); goto(Idle) }
            text.matchesKeyword(helpKeywords)      -> {
                furhat.say(
                    "I'm a training robot. I can pretend to be a child patient so you can " +
                    "practise your clinical interview skills. Let me know if you'd like to get started."
                )
                reentry()
            }
            // All failed — do NOT treat as affirmative
            else -> furhat.ask("Sorry, I didn't quite get that. Would you like to try a practice interview?")
        }
    }

    onNoResponse {
        val phrase = pickSilencePhrase(silencePhrases, lastInitialInteractionSilence)
        lastInitialInteractionSilence = phrase
        furhat.ask(phrase)
    }
}

// ── Choose Persona ────────────────────────────────────────────────────────────

fun ChoosePersona() = state(Parent) {

    val mainPrompt = "I have some ready-made cases, or I can create a custom one based on what you want to practise. Which would you prefer?"

    val silencePhrases = listOf(
        "Would you like to browse the existing cases, or tell me what you'd like to practise?",
        "Still there? You can pick from the list or describe what you need.",
        "Take your time — let me know if you want to see the cases or describe something."
    )

    onEntry {
        currentPersonaPage = 0
        furhat.attend(users.random)
        furhat.ask(mainPrompt)
    }

    onReentry {
        furhat.ask("Would you like to browse the existing cases, or tell me what you'd like to practise?")
    }

    onResponse {
        val text = it.text
        when {
            // Global keywords
            text.matchesKeyword(exitKeywords) -> { furhat.say("Okay, goodbye."); goto(Idle) }
            text.matchesKeyword(helpKeywords) -> {
                furhat.say(
                    "I can show you a list of pre-made patient cases, or you can describe what " +
                    "you'd like to practise and I'll create one for you."
                )
                reentry()
            }
            // LLM tier — thinking cue before API call
            else -> {
                furhat.say("Hmm…")
                val label = call {
                    classifyIntent(
                        mainPrompt,
                        text,
                        "- browse\n- custom\n- direct_description:[the training need description]"
                    )
                } as String
                when {
                    label == "browse"                        -> goto(BrowsePersonas)
                    label == "custom"                        -> goto(DescribeCase())
                    label.startsWith("direct_description:") -> {
                        val description = label.removePrefix("direct_description:").trim()
                        goto(DescribeCase(prefilled = description))
                    }
                    else -> furhat.ask("Would you like to browse the existing cases, or tell me what you'd like to practise?")
                }
            }
        }
    }

    onNoResponse {
        val phrase = pickSilencePhrase(silencePhrases, lastChoosePersonaSilence)
        lastChoosePersonaSilence = phrase
        furhat.ask(phrase)
    }
}

// ── Browse Personas ───────────────────────────────────────────────────────────

val BrowsePersonas: State by lazy {
state(Parent) {

    val chunkSize = 3
    var lastPrompt       = ""
    var lastSilencePhrase = ""

    fun buildListing(chunk: List<furhatos.app.openaichat.setting.Persona>): String {
        val connectors = listOf("First, there's", "Then there's", "And there's")
        return chunk.mapIndexed { i, p ->
            "${connectors.getOrElse(i) { "And there's" }} ${p.name} — ${p.desc}."
        }.joinToString(" ")
    }

    fun buildClassifyPrompt(
        prompt: String,
        speech: String,
        chunk: List<furhatos.app.openaichat.setting.Persona>
    ): String {
        val context = chunk.joinToString("\n") { "- ${it.name}: ${it.desc}" }
        val labels  = chunk.joinToString("\n") { "- select:${it.name.lowercase()}" }
        return """
You are classifying what a user said to a conversational robot.
The system just asked: "$prompt"
The user responded: "$speech"

The currently displayed cases are:
$context

Classify as exactly ONE of:
$labels
- unclear

Respond with ONLY the label.
""".trimIndent()
    }

    fun findPersona(name: String): List<furhatos.app.openaichat.setting.Persona> {
        val q = name.lowercase().trim()
        return personas.filter { p ->
            p.name.lowercase() == q ||
            p.name.lowercase().contains(q) ||
            p.otherNames.any { alias -> alias.lowercase() == q }
        }
    }

    onEntry {
        val chunk  = personas.drop(currentPersonaPage * chunkSize).take(chunkSize)
        val isLast = (currentPersonaPage + 1) * chunkSize >= personas.size
        val prompt = if (isLast)
            "Those are all the cases I have. Any of those sound good? Just say a name."
        else
            "Any of those sound good? Just say a name, or I can keep going."
        lastPrompt = prompt
        furhat.say(buildListing(chunk))
        furhat.ask(prompt)
    }

    onReentry {
        val chunk  = personas.drop(currentPersonaPage * chunkSize).take(chunkSize)
        val isLast = (currentPersonaPage + 1) * chunkSize >= personas.size
        val names  = when (chunk.size) {
            1    -> chunk.first().name
            else -> chunk.dropLast(1).joinToString(", ") { it.name } + ", or " + chunk.last().name
        }
        val prompt = if (isLast) "Which one would you like? $names."
                     else        "Which one would you like? $names. Or I can keep going."
        lastPrompt = prompt
        furhat.ask(prompt)
    }

    onNoResponse {
        val phrases = listOf(
            "Are you still there? Which case would you like?",
            "Take your time — just say a name when you're ready.",
            "I'm here. Just say a name to pick a case."
        )
        val phrase = pickSilencePhrase(phrases, lastSilencePhrase)
        lastSilencePhrase = phrase
        furhat.ask(phrase)
    }

    onResponse {
        val text  = it.text
        val chunk = personas.drop(currentPersonaPage * chunkSize).take(chunkSize)
        when {
            // State-specific keywords — checked first
            text.matchesKeyword(nextPageKeywords) -> {
                val isLast = (currentPersonaPage + 1) * chunkSize >= personas.size
                if (isLast) {
                    furhat.say("Those are all the cases I have. Here they are again from the beginning.")
                    currentPersonaPage = 0
                } else {
                    currentPersonaPage++
                }
                goto(BrowsePersonas)
            }
            text.matchesKeyword(goBackKeywords)       -> { currentPersonaPage = 0; goto(ChoosePersona()) }
            text.matchesKeyword(switchToCustomKeywords) -> goto(DescribeCase())
            // Global keywords
            text.matchesKeyword(exitKeywords)         -> { furhat.say("Okay, goodbye."); goto(Idle) }
            text.matchesKeyword(helpKeywords)         -> {
                furhat.say(
                    "You can pick a patient by saying their name. " +
                    "If you'd like to hear more cases, just let me know, or you can go back."
                )
                reentry()
            }
            // LLM tier — classify against personas on current page
            else -> {
                furhat.say("Hmm…")
                val label = call { callGeminiText(buildClassifyPrompt(lastPrompt, text, chunk)) } as String
                if (label.startsWith("select:")) {
                    val nameGuess = label.removePrefix("select:").trim()
                    val matches   = findPersona(nameGuess)
                    when (matches.size) {
                        0    -> {
                            val names = chunk.joinToString(", ") { it.name }
                            furhat.ask("I didn't catch which case you meant. The options on this page are $names.")
                        }
                        1    -> startPersona(matches.first())
                        else -> furhat.ask(
                            "Did you mean ${matches[0].name} or ${matches[1].name}?"
                        )
                    }
                } else {
                    val names = chunk.joinToString(", ") { it.name }
                    furhat.ask("I didn't catch which case you meant. The options on this page are $names.")
                }
            }
        }
    }
}
}

// ── Describe Case — custom persona generation ─────────────────────────────────

fun DescribeCase(attempt: Int = 1, noResponseCount: Int = 0): State = state(Parent) {

    val nonAnswerKeywords = listOf(
        "don't know", "dont know", "not sure", "i'm thinking", "im thinking",
        "hmm", "um", "uh", "let me think", "good question", "i have no idea",
        "no idea", "pass"
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

    onResponse("go back", "back", "list cases", "show me the list", "existing cases",
               "choose a case", "available cases", "show cases", "pick a case") {
        furhat.say("No problem. Let me show you the available cases.")
        goto(ChoosePersona())
    }

    onResponse {
        val text = it.text.lowercase()
        val originalText = it.text

        if (listKeywords.any { kw -> text.contains(kw) }) {
            furhat.say("No problem. Let me show you the available cases.")
            goto(ChoosePersona())
            return@onResponse
        }

        if (nonAnswerKeywords.any { kw -> text.contains(kw) }) {
            furhat.ask(
                "No problem. For example — you could say " +
                "'resistant teenager', 'separation anxiety in a young child', " +
                "or 'I struggle with patients who minimise their symptoms'."
            )
            return@onResponse
        }

        if (text.contains("never mind") || text.contains("nevermind") || text.contains("skip")) {
            furhat.say("No problem. Let me show you the available cases.")
            goto(ChoosePersona())
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
        if (noResponseCount < 2) {
            furhat.ask("I didn't hear anything. What would you like to practise?")
            goto(DescribeCase(attempt = attempt, noResponseCount = noResponseCount + 1))
        } else {
            furhat.say("No problem. Let me show you the available cases.")
            goto(ChoosePersona())
        }
    }
}
