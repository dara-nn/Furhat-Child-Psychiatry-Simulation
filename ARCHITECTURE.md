# Furhat Child Psychiatry Simulation — Architecture & Design Reference

> Analysis of `/Users/Dara/Documents/build.gradle` (local mirror of the Furhat skill codebase)

---

## 1. Conversation Flow: States, Transitions & Behaviors

The system uses a hierarchical state machine built on Furhat's Kotlin Flow API.

### State Diagram

```
Init (initializes Gemini API, sets host persona)
  ↓
InitFlow
  ├─→ users.hasAny()  → InitialInteraction
  └─→ !users.hasAny() → Idle

Idle (waiting for user)
  ├─→ onUserEnter → InitialInteraction
  └─→ onResponse  → InitialInteraction

InitialInteraction (greeting + "want to try?")
  ├─→ Yes / start keywords     → ChoosePersona()
  ├─→ No / exit keywords       → Idle
  ├─→ Help / confused keywords → reentry (same state)
  └─→ No response ×3           → Idle

ChoosePersona (offer Browse vs Describe)
  ├─→ Browse keywords                    → BrowsePersonas
  ├─→ Describe keywords                  → DescribeCase()
  ├─→ Direct description (LLM-detected)  → DescribeCase(prefilled=description)
  ├─→ Exit                               → Idle
  ├─→ Help                               → reentry
  └─→ No response ×3                     → Idle

BrowsePersonas (list cases in pages of 3)
  ├─→ Name match (keyword or LLM)  → startPersona() → MainChat
  ├─→ More / Next keywords         → increment page → BrowsePersonas
  ├─→ Back keywords                → decrement page or → ChoosePersona
  ├─→ Custom / Describe keywords   → DescribeCase()
  ├─→ Exit                         → Idle
  ├─→ Help                         → reentry
  └─→ No response ×3               → Idle

DescribeCase (user describes training need in natural language)
  ├─→ prefilled != null            → generatePersonaFromDescription() → handle result
  ├─→ Short input (<4 words)       → ask for more → reentry
  ├─→ LLM classifies "vague"       → ask for more detail → reentry
  ├─→ LLM classifies "description" → generatePersonaFromDescription() → handle result
  │     ├─ GeneratedPersona        → startPersona() → MainChat
  │     ├─ NeedsClarification      → ask clarification → DescribeCase(attempt=2)
  │     └─ GenerationFailed        → BrowsePersonas
  ├─→ Go back                      → ChoosePersona(skipIntro=true)
  ├─→ Skip / Browse                → ChoosePersona or BrowsePersonas
  ├─→ Exit                         → Idle
  ├─→ Help                         → reentry
  └─→ No response ×2               → ChoosePersona

MainChat (live interview with persona)
  ├─→ stopSession keyword   → end session → AfterChat
  ├─→ Any other response    → getResponse() from Gemini chatbot → reentry
  └─→ No response           → reentry (silent)

AfterChat (ask if want another case)
  ├─→ Yes / start keywords   → ChoosePersona()
  ├─→ No / exit keywords     → Idle
  ├─→ Persona name mentioned → startPersona() → MainChat
  ├─→ Help                   → reentry
  ├─→ No response ×3         → Idle
  └─→ Other                  → ask if want another case
```

**Key source files:**

| State | File | Lines |
|-------|------|-------|
| Init, InitFlow | `flow/init.kt` | 12–43 |
| Idle | `flow/main/idle.kt` | 10–40 |
| InitialInteraction, ChoosePersona, BrowsePersonas, DescribeCase | `flow/main/greeting.kt` | 64–503 |
| MainChat, AfterChat | `flow/chatbot/chat.kt` | 22–122 |
| Parent (ElevenLabs config, gaze, user tracking) | `flow/parent.kt` | — |

---

## 2. Assistant Flow (Before a Case Starts)

### Phase 1 — Initial Greeting (`InitialInteraction`)

**File:** `flow/main/greeting.kt` lines 72–81

```kotlin
furhat.say("Hi there! I'm a training assistant for child psychiatry.")
delay(600)
furhat.say(
    "I can play the role of a child patient during a simulated clinical interview, " +
    "so you can practise your interview skills."
)
delay(600)
furhat.say("Would you like to give it a try?")
furhat.listen(timeout = 10000)
```

