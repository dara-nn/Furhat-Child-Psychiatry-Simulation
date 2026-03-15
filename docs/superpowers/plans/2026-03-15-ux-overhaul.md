# UX Overhaul: Hybrid Keyword + LLM Input Handling

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all hard-coded keyword/phrase matching with a two-tier system (exact keyword match → Gemini Flash LLM classification), rewrite all pre-chat conversation copy, and restrict MainChat to `stop_session` keywords only.

**Architecture:** A new `keywords.kt` file holds all keyword lists and helpers. A `callGeminiText` primitive + `classifyIntent` function in `gemini.kt` provide the LLM tier. Each state's `onResponse {}` catch-all processes: state-specific keywords → global help/exit keywords → LLM classifier → reprompt. MainChat inherits no globals and only reacts to `stopSessionKeywords`.

**Tech Stack:** Kotlin, Furhat SDK, Gemini Flash API (existing infrastructure)

---

## Files

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/main/kotlin/furhatos/app/openaichat/flow/keywords.kt` | All keyword lists + `matchesKeyword()` + `pickSilencePhrase()` helpers |
| Modify | `src/main/kotlin/furhatos/app/openaichat/flow/chatbot/gemini.kt` | Add `callGeminiText()` primitive + `classifyIntent()` |
| Modify | `src/main/kotlin/furhatos/app/openaichat/flow/main/greeting.kt` | Complete rewrite of all pre-chat states + shared helpers |
| Modify | `src/main/kotlin/furhatos/app/openaichat/flow/chatbot/chat.kt` | MainChat stop_session only; AfterChat conversational register |

---

## Chunk 1: Foundation

### Task 1: keywords.kt

**Files:**
- Create: `src/main/kotlin/furhatos/app/openaichat/flow/keywords.kt`

- [ ] Create the file with this exact content:

```kotlin
package furhatos.app.openaichat.flow

// ── Normalisation ─────────────────────────────────────────────────────────────

fun String.normalizeForKeyword(): String =
    this.lowercase().trim().replace(Regex("[^a-z0-9 ']"), "")

/** Full-utterance, case-insensitive match after stripping punctuation. */
fun String.matchesKeyword(keywords: List<String>): Boolean {
    val normalized = normalizeForKeyword()
    return keywords.any { it.normalizeForKeyword() == normalized }
}

// ── Silence reprompt helper ───────────────────────────────────────────────────

/** Returns a random phrase from [phrases], avoiding [last] if more than one option exists. */
fun pickSilencePhrase(phrases: List<String>, last: String): String {
    val candidates = if (phrases.size > 1) phrases.filter { it != last } else phrases
    return candidates.random()
}

// ── Global: help ─────────────────────────────────────────────────────────────
// Active in all states EXCEPT MainChat. State-specific keywords checked first.

val helpKeywords = listOf(
    "help", "help me", "what can i do", "what are my options", "what do i do",
    "i need help", "i'm confused", "i don't understand", "i don't get it", "i'm lost",
    "options", "i'm not sure what to do", "what should i do", "what now",
    "how does this work", "what is this", "instructions"
)

// ── Global: exit ──────────────────────────────────────────────────────────────
// Active in all states EXCEPT MainChat. Checked after state-specific keywords.

val exitKeywords = listOf(
    "stop", "exit", "quit", "i'm done", "i want to stop", "i want to leave",
    "end", "goodbye", "bye", "that's all", "i'm finished", "done",
    "i want to go", "i go now", "that is all", "no more", "i leave",
    "finish", "finished", "stop session"
)

// ── InitialInteraction ────────────────────────────────────────────────────────

val startYesKeywords = listOf(
    "yes", "yeah", "yep", "yup", "sure", "okay", "ok", "let's go", "let's do it",
    "absolutely", "of course", "why not", "go ahead", "let's start", "i'd like that",
    "i'd love to", "sounds good", "please", "yes please", "let's try", "i'm ready",
    "yes yes", "ok ok", "sure sure", "right", "alright", "fine", "i want to try",
    "i try", "we can start", "let's", "ready", "i am ready", "i would like to", "i want to"
)

val startNoKeywords = listOf(
    "no", "nah", "nope", "no thanks", "no thank you", "not right now", "not now",
    "maybe later", "i'm good", "not today", "not interested", "i'd rather not",
    "i don't want to", "not yet", "later", "no no", "i don't want", "i do not want",
    "not for me", "i pass", "i'm ok", "i am ok"
)

