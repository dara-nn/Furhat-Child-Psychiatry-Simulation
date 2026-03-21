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
var lastInitialInteractionSilence  = ""
var lastChooseModeSilence       = ""
var lastDescribeCaseSilence        = ""
var choosePersonaNoResponseCount   = 0
var browsePersonasNoResponseCount  = 0

// ── Shared helpers ─────────────────────────────────────────────────────────────

/**
 * Pre-transition announcement (system voice, before face switch).
 * Then navigates to MainChat where the face switch happens.
 */
internal fun FlowControlRunner.startPersona(persona: Persona) {
    furhat.say("Alright, you're about to meet ${persona.name}, ${persona.desc}. Say stop session, or, exit, at any time to end.")
    currentPersona = persona
    goto(MainChat)
}

/** Shared result handler for both DescribeCase paths (normal + prefilled). */
private fun FlowControlRunner.handleGenerationResult(
    result: PersonaGenerationResult,
    attempt: Int,
    description: String? = null
) {
    when (result) {
        is GeneratedPersona    -> startPersona(result.persona)
        is NeedsClarification  -> {
            if (attempt < 2) {
                furhat.say(result.question)
                goto(DescribeCase(attempt = 2))
            } else {
                furhat.say("I'm not sure I understood well enough. Let me show you the available cases instead.")
                goto(ChooseMode(skipIntro = true))
            }
        }
        is GenerationFailed -> {
            if (attempt < 2 && description != null) {
                furhat.say("Having a little trouble — let me try once more.")
                val retryResult = call { generatePersonaFromDescription(description) } as PersonaGenerationResult
                handleGenerationResult(retryResult, attempt = 2)
            } else {
                furhat.say(
                    "I'm having some trouble creating a case right now — you could try again in a moment. " +
                    "For now, let me show you the available cases."
                )
                goto(BrowsePersonas)
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
        furhat.say("Would you like to give it a try?")
        println(">>> ROBOT_LISTENING: INITIAL_INTERACTION")
        furhat.listen(timeout = 10000, endSil = 5000)
    }

    onReentry {
        furhat.say("Would you like to try a practice interview?")
        println(">>> ROBOT_LISTENING: INITIAL_INTERACTION")
        furhat.listen(timeout = 10000, endSil = 5000)
    }

    onResponse<Yes> { goto(ChooseMode()) }
    onResponse<No>  { furhat.say("Okay, no worries. I'll be here if you change your mind."); goto(Idle) }

    onResponse {
        val text = it.text
        when {
            // State-specific keywords checked first
            text.matchesKeyword(startYesKeywords)  -> goto(ChooseMode())
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
            // LLM fallback — context-aware classification for natural phrasing
            else -> {
                furhat.say("Hmm…")
                val label = call {
                    classifyIntent(
                        "Would you like to give it a try?",
                        text,
                        "- yes\n- no\n- unclear"
                    )
                } as String
                when (label) {
                    "yes" -> goto(ChooseMode())
                    "no"  -> { furhat.say("Okay, no worries. I'll be here if you change your mind."); goto(Idle) }
                    else  -> { furhat.say("Sorry, I didn't quite get that. Would you like to try a practice interview?"); println(">>> ROBOT_LISTENING: INITIAL_INTERACTION"); furhat.listen(timeout = 10000, endSil = 5000) }
                }
            }
        }
    }

    onNoResponse {
        val phrase = pickSilencePhrase(silencePhrases, lastInitialInteractionSilence)
        lastInitialInteractionSilence = phrase
        furhat.say(phrase)
        println(">>> ROBOT_LISTENING: INITIAL_INTERACTION")
        furhat.listen(timeout = 10000, endSil = 5000)
    }
}

// ── Choose Mode ────────────────────────────────────────────────────────────

fun ChooseMode(skipIntro: Boolean = false): State = state(Parent) {

    val mainPrompt = "Would you like to browse the ready-made cases, or create a custom case?"

    val silencePhrases = listOf(
        "Still there? You can browse the ready-made cases, or, create a custom case — which would you prefer?",
        "Take your time — choose between - browsing the available cases, or, creating your own.",
        "I'm here. Want to see what's available, or, create your own case?"
    )

    onEntry {
        choosePersonaNoResponseCount = 0
        currentPersonaPage = 0
        furhat.attend(users.random)
        if (!skipIntro) {
            furhat.say(
                "I have a set of pre-made patient cases, you can browse those, or, I can build a custom case based on what you want to practise."
            )
            delay(400)
            furhat.say("Would you like to browse, or, create a custom case?")
        } else {
            furhat.say("Would you like to browse the ready-made cases, or create a custom case?")
        }
        println(">>> ROBOT_LISTENING: CHOOSE_MODE")
        furhat.listen(timeout = 10000, endSil = 5000)
    }

    onReentry {
        furhat.say("Want to look through the available cases, or, create one yourself?")
        println(">>> ROBOT_LISTENING: CHOOSE_MODE")
        furhat.listen(timeout = 10000, endSil = 5000)
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
                reentry()
            }
            // LLM — full intent classifier for anything not caught by keywords
            else -> {
                furhat.say("Hmm…")
                val label = call {
                    classifyIntent(
                        mainPrompt,
                        text,
                        "- browse\n- custom\n- direct_description:[the training need description]\n- exit\n- unclear"
                    )
                } as String
                when {
                    label == "browse"                        -> goto(BrowsePersonas)
                    label == "custom"                        -> goto(DescribeCase())
                    label.startsWith("direct_description:") -> {
                        val description = label.removePrefix("direct_description:").trim()
                        goto(DescribeCase(prefilled = description))
                    }
                    label == "exit"                          -> { furhat.say("Okay, goodbye."); goto(Idle) }
                    else -> { furhat.say("Would you like to browse the ready-made cases, or create a custom case?"); println(">>> ROBOT_LISTENING: CHOOSE_MODE"); furhat.listen(timeout = 10000, endSil = 5000) }
                }
            }
        }
    }

    onNoResponse {
        choosePersonaNoResponseCount++
        if (choosePersonaNoResponseCount < 3) {
            val phrase = pickSilencePhrase(silencePhrases, lastChooseModeSilence)
            lastChooseModeSilence = phrase
            furhat.say(phrase)
            println(">>> ROBOT_LISTENING: CHOOSE_MODE")
            furhat.listen(timeout = 10000, endSil = 5000)
        } else {
            furhat.say("I'll go quiet for now. Just say something whenever you're ready to start.")
            goto(Idle)
        }
    }
}