On reentry (if no response): `"Would you like to try a practice interview?"`

Silence fallbacks (random pick after each timeout):
- `"Are you still there? Would you like to give it a try?"`
- `"Hello? I'm here whenever you're ready."`
- `"Just let me know if you'd like to get started."`

### Phase 2 — Case Selection (`ChoosePersona`)

**File:** `flow/main/greeting.kt` lines 142–162

On first entry (skipIntro = false):
1. `"I have a set of pre-made patient cases — each one is a different child with a different background and symptoms."`
2. `"You can browse those, or — I can build a custom case just for you."`
3. `"Just say — browse — to see the ready-made cases, or say — describe — and tell me what kind of patient or situation you'd like to practise."`

On skipIntro (returning from DescribeCase):
- `"Say — browse — or — describe —."`

### Phase 3 — Case Browsing (`BrowsePersonas`)

**File:** `flow/main/greeting.kt` lines 286–305

Cases are listed in pages of 3 using a naming template:
- First item: `"There's [Name] — [desc]."`
- Middle items: `"Then there's [Name] — [desc]."`
- Last item: `"And there's [Name] — [desc]."`

Followed by a navigation cue:
- If first and only page: `"— [names] — say a name to pick one."`
- If first of many: `"— [names] — say a name to pick one, or — more — to hear more."`
- If last of many: `"— [names] — say a name to pick one, or — back — for the previous ones."`
- Middle pages: `"— [names] — say a name, — more — for more, or — back — for the previous ones."`

### Phase 4 — Case Description (`DescribeCase`)

**File:** `flow/main/greeting.kt` lines 408–427

```
"I'll create a patient based on what you describe.
For example: a withdrawn 10-year-old who stopped talking after his parents separated.
You can add as much as you like — age, personality, background, how they behave.
What would you like to practise?"
```

Listens with `timeout=20000, endSil=2500, maxSpeech=60000` (extended for natural speech).

After receiving input, generates a persona via Gemini and handles:
- `GeneratedPersona` → proceed
- `NeedsClarification` → ask the AI-generated clarification question; if attempt 2 fails, fall back to BrowsePersonas
- `GenerationFailed` → `"I'm having some trouble creating a case right now…"` → BrowsePersonas

### Phase 5 — Case Announcement & Persona Switch

**File:** `flow/main/greeting.kt` lines 33–37; `flow/chatbot/chat.kt` lines 13–31

```kotlin
furhat.say("Alright, you're about to meet ${persona.name} — ${persona.desc}. Say — stop session — at any time to end.")
goto(MainChat)
```

Inside `MainChat.onEntry`:
1. `furhat.attend(Location(0.0, -1.8, 1.0))` — robot averts gaze downward
2. 900 ms delay
3. `activate(targetPersona)` — switches face, voice (ElevenLabs), mask
4. 850 ms delay
5. `furhat.attend(Location(0.0, 0.0, 1.0))` — returns gaze to user
6. 450 ms delay
7. If `persona.intro` is non-empty → robot speaks intro in the new character's voice
8. `Furhat.dialogHistory.clear()` → fresh conversation context
9. `reentry()` → start listening

**Host Persona config** (`setting/persona.kt` lines 61–67):
- Face: `Jane`
- Mask: `adult`
- Voice: ElevenLabs `"SarahHost - Approachable and Informative"` (female, multilingual)

---

## 3. Intent Recognition Mechanism

The system uses a **two-tier hybrid NLU**:

### Tier 1: Keyword Matching (Fast, Deterministic)

**File:** `flow/keywords.kt`

All text is normalized before matching:

```kotlin
// lines 5–12
fun String.normalizeForKeyword(): String =
    this.lowercase().trim().replace(Regex("[^a-z0-9 ']"), "")

fun String.matchesKeyword(keywords: List<String>): Boolean {
    val padded = " ${normalizeForKeyword()} "
    return keywords.any { " ${it.normalizeForKeyword()} " in padded }
}
```

This enables substring matching anywhere in the utterance after stripping punctuation and lowercasing. `"yeah sure"` matches `"yes"`, `"can we go back please"` matches `"back"`.

