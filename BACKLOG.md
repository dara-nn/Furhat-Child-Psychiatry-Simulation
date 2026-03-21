# Backlog

## Open

### C1 — Duplicate entries in confusedKeywords and helpKeywords
`"i don't get it"` and `"i don't understand"` appear in both lists. Which handler fires depends on check order, which varies by state — same phrase produces three different outcomes across `InitialInteraction`, `DescribeCase`, `BrowsePersonas`.

### C2 — AfterChat: no confusedKeywords handler
`"Say it again"`, `"pardon?"`, `"what?"` fall to LLM → classified `"unclear"` → a different question is asked instead of repeating. Every other state handles `confusedKeywords` explicitly.
**File:** `src/main/kotlin/furhatos/app/openaichat/flow/chatbot/chat.kt`

### C3 — BrowsePersonas: confused → goto(BrowsePersonas) instead of reentry()
Full re-announcement of all cases on any confused phrase. `reentry()` would give the short name-list reprompt instead.
**File:** `src/main/kotlin/furhatos/app/openaichat/flow/main/greeting.kt:365`

### C4 — "what" in confusedKeywords is a broad substring match
Any utterance containing the word "what" matches `confusedKeywords` in `BrowsePersonas` (priority 1) and triggers a full re-read instead of LLM classification. e.g. "what age group", "what kind of case".
**File:** `src/main/kotlin/furhatos/app/openaichat/flow/keywords.kt`

### C5 — InitialInteraction: no silence count limit
All other states cap silence retries (2–3) and exit to Idle. `InitialInteraction` loops indefinitely.
**File:** `src/main/kotlin/furhatos/app/openaichat/flow/main/greeting.kt`

### DescribeCase short-response loop — no exit path
**File:** `src/main/kotlin/furhatos/app/openaichat/flow/main/greeting.kt:479–484`

When a user gives a 1–3 word input at the `DescribeCase` prompt, the `wordCount < 4` guard asks for more detail and re-listens — but nothing counts consecutive short responses. A user who keeps giving short answers is stuck indefinitely; they never reach the `onNoResponse` fallback (which only counts silence) or any other escape.

**Impact:** A confused clinician who can't formulate a description has no way out without knowing the exact stop keywords.

**Fix direction:** Add a `shortResponseCount` parameter to `DescribeCase()`. After N short responses, offer to browse the available cases instead.