val confusedKeywords = listOf(
    "what", "huh", "sorry", "pardon", "pardon me", "excuse me", "say that again",
    "say again", "repeat that", "can you repeat that", "can you repeat", "come again",
    "i didn't catch that", "i didn't hear you", "what did you say", "one more time",
    "i don't get it", "i don't understand", "i didn't understand", "i did not understand",
    "what do you mean", "what you mean", "what does that mean", "what is that",
    "what was that", "again", "again please", "please repeat", "repeat please",
    "say it again", "tell again", "once more", "i not understand", "not understand",
    "explain", "explain please", "can you explain", "what you said", "sorry what"
)

// ── Navigation: go_back (BrowsePersonas, DescribeCase) ───────────────────────

val goBackKeywords = listOf(
    "go back", "back", "go back to the menu", "start over", "take me back",
    "return", "previous", "previous menu", "main menu", "menu", "beginning",
    "from the beginning", "to the beginning", "go to menu", "back to menu", "back to start"
)

// ── Navigation: skip (DescribeCase only) ─────────────────────────────────────

val skipKeywords = listOf(
    "never mind", "nevermind", "skip", "forget it", "forget about it",
    "i changed my mind", "actually no", "don't worry", "don't bother",
    "no i don't want", "nothing", "it's fine", "it's ok", "leave it", "not anymore"
)

// ── BrowsePersonas ────────────────────────────────────────────────────────────

val nextPageKeywords = listOf(
    "more", "next", "next page", "keep going", "continue", "what else",
    "show me more", "more cases", "next ones", "any more", "any others",
    "are there more", "what else is there", "and", "other ones", "others",
    "show more", "give me more", "more please", "next please", "is there more",
    "do you have more", "what other"
)

val switchToCustomKeywords = listOf(
    "describe", "create", "custom", "custom case", "create my own", "make my own",
    "i'd rather describe", "let me describe", "i want to describe",
    "i have something in mind", "i want my own", "my own", "own case",
    "make one", "create one", "i want to make", "i want to create",
    "something else", "different one", "i have idea", "i have an idea"
)

// ── DescribeCase ──────────────────────────────────────────────────────────────

val listCasesKeywords = listOf(
    "list cases", "show cases", "show me the cases", "show me the list",
    "available cases", "what cases do you have", "what's available",
    "browse", "browse cases", "the list", "show list", "what you have",
    "what do you have", "cases", "the cases", "show me", "list", "list please"
)

// ── MainChat ──────────────────────────────────────────────────────────────────
// ONLY these keywords are active during MainChat. "stop", "bye", etc. must NOT trigger.

val stopSessionKeywords = listOf(
    "stop session", "stop the session", "end session", "end the session",
    "i want to stop the session", "stop this session", "finish session",
    "finish the session", "i want to end", "i want to stop this",
    "session stop", "session end"
)
```

- [ ] Run `./gradlew build` — keywords.kt must compile without errors. greeting.kt will show `Unresolved reference` errors for old keyword vals (e.g. `describeKeywords`, `listKeywords`) — those are expected and will be fixed in Task 3. Any error originating in `keywords.kt` itself is blocking.
- [ ] Commit: `git add src/main/kotlin/furhatos/app/openaichat/flow/keywords.kt && git commit -m "feat: add keyword lists and helpers"`

---

### Task 2: callGeminiText + classifyIntent in gemini.kt

**Files:**
- Modify: `src/main/kotlin/furhatos/app/openaichat/flow/chatbot/gemini.kt`

Pre-condition: confirm these symbols exist in `gemini.kt` before appending (they do as of the current codebase, but verify before starting):
- `escapeForJson(text: String): String` — private top-level function
- `geminiServiceKey: String` — top-level lazy val
- `extractGeminiText(jsonResponse: String): String?` — private top-level function

- [ ] Confirm the three symbols above are present in `gemini.kt` (grep or read the file)
- [ ] Append to `gemini.kt`:
- [ ] Run `git add src/main/kotlin/furhatos/app/openaichat/flow/chatbot/gemini.kt`

```kotlin
// ── LLM classification helpers ────────────────────────────────────────────────