// ── Browse Personas ───────────────────────────────────────────────────────────

val BrowsePersonas: State by lazy {
state(Parent) {

    val chunkSize = 3
    val visiblePersonas = personas.take(6)
    var lastPrompt        = ""
    var lastSilencePhrase = ""

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
        val selectLabels = chunk.joinToString("\n") { "- select:${it.name.lowercase()}" }
        return """
You are classifying what a user said to a conversational robot used for clinical training.
The system just asked: "${escapeStr(prompt)}"
The user responded: "${escapeStr(speech)}"

The currently displayed cases are:
$context

Classify as exactly ONE of:
$selectLabels
- next (wants to see more cases)
- back (wants to go back or return to the previous menu)
- custom (wants to describe or create their own case)
- exit (wants to stop, quit, leave, or is done practising)
- help (confused or asking for instructions)
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

    fun findPersonaInText(text: String): List<furhatos.app.openaichat.setting.Persona> {
        val normalized = text.lowercase().trim()
        return visiblePersonas.filter { p ->
            normalized.contains(p.name.lowercase()) ||
            p.otherNames.any { alias -> normalized.contains(alias.lowercase()) }
        }
    }

    onEntry {
        browsePersonasNoResponseCount = 0
        val chunk   = visiblePersonas.drop(currentPersonaPage * chunkSize).take(chunkSize)
        val isLast  = (currentPersonaPage + 1) * chunkSize >= visiblePersonas.size
        val isFirst = currentPersonaPage == 0

        if (isFirst) {
            furhat.say("I have ${visiblePersonas.size} patient cases in total.")
            delay(400)
        }

        chunk.forEachIndexed { i, p ->
            val eName = "${p.name}"
            val line = when {
                i == 0              -> "There's $eName — ${p.desc}."
                i == chunk.size - 1 -> "And there's $eName — ${p.desc}."
                else                -> "Then there's $eName — ${p.desc}."
            }
            furhat.say(line)
            if (i < chunk.size - 1) delay(200)
        }

        val namesList = chunk.joinToString(", ") { "— ${it.name} —" }
        val cue = when {
            isFirst && isLast -> "$namesList — say a name to pick one."
            isFirst           -> "$namesList — say a name to pick one, or — more — to hear more."
            isLast            -> "$namesList — say a name to pick one, or — back — for the previous ones."
            else              -> "$namesList — say a name, — more — for more, or — back — for the previous ones."
        }
        lastPrompt = cue
        furhat.say(cue)
        println(">>> ROBOT_LISTENING: BROWSE_PERSONAS")
        furhat.listen(timeout = 10000, endSil = 5000)
    }

    onReentry {
        val chunk  = visiblePersonas.drop(currentPersonaPage * chunkSize).take(chunkSize)
        val isLast = (currentPersonaPage + 1) * chunkSize >= visiblePersonas.size
        val names  = if (chunk.size == 1) "— ${chunk.first().name} —"
                     else chunk.dropLast(1).joinToString(", ") { "— ${it.name} —" } + ", or — ${chunk.last().name} —"
        val prompt = if (isLast) "Which one? $names."
                     else        "Which one? $names. Or say — more —."
        lastPrompt = prompt
        furhat.say(prompt)
        println(">>> ROBOT_LISTENING: BROWSE_PERSONAS")
        furhat.listen(timeout = 10000, endSil = 5000)
    }

    onNoResponse {
        browsePersonasNoResponseCount++
        if (browsePersonasNoResponseCount < 3) {
            val phrases = listOf(
                "Are you still there? Just say a name to pick a case.",
                "Take your time — say a name, 'more', or 'back'.",
                "I'm here. Just say a name to pick one."
            )
            val phrase = pickSilencePhrase(phrases, lastSilencePhrase)
            lastSilencePhrase = phrase
            furhat.say(phrase)
            println(">>> ROBOT_LISTENING: BROWSE_PERSONAS")
            furhat.listen(timeout = 10000, endSil = 5000)
        } else {
            furhat.say("I'll go quiet for now. Just say something whenever you're ready to start.")
            goto(Idle)
        }
    }

    onResponse {
        val text = it.text
        when {
            text.matchesKeyword(confusedKeywords)   -> goto(BrowsePersonas)
            text.matchesKeyword(nextPageKeywords) -> {
                val isLast = (currentPersonaPage + 1) * chunkSize >= visiblePersonas.size
                if (isLast) {
                    furhat.say("Those are all the patient cases. Starting again from the beginning.")
                    currentPersonaPage = 0
                } else {
                    currentPersonaPage++
                }
                goto(BrowsePersonas)
            }
            text.matchesKeyword(goBackKeywords) -> {
                if (currentPersonaPage > 0) {
                    currentPersonaPage--
                    goto(BrowsePersonas)
                } else {
                    goto(ChooseMode(skipIntro = true))
                }
            }
            text.matchesKeyword(switchToCustomKeywords) -> goto(DescribeCase())
            text.matchesKeyword(exitKeywords) || text.matchesKeyword(startNoKeywords) -> { furhat.say("Okay, goodbye then."); goto(Idle) }
            text.matchesKeyword(helpKeywords)           -> {
                furhat.say("Say a name to pick a case, 'more' for more, or 'back' to go back.")
                reentry()
            }
            else -> {
                // Try direct name scan across ALL personas first (no LLM, cross-group)
                val directMatches = findPersonaInText(text)
                when (directMatches.size) {
                    1    -> startPersona(directMatches.first())
                    else -> {
                        furhat.say("Hmm…")
                        val chunk = visiblePersonas.drop(currentPersonaPage * chunkSize).take(chunkSize)
                        val label = call { callGeminiText(buildClassifyPrompt(lastPrompt, text, chunk)) } as String
                        when {
                            label.startsWith("select:") -> {
                                val nameGuess = label.removePrefix("select:").trim()
                                val matches   = findPersona(nameGuess)
                                when (matches.size) {
                                    1    -> startPersona(matches.first())
                                    0    -> { val names = chunk.joinToString(", ") { it.name }; furhat.say("I didn't catch that. These cases are $names."); println(">>> ROBOT_LISTENING: BROWSE_PERSONAS"); furhat.listen(timeout = 10000, endSil = 5000) }
                                    else -> { furhat.say("Did you mean ${matches[0].name} or ${matches[1].name}?"); println(">>> ROBOT_LISTENING: BROWSE_PERSONAS"); furhat.listen(timeout = 10000, endSil = 5000) }
                                }
                            }
                            label == "next" -> {
                                val isLast = (currentPersonaPage + 1) * chunkSize >= visiblePersonas.size
                                if (isLast) { furhat.say("Those are all the cases. Starting again from the beginning."); currentPersonaPage = 0 } else currentPersonaPage++
                                goto(BrowsePersonas)
                            }
                            label == "back" -> {
                                if (currentPersonaPage > 0) { currentPersonaPage--; goto(BrowsePersonas) } else goto(ChooseMode(skipIntro = true))
                            }
                            label == "custom" -> goto(DescribeCase())
                            label == "exit"   -> { furhat.say("Okay, goodbye then."); goto(Idle) }
                            label == "help"   -> { furhat.say("Say a name to pick a case, 'more' for more, or 'back' to go back."); reentry() }
                            else -> { val names = chunk.joinToString(", ") { it.name }; furhat.say("I didn't catch that. These cases are $names."); println(">>> ROBOT_LISTENING: BROWSE_PERSONAS"); furhat.listen(timeout = 10000, endSil = 5000) }
                        }
                    }
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
        "I'll create a patient based on what you describe. " +
        "For example: a withdrawn 10-year-old who stopped talking after his parents separated. " +
        "You can add as much as you like — age, personality, background, how they behave. " +
        "What would you like to practise?"

    onEntry {
        when {
            prefilled != null -> {
                // Forwarded from ChooseMode — skip asking, go straight to generation
                println("DescribeCase.onEntry: prefilled='$prefilled'")
                furhat.say("Got it. Let me put together a case for you — one moment.")
                println("DescribeCase.onEntry: entering call block")
                val result = call { generatePersonaFromDescription(prefilled) } as PersonaGenerationResult
                println("DescribeCase.onEntry: result=$result")
                handleGenerationResult(result, attempt = 1, description = prefilled)
            }
            attempt == 1 -> { furhat.say(mainPrompt); println(">>> ROBOT_LISTENING: DESCRIBE_CASE"); furhat.listen(timeout = 20000, endSil = 5000, maxSpeech = 60000) }
            else         -> { println(">>> ROBOT_LISTENING: DESCRIBE_CASE"); furhat.listen(timeout = 20000, endSil = 5000, maxSpeech = 60000) }   // attempt 2: clarification question already said
        }
    }

    onResponse {
        val text = it.text

        // State-specific keywords — checked first
        when {
            text.matchesKeyword(goBackKeywords)   -> { furhat.say("No problem."); goto(ChooseMode(skipIntro = true)) }
            text.matchesKeyword(skipKeywords)     -> { furhat.say("No problem. Let me show you the available cases."); goto(ChooseMode(skipIntro = true)) }
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
                val wordCount = text.trim().split(Regex("\\s+")).size
                if (wordCount < 4) {
                    furhat.say(
                        "Could you tell me a bit more? For example: a withdrawn child who won't answer questions, " +
                        "or a teenager with a difficult home situation."
                    )
                    println(">>> ROBOT_LISTENING: DESCRIBE_CASE")
                    furhat.listen(timeout = 20000, endSil = 5000, maxSpeech = 60000)
                } else {
                    // LLM tier — full intent classifier for anything not caught by keywords
                    furhat.say("Hmm…")
                    val label = call {
                        classifyIntent(
                            mainPrompt,
                            text,
                            "- vague (no condition, symptom, or situation described)\n- description\n- browse (wants to see ready-made cases instead)\n- back (wants to go back to the previous menu)\n- exit (wants to stop or leave)"
                        )
                    } as String
                    when (label) {
                        "vague"  -> {
                            furhat.say(
                                "No problem — it can be anything. A type of situation, something you find tricky, " +
                                "a kind of patient. Whatever comes to mind."
                            )
                            println(">>> ROBOT_LISTENING: DESCRIBE_CASE")
                            furhat.listen(timeout = 20000, endSil = 5000, maxSpeech = 60000)
                        }
                        "browse" -> { furhat.say("Sure."); goto(BrowsePersonas) }
                        "back"   -> { furhat.say("No problem."); goto(ChooseMode(skipIntro = true)) }
                        "exit"   -> { furhat.say("Okay, goodbye."); goto(Idle) }
                        else     -> {
                            // "description" or "unclear" — treat as description and attempt generation
                            println("DescribeCase.onResponse: generating from text='$text'")
                            furhat.say("Let me put together a case for you — one moment.")
                            val result = call { generatePersonaFromDescription(text) } as PersonaGenerationResult
                            println("DescribeCase.onResponse: result=$result")
                            handleGenerationResult(result, attempt, description = text)
                        }
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
            goto(ChooseMode(skipIntro = true))
        }
    }
}