#### Global keywords (active in all states except MainChat)

| Set | Examples | Lines |
|-----|---------|-------|
| `helpKeywords` | `"help"`, `"what are my options"`, `"i'm confused"`, `"instructions"` | 22–28 |
| `exitKeywords` | `"stop"`, `"exit"`, `"bye"`, `"i'm done"`, `"finish"` | 30–38 |

#### State-specific keywords

| Set | Examples | Lines |
|-----|---------|-------|
| `startYesKeywords` | `"yes"`, `"sure"`, `"let's go"`, `"ready"`, `"i want to try"` | 44–50 |
| `startNoKeywords` | `"no"`, `"not now"`, `"maybe later"`, `"i don't want to"` | 52–57 |
| `confusedKeywords` | `"what"`, `"sorry"`, `"repeat that"`, `"i don't understand"` | 59–70 |
| `goBackKeywords` | `"go back"`, `"start over"`, `"menu"`, `"previous page"` | 72–79 |
| `nextPageKeywords` | `"more"`, `"next"`, `"show me more"`, `"any others"` | 81–88 |
| `switchToCustomKeywords` | `"describe"`, `"create"`, `"my own"`, `"make one"` | 90–100 |
| `listCasesKeywords` | `"browse"`, `"show cases"`, `"what do you have"`, `"list"` | 102–109 |
| `stopSessionKeywords` | `"stop session"`, `"end session"`, `"i want to end"` | 120–125 |

**Critical design note:** During `MainChat`, **only** `stopSessionKeywords` are checked. Words like `"stop"` or `"bye"` pass through to the chatbot, so trainees can use them naturally in patient dialogue without accidentally exiting.

#### Furhat built-in NLU (Logistic Multi-Intent Classifier)

**File:** `main.kt` lines 14–16

```kotlin
LogisticMultiIntentClassifier.setAsDefault()
```

The system registers Furhat's `Yes`/`No` intent handlers as a fallback alongside keyword matching:

```kotlin
// greeting.kt lines 89–90
onResponse<Yes> { goto(ChoosePersona()) }
onResponse<No>  { furhat.say("Okay, no worries…"); goto(Idle) }
```

### Tier 2: LLM Classification (Semantic Fallback)

**File:** `flow/chatbot/gemini.kt` lines 387–400

When keyword matching fails, the system calls Gemini Flash 3 with a structured zero-shot classification prompt:

```kotlin
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

**API settings** (`gemini.kt` lines 357–380):
- Model: `gemini-3-flash-preview`
- Temperature: `0.0` (deterministic)
- Max tokens: `64`
- Fallback on error: `"unclear"`

#### Example: `ChoosePersona` LLM routing

**File:** `flow/main/greeting.kt` lines 181–200

Labels passed: `"- browse\n- custom\n- direct_description:[the training need description]"`

If a user says `"I'd like to practice with a reluctant teenager"`, the LLM returns `direct_description:a reluctant teenager`, and the system jumps directly to `DescribeCase(prefilled=...)` skipping the describe-prompt interaction.

#### Example: `BrowsePersonas` case selection

**File:** `flow/main/greeting.kt` lines 235–256

The classification prompt includes the currently-displayed cases as context:

```
The currently displayed cases are:
- Helmi: 12 year old with social anxiety
- Lauri: 14 year old with depression symptoms
- Sara: 10 year old with generalized anxiety

