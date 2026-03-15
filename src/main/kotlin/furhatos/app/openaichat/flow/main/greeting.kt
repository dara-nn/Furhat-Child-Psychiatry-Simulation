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
                goto(ChoosePersona(skipIntro = true))
            }
        }
        is GenerationFailed -> {
            if (attempt < 2) {
                furhat.ask("I didn't quite catch that. Could you describe what you'd like to practise in a bit more detail?")
                goto(DescribeCase(attempt = 2))
            } else {
                furhat.say("I'm having trouble with that one. Let me show you the available cases instead.")
                goto(ChoosePersona(skipIntro = true))
            }
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
        furhat.say("Hi there! I'm a training assistant for child psychiatry.")
        delay(600)
        furhat.say(
            "I can play the role of a child patient during a simulated clinical interview, " +
            "so you can practise your interview skills."
        )
        delay(600)
        furhat.ask("Would you like to give it a try?")
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

fun ChoosePersona(skipIntro: Boolean = false) = state(Parent) {

    val mainPrompt = "Would you like to browse the ready-made cases, or describe what you want to practise? Say 'browse' or 'describe'."

    val silencePhrases = listOf(
        "Still there? Say 'browse' or 'describe'.",
        "Take your time — say 'browse' to see the cases, or 'describe' to create your own.",
        "I'm here. Just say 'browse' or 'describe'."
    )

    onEntry {
        currentPersonaPage = 0
        furhat.attend(users.random)
        if (!skipIntro) {
            furhat.say(
                "I have a set of pre-made patient cases — " +
                "each one is a different child with a different background and symptoms."
            )
            delay(600)
            furhat.say(
                "You can browse those, or if you'd like to practise something specific, " +
                "I can create a custom case for you. Say 'browse' or 'describe'."
            )
        } else {
            furhat.say("Say 'browse' or 'describe'.")
        }
        furhat.listen(timeout = 10000)
    }

    onReentry {
        furhat.ask("Say 'browse' to see the pre-made options, or 'describe' to tell me what you want to practise.")
    }

    onResponse {
        val text = it.text
        when {
            // State-specific keyword tiers (fast, no LLM)
            text.matchesKeyword(listCasesKeywords)      -> goto(BrowsePersonas)
            text.matchesKeyword(switchToCustomKeywords) -> goto(DescribeCase())
            // Global keywords
            text.matchesKeyword(exitKeywords)           -> { furhat.say("Okay, goodbye."); goto(Idle) }
            text.matchesKeyword(helpKeywords) || text.matchesKeyword(confusedKeywords) -> {
                furhat.say("Say 'browse' to see the pre-made options, or 'describe' to create your own.")
                reentry()
            }
            // LLM — only for natural descriptions that should skip straight to generation
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
                    else -> furhat.ask("Say 'browse' to see the pre-made options, or 'describe' to create your own.")
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

    fun escapeStr(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    fun buildClassifyPrompt(
        prompt: String,
        speech: String,
        chunk: List<furhatos.app.openaichat.setting.Persona>
    ): String {
        val context = chunk.joinToString("\n") { "- ${it.name}: ${it.desc}" }
        val labels  = chunk.joinToString("\n") { "- select:${it.name.lowercase()}" }
        return """
You are classifying what a user said to a conversational robot.
The system just asked: "${escapeStr(prompt)}"
The user responded: "${escapeStr(speech)}"

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
            text.matchesKeyword(goBackKeywords)       -> { currentPersonaPage = 0; goto(ChoosePersona(skipIntro = true)) }
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

// ── Describe Case ─────────────────────────────────────────────────────────────

fun DescribeCase(
    attempt: Int = 1,
    noResponseCount: Int = 0,
    prefilled: String? = null
): State = state(Parent) {

    val mainPrompt =
        "Tell me what you'd like to practise. You can describe anything — " +
        "a situation, something you find challenging, a type of patient, anything at all."

    onEntry {
        when {
            prefilled != null -> {
                // Forwarded from ChoosePersona — skip asking, go straight to generation
                furhat.say("Got it. Let me put together a case for you — one moment.")
                val result = call { generatePersonaFromDescription(prefilled) } as PersonaGenerationResult
                handleGenerationResult(result, attempt = 1)
            }
            attempt == 1 -> { furhat.say(mainPrompt); furhat.listen(timeout = 10000) }
            else         -> furhat.listen(timeout = 10000)   // attempt 2: clarification question already said
        }
    }

    onResponse {
        val text = it.text

        // State-specific keywords — checked first
        when {
            text.matchesKeyword(goBackKeywords)   -> { furhat.say("No problem."); goto(ChoosePersona(skipIntro = true)) }
            text.matchesKeyword(skipKeywords)     -> { furhat.say("No problem. Let me show you the available cases."); goto(ChoosePersona(skipIntro = true)) }
            text.matchesKeyword(listCasesKeywords) -> { furhat.say("Sure."); goto(BrowsePersonas) }
            // Global keywords
            text.matchesKeyword(exitKeywords)     -> { furhat.say("Okay, goodbye."); goto(Idle) }
            text.matchesKeyword(helpKeywords)     -> {
                furhat.say(
                    "Just tell me what you'd like to practise — anything at all. " +
                    "Or I can show you the pre-made cases instead."
                )
                reentry()
            }
            else -> {
                // LLM tier — classify: vague / description / unclear→treat as description
                furhat.say("Hmm…")
                val label = call {
                    classifyIntent(
                        mainPrompt,
                        text,
                        "- vague\n- description"
                    )
                } as String
                when (label) {
                    "vague" -> furhat.ask(
                        "No problem — it can be anything. A type of situation, something you find tricky, " +
                        "a kind of patient. Whatever comes to mind."
                    )
                    else -> {
                        // "description" or "unclear" — treat as description and attempt generation
                        furhat.say("Let me put together a case for you — one moment.")
                        val result = call { generatePersonaFromDescription(text) } as PersonaGenerationResult
                        handleGenerationResult(result, attempt)
                    }
                }
            }
        }
    }

    onNoResponse {
        val silencePhrases = listOf(
            "I didn't hear anything. What would you like to practise?",
            "Take your time. Anything at all — a type of patient, a situation, a challenge.",
            "Still there? Just describe anything you'd like to work on."
        )
        if (noResponseCount < 2) {
            val phrase = pickSilencePhrase(silencePhrases, lastDescribeCaseSilence)
            lastDescribeCaseSilence = phrase
            furhat.say(phrase)
            goto(DescribeCase(attempt = attempt, noResponseCount = noResponseCount + 1))
        } else {
            furhat.say("No worries. Let me show you the available cases instead.")
            goto(ChoosePersona(skipIntro = true))
        }
    }
}