/**
 * Sends [prompt] to Gemini Flash and returns the trimmed lowercase text response,
 * or "unclear" on any error or empty response.
 * Temperature 0 for deterministic classification.
 */
fun callGeminiText(prompt: String): String {
    val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    return try {
        val requestBody = """
        {
          "contents": [{"parts": [{"text": "${escapeForJson(prompt)}"}]}],
          "generationConfig": {"temperature": 0.0, "maxOutputTokens": 64}
        }
        """.trimIndent()
        val connection = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("x-goog-api-key", geminiServiceKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.bufferedWriter().use { it.write(requestBody) }
        if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
            extractGeminiText(connection.inputStream.bufferedReader().readText())
                ?.trim()?.lowercase() ?: "unclear"
        } else "unclear"
    } catch (e: Exception) {
        e.printStackTrace()
        "unclear"
    }
}

/**
 * Classifies [userSpeech] given [lastPrompt] Furhat just said.
 * [labelsBlock] is a newline-separated list of label lines (each starting with "- ").
 * Returns one of the labels or "unclear".
 */
fun classifyIntent(lastPrompt: String, userSpeech: String, labelsBlock: String): String {
    val prompt = """
You are classifying what a user said to a conversational robot.
The system just asked: "${escapeForJson(lastPrompt)}"
The user responded: "${escapeForJson(userSpeech)}"

Classify as exactly ONE of:
$labelsBlock
- unclear

Respond with ONLY the label.
""".trimIndent()
    return callGeminiText(prompt)
}
```

- [ ] Run `./gradlew build`
- [ ] Commit: `git add src/main/kotlin/furhatos/app/openaichat/flow/chatbot/gemini.kt && git commit -m "feat: add callGeminiText and classifyIntent helpers"`

---

## Chunk 2: InitialInteraction + ChoosePersona

### Task 3: greeting.kt — top-level vars + shared helpers

**Files:**
- Modify: `src/main/kotlin/furhatos/app/openaichat/flow/main/greeting.kt`

Replace the current top-of-file section (vars + `startPersona` helper). Also add new imports.

- [ ] Replace the entire file header (everything before `// ── Initial greeting ──`) with:

```kotlin
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
```

Note: `startPersona` is now `internal` so chat.kt (same module) can call it.