Classify as exactly ONE of:
- select:helmi
- select:lauri
- select:sara
- unclear
```

This allows `"the anxious one"` or `"the younger girl"` to correctly resolve to a name.

### Decision Order (all non-MainChat states)

1. Furhat built-in intents (`Yes`, `No`) — registered with `onResponse<Intent>`
2. State-specific keyword sets — evaluated in `when { }` block
3. Global keywords (`help`, `exit`) — evaluated in parent or same block
4. LLM classification — called only when all above fail
5. Hard fallback — hardcoded reprompt or `reentry()`

---

## 4. Assistant Utterance Design

Utterances fall into six types:

### Type 1: Hardcoded Navigation & System Utterances

Used for all host interactions (not in-character). Fixed strings embedded in state logic.

**Examples:**
- `"Hi there! I'm a training assistant for child psychiatry."` (greeting.kt:73)
- `"Alright, you're about to meet ${persona.name} — ${persona.desc}…"` (greeting.kt:34) — template with dynamic persona name/desc
- `"Okay, ending the session."` (chat.kt:42)
- `"I hope that was useful practice."` / `"Good session."` (chat.kt:44–48, random pick)

### Type 2: Randomized Silence Reprompts

Each state has a `silencePhrases` list; one is selected randomly after each `onNoResponse`.

**File:** `flow/main/greeting.kt` lines 66–70, 166–170; `flow/chatbot/chat.kt` lines 69–73

```kotlin
val silencePhrases = listOf(
    "Are you still there? Would you like to give it a try?",
    "Hello? I'm here whenever you're ready.",
    "Just let me know if you'd like to get started."
)
```

### Type 3: Error & Clarification Utterances

**Short input (<4 words)** (greeting.kt:450–453):
> `"Could you tell me a bit more? For example: a withdrawn child who won't answer questions…"`

**Vague description** (greeting.kt:465–471):
> `"No problem — it can be anything. A type of situation, something you find tricky, a kind of patient."`

**Clarification from LLM** (greeting.kt:43–50): The AI-generated clarification question from `NeedsClarification.question` is spoken directly.

**Generation failure** (greeting.kt:52–58):
> `"I'm having some trouble creating a case right now — you could try again in a moment. For now, let me show you the available cases."`

### Type 4: LLM-Generated Patient Utterances (In-Session)

During `MainChat`, all responses are generated dynamically:

**File:** `flow/chatbot/chat.kt` lines 37–56; `flow/chatbot/gemini.kt` lines 14–86

```kotlin
furhat.gesture(GazeAversion(2.0))
val response = call { currentPersona.chatbot.getResponse() } as String
furhat.say(response)
reentry()
```

`getResponse()` sends the system prompt + last 6 dialog history items to Gemini:
- Temperature: `0.7`
- Max tokens: `1024`
- History window: last 6 exchanges (3 user + 3 AI turns)

A `GazeAversion` gesture plays during the API call so the robot appears to "think".

### Type 5: Hardcoded Persona System Prompts

Each pre-made persona has a ~400-word system prompt defining character, symptoms, and behavioral rules.

**File:** `setting/persona.kt`

| Persona | Age/Gender | Condition | Difficulty | Lines |
|---------|-----------|-----------|-----------|-------|
| Helmi | 12F | Social anxiety | Easy | 79–97 |
| Lauri | 14M | Depression | Medium | 107–125 |
| Sara | 10F | Generalized anxiety | Medium | 135–153 |
| Elias | 16M | Depression + irritability | Hard | 165–184 |
| Lin | 8F | Separation anxiety | Easy | ~194–212 |
| Carlos | 17M | Depression + academic pressure | Hard | ~222–240 |
| Asha | 15F | Perfectionism + anxiety | Medium | ~250–268 |

Prompt structure:
```
You are [name], a [age]-year-old [gender] with [condition]. [backstory]. This is a [difficulty] case.
Personality and communication style:
- [3–4 bullets: speech style, openness, typical responses]
Symptoms and backstory:
- [3–4 bullets: specific symptoms, what brought them in]
Rules:
- Keep responses to a maximum of five sentences.
- Never break character or mention that you are an AI.
- [case-specific rules]
```

### Type 6: Dynamically Generated Persona Prompts

For custom cases described by the trainee, a meta-prompt is sent to Gemini to generate the full persona JSON including a system prompt:

**File:** `flow/chatbot/gemini.kt` lines 181–208

- Temperature: `0.9` (more creative)
- Max tokens: `1024`
- Returns a JSON object with fields: `name`, `age`, `gender`, `condition`, `difficulty`, `intro`, `desc`, `systemPrompt`
- The generated `systemPrompt` follows the same section structure as the hardcoded ones

---

## Summary: Utterance Types at a Glance

| Type | Source | Deterministic | Use Case |
|------|--------|--------------|---------|
| Hardcoded greeting/nav | Kotlin string literals | Yes | Host-mode system interactions |
| Template with persona vars | Kotlin string templates | Semi (dynamic name/desc) | Case announcements |
| Random reprompt list | `listOf(...).random()` | No | Silence handling |
| Hardcoded error/fallback | Kotlin string literals | Yes | Short input, gen failure |
| LLM clarification question | Gemini-generated field | No | Custom case needs more info |
| LLM patient response | Gemini (temp 0.7) | No | In-session roleplay |
| Hardcoded system prompt | persona.kt string | Yes | Pre-made persona character brief |
| LLM-generated system prompt | Gemini (temp 0.9) | No | Custom persona character brief |

---

## 5. Who Handles What the Host Says

The "host" is the robot's neutral facilitator persona — the voice that greets the trainee, presents cases, and wraps up sessions. It is distinct from the patient personas.

### Host utterances are entirely hardcoded Kotlin strings

There is **no LLM involved** in host speech. Every line the host says is a string literal (or simple template) written directly in `flow/main/greeting.kt` and `flow/chatbot/chat.kt`. The decision of *what* to say is made by the state machine — whichever branch of a `when { }` block is reached, that string is passed to `furhat.say(...)`.

```
State machine branch fires
  → hardcoded string (or string template with persona.name / persona.desc)
  → furhat.say(text)
  → ElevenLabs TTS renders it in the host voice
  → Furhat robot plays audio + animates lips
