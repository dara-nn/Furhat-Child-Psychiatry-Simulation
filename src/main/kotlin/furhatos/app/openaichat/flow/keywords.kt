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
    "from the beginning", "to the beginning", "go to menu", "back to menu", "back to start",
    "previous page", "go back to the previous page", "back to previous page",
    "go to previous", "go to previous page", "last page", "go back a page"
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
    "describe a case", "describe case", "describe my case", "describe my own case",
    "create a case", "create my own case", "make a case", "make my own case",
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