- [ ] Run `./gradlew build` (will have errors for states not yet rewritten — that's expected)

---

### Task 4: greeting.kt — InitialInteraction

Replace the existing `InitialInteraction` state.

- [ ] Replace the `InitialInteraction` state with:

```kotlin
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
```

- [ ] Run `./gradlew build`
- [ ] Commit: `git commit -m "feat: rewrite InitialInteraction with two-tier input handling"`

---

### Task 5: greeting.kt — ChoosePersona

Replace the existing `ChoosePersona` function. No state-specific keyword tier — all speech goes to LLM after global check.

- [ ] Replace the `ChoosePersona` function with:

```kotlin
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
```

- [ ] Run `./gradlew build`
- [ ] Commit: `git commit -m "feat: rewrite ChoosePersona with LLM classification"`

---

## Chunk 3: BrowsePersonas + DescribeCase

### Task 6: greeting.kt — BrowsePersonas

Replaces entire `BrowsePersonas` state. Note `lastPrompt` and `lastSilencePhrase` are local vars inside the `lazy` block (the state is a singleton, so they persist across reentries).

- [ ] Replace the `BrowsePersonas` state with:

```kotlin
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
```

- [ ] Run `./gradlew build`
- [ ] Commit: `git commit -m "feat: rewrite BrowsePersonas with LLM persona selection"`

---

### Task 7: greeting.kt — DescribeCase

Replace the existing `DescribeCase` function. Add `prefilled` parameter for direct-description forwarding from ChoosePersona.

- [ ] Replace the `DescribeCase` function with:

```kotlin
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
            attempt == 1 -> furhat.ask(mainPrompt)
            else         -> furhat.listen()   // attempt 2: clarification question already said
        }
    }

    onResponse {
        val text = it.text

        // State-specific keywords — checked first
        when {
            text.matchesKeyword(goBackKeywords)   -> { furhat.say("No problem."); goto(ChoosePersona()) }
            text.matchesKeyword(skipKeywords)     -> { furhat.say("No problem. Let me show you the available cases."); goto(ChoosePersona()) }
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
            furhat.ask(phrase)
            goto(DescribeCase(attempt = attempt, noResponseCount = noResponseCount + 1))
        } else {
            furhat.say("No worries. Let me show you the available cases instead.")
            goto(ChoosePersona())
        }
    }
}
```

- [ ] Run `./gradlew build`
- [ ] Commit: `git commit -m "feat: rewrite DescribeCase with LLM classification + prefilled path"`

---

## Chunk 4: MainChat + AfterChat + Cleanup

### Task 8: chat.kt — MainChat

Restrict to `stopSessionKeywords` only. Remove old stop-word set. `concealedSwitch` moves to file scope as `internal` (consistent with `startPersona` pattern and avoids any SDK local-extension receiver-scoping ambiguity in the state block).

- [ ] Add `internal fun FlowControlRunner.concealedSwitch(...)` as a file-scope function in `chat.kt`, BEFORE the `MainChat` declaration:

```kotlin
internal fun FlowControlRunner.concealedSwitch(targetPersona: Persona) {
    furhat.attend(Location(0.0, -1.8, 1.0))
    delay(900)
    activate(targetPersona)
    delay(850)
    furhat.attend(Location(0.0, 0.0, 1.0))
    delay(450)
}
```

- [ ] Replace `MainChat` with (no local `concealedSwitch` — uses the file-scope version above):

```kotlin
val MainChat = state(Parent) {

    onUserLeave { /* suppress Parent's goto(Idle) during chat */ }

    onEntry {
        concealedSwitch(currentPersona)
        if (currentPersona.intro.isNotEmpty()) furhat.say(currentPersona.intro)
        Furhat.dialogHistory.clear()
        reentry()
    }

    onReentry {
        furhat.listen()
    }

    onResponse {
        val text = it.text
        // ONLY stop_session keywords active — "stop", "bye", "help" etc. go to the persona
        if (text.matchesKeyword(stopSessionKeywords)) {
            furhat.say("Okay, ending the session.")
            concealedSwitch(hostPersona)
            furhat.say(
                listOf(
                    "I hope that was useful practice.",
                    "I hope you found that helpful.",
                    "Good session. Let me know if you want to try another case."
                ).random()
            )
            goto(AfterChat)
        } else {
            furhat.gesture(GazeAversion(2.0))
            val response = call { currentPersona.chatbot.getResponse() } as String
            furhat.say(response)
            reentry()
        }
    }

    onNoResponse {
        reentry()
    }
}
```

Note: `currentPersona.chatbot.getResponse()` takes no arguments — it reads the latest user utterance from `Furhat.dialogHistory` internally (unchanged from the original implementation). This is intentional; do not add `it.text` as an argument.

- [ ] Run `./gradlew build`
- [ ] Confirm `concealedSwitch` is no longer defined inside the state block (the file-scope version is used instead)
- [ ] Commit: `git add src/main/kotlin/furhatos/app/openaichat/flow/chatbot/chat.kt && git commit -m "feat: restrict MainChat to stop_session keywords only"`

---

### Task 9: chat.kt — AfterChat

Update AfterChat to use `startPersona()`, add exit/help keywords, restore yes/no framing.

- [ ] Replace `AfterChat` with:

```kotlin
val AfterChat: State = state(Parent) {

    var noResponseCount  = 0
    var lastSilencePhrase = ""

    val silencePhrases = listOf(
        "I didn't catch that. Would you like to try another case?",
        "Still there? Happy to set up another case if you'd like.",
        "Just let me know — would you like to talk to someone else?"
    )

    onEntry {
        noResponseCount = 0
        lastSilencePhrase = ""
        furhat.ask("Would you like to talk to someone else?")
    }

    onResponse<Yes> { goto(ChoosePersona()) }
    onResponse<No>  { furhat.say("Okay, goodbye then."); goto(Idle) }

    onResponse {
        val text = it.text
        when {
            text.matchesKeyword(startYesKeywords)  -> goto(ChoosePersona())
            text.matchesKeyword(startNoKeywords)   -> { furhat.say("Okay, goodbye then."); goto(Idle) }
            text.matchesKeyword(exitKeywords)      -> { furhat.say("Okay, goodbye then."); goto(Idle) }
            text.matchesKeyword(helpKeywords)      -> {
                furhat.say("Would you like another case, or are you done for today?")
                reentry()
            }
            else -> {
                // Try persona name match (direct jump)
                val matched = personas.find { p ->
                    text.normalizeForKeyword().contains(p.name.lowercase()) ||
                    p.otherNames.any { alias -> text.normalizeForKeyword().contains(alias.lowercase()) }
                }
                if (matched != null) {
                    startPersona(matched)
                } else {
                    furhat.ask("Would you like to try another case, or go straight to a specific one?")
                }
            }
        }
    }

    onNoResponse {
        noResponseCount++
        if (noResponseCount < 3) {
            val phrase = pickSilencePhrase(silencePhrases, lastSilencePhrase)
            lastSilencePhrase = phrase
            furhat.ask(phrase)
        } else {
            furhat.say("Okay, goodbye then.")
            goto(Idle)
        }
    }
}
```

- [ ] Verify `import furhatos.app.openaichat.flow.*` is present at the top of `chat.kt` (required for `startPersona`, `personas`, `normalizeForKeyword`, `pickSilencePhrase`, and all keyword lists to resolve)
- [ ] Run `./gradlew build`
- [ ] Commit: `git add src/main/kotlin/furhatos/app/openaichat/flow/chatbot/chat.kt && git commit -m "feat: update AfterChat with conversational register + startPersona"`

---

### Task 10: Cleanup

**Explicit removals** (do not skip — a clean build will not catch all of these):

- [ ] In `greeting.kt`: remove `var askedToStartSimulation = false` (if not already gone from Task 3)
- [ ] In `greeting.kt`: remove `val describeKeywords = listOf(...)` and `val listKeywords = listOf(...)` (if not already gone from Task 3)
- [ ] In `chat.kt`: remove `var afterChatNoResponseCount = 0` from file scope (replaced by state-local `var noResponseCount = 0` inside AfterChat's `state {}` block in Task 9)
- [ ] Run `grep -n "askedToStartSimulation\|afterChatNoResponseCount\|val describeKeywords\|val listKeywords" src/main/kotlin/furhatos/app/openaichat/flow/main/greeting.kt src/main/kotlin/furhatos/app/openaichat/flow/chatbot/chat.kt` — expect no output

**Verify keyword handler migration:**
- [ ] Confirm `greeting.kt` BrowsePersonas has no `onResponse("start over", "from the beginning", "go back", "restart")` — replaced by `goBackKeywords` match

- [ ] Run `./gradlew build` — expect clean build with no errors or warnings in the modified files

**Manual test checklist** (run in Furhat emulator or robot):
- [ ] Idle → user enters → InitialInteraction: hears "Hi there!" then ~1s pause then explanation + question
- [ ] Say "what?" → short confused response (not full replay); robot re-asks
- [ ] Say "yes" → ChoosePersona prompt
- [ ] Say something open-ended like "show me what you have" → LLM classifies → BrowsePersonas
- [ ] In BrowsePersonas: say a persona name → pre-transition "Alright, you're about to meet..." → MainChat starts
- [ ] During MainChat: say "stop" → persona responds (NOT system exit)
- [ ] During MainChat: say "help" → persona responds (NOT system help)
- [ ] During MainChat: say "stop session" → session ends gracefully → AfterChat
- [ ] In AfterChat: say a persona name directly (e.g. "Helmi") → startPersona() called → pre-transition + MainChat (not inline goto)
- [ ] In AfterChat: stay silent 3× → system says goodbye → Idle
- [ ] In AfterChat: say "help" → conversational reprompt (no "say X to do Y")
- [ ] In BrowsePersonas: say "go back" → ChoosePersona
- [ ] Say "I want to describe something" in ChoosePersona → DescribeCase
- [ ] Stay silent 3× in DescribeCase → auto-navigate to ChoosePersona
- [ ] Say "I struggle with teenagers who won't talk" in ChoosePersona → LLM returns `direct_description:...` → DescribeCase(prefilled=...) → generation proceeds without re-asking

- [ ] Commit: `git commit -m "chore: remove stale vars and verify build clean"`

---

## Post-implementation note

The ElevenLabs API key in `init.kt` line 12 is hardcoded (`sk_9b839c...`). This was not in scope for this plan but should be moved to `local.properties` in a follow-up, same pattern as the Gemini key.