```

### Host voice: ElevenLabs

The host speaks through ElevenLabs just like the patient personas do — it just uses a different voice ID:

```kotlin
// setting/persona.kt lines 61–67
val hostPersona = Persona(
    name  = "Host",
    face  = listOf("Jane"),
    mask  = "adult",
    voice = ElevenlabsVoice("SarahHost - Approachable and Informative", Gender.FEMALE, Language.MULTILINGUAL)
)
```

`activate(hostPersona)` swaps the face, mask, and ElevenLabs voice back to the host after a session ends (the same `concealedSwitch()` mechanism used to switch *into* a patient).

### What the host never does

- It never calls Gemini to decide what to say.
- It never improvises or paraphrases. If a branch isn't coded, the host either reprompts from a fixed list or falls back to the LLM *only for intent classification* — not to generate its own words.
- The one exception: when a custom persona generation returns a `NeedsClarification` result, the AI-generated clarification *question* is spoken by the host verbatim (`furhat.say(result.question)`). This is the only moment host speech is LLM-sourced.

### Contrast: patient utterances

Patient utterances are the opposite — fully LLM-generated. Once `MainChat` is entered, every response is produced by `GeminiAIChatbot.getResponse()` using the persona's system prompt + conversation history. The Kotlin code never dictates what the patient says; it only triggers the API call and passes the result to `furhat.say(...)`.

| Speaker | Text source | Voice source |
|---------|------------|-------------|
| Host | Hardcoded Kotlin strings / string templates | ElevenLabs `SarahHost` |
| Patient (pre-made) | Gemini Flash 3 (temp 0.7) guided by hardcoded system prompt | ElevenLabs persona-specific voice |
| Patient (custom) | Gemini Flash 3 (temp 0.7) guided by LLM-generated system prompt | ElevenLabs persona-specific voice |

---

## File Index

| Path | Purpose |
|------|---------|
| `flow/init.kt` | Skill entry point, Gemini init, host persona activation |
| `flow/main/idle.kt` | Idle state (waiting for user) |
| `flow/main/greeting.kt` | All pre-chat states: InitialInteraction → ChoosePersona → BrowsePersonas → DescribeCase |
| `flow/chatbot/chat.kt` | MainChat (interview) and AfterChat states |
| `flow/parent.kt` | Parent state: ElevenLabs config, gaze aversion gesture, user tracking |
| `flow/keywords.kt` | All keyword definitions and `matchesKeyword` utility |
| `flow/chatbot/gemini.kt` | Gemini API integration: chat, classification, persona generation |
| `setting/persona.kt` | 7 pre-made personas + host persona with system prompts, faces, voices |
| `setting/config.kt` | Config helpers |
| `main.kt` | Skill entry point, `LogisticMultiIntentClassifier` setup |
| `utils/utils.kt` | Gesture definitions, helper functions |
