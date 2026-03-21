# Changelog

## 2026-Mar-18

### Custom Case Generation — Now Working
The custom case flow introduced in the Mar-16 release was silently failing on every attempt due to the Gemini thinking model exhausting its token budget before producing output. This has been fixed. Clinicians can now describe any patient they want to practise with and the robot will generate a full character on the fly.

### Face and Voice Mapping

#### Pre-Made Cases
| Persona | Face | Voice |
|---|---|---|
| Helmi (12F, Finnish, social anxiety) | White teen girl | White teen girl |
| Lauri (14M, Finnish, depression) | White teen boy | White teen boy |
| Emmi (8F, Finnish, separation anxiety) | Child girl | Emmichildgirl |
| Mei (10F, Chinese, generalized anxiety) | Asian teen girl | Asian teen girl |
| Asha (15F, Indian, perfectionism) | Middle east teen girl | Middle east teen girl |
| Carlos (17M, Mexican, masked depression) | Latin teen boy | Latin teen boy |
| Dmitri (16M, Russian, irritable depression) | Eastern EU teen boy | Eastern EU teen boy |
| Host | Assistant | Assistant |

#### Custom-Generated Cases
Generated personas are now matched to face and voice by age, gender, and cultural background:

| Gender | Age | Background | Face | Mask |
|---|---|---|---|---|
| Female | ≥ 12 | Finnish / European | White teen girl | adult |
| Female | ≥ 12 | Eastern EU | Eastern EU teen girl | adult |
| Female | ≥ 12 | Latin | Latin teen girl | adult |
| Female | ≥ 12 | Middle Eastern / Indian | Middle east teen girl | adult |
| Female | ≥ 12 | Asian | Asian teen girl | adult |
| Female | ≥ 12 | African | Black teen girl | adult |
| Female | < 12 | Any | Child girl | child |
| Male | ≥ 12 | Finnish / European | White teen boy | adult |
| Male | ≥ 12 | Eastern EU | Eastern EU teen boy | adult |
| Male | ≥ 12 | Latin | Latin teen boy | adult |
| Male | ≥ 12 | Middle Eastern / Indian | Middle east teen boy | adult |
| Male | ≥ 12 | Asian | Asian teen boy | adult |
| Male | ≥ 12 | African | Black teen boy | adult |
| Male | < 12 | Any | Child boy | child |

Voice is set to match the face name exactly. Previously, custom cases used hardcoded voices that did not correspond to any real ElevenLabs profile.

### Intent Recognition
Each navigation state now runs a two-tier intent pipeline:

1. **Keyword matching** — fast, no API call. Checked first for common phrases.
2. **LLM classification** — Gemini classifies meaning when no keyword matches.

States and what runs in each:

| State | Keywords checked | LLM fallback labels |
|---|---|---|
| InitialInteraction | yes / no / confused / exit | yes, no, unclear |
| ChooseMode | browse / custom / exit / help | browse, custom, direct_description, exit, unclear |
| BrowsePersonas | persona name / next / back / custom / exit / help | select:\<name\>, next, back, custom, exit, help, unclear |
| DescribeCase | back / skip / browse / exit / help | vague, description, browse, back, exit |
| AfterChat | persona name / yes / no / browse / custom / exit / help | yes, no, browse, custom, exit, unclear |

Exit keyword matching has also been tightened — generic single words like "stop", "end", "done", and "finish" have been removed. These were falsely triggering exit when users described a patient scenario (e.g. "a child who stopped talking").

### Emotional Tone
All seven pre-made personas now speak with a condition-appropriate emotional tone. Custom-generated personas receive the same instruction automatically.

### BrowsePersonas — Asha Replaces Dmitri
Asha (15-year-old Indian girl with perfectionism and anxiety) now appears in the browseable case list. Dmitri remains accessible by name.

---

## 2026-Mar-16

### Custom Case Generation
Clinicians can now describe what they want to practise in natural language — for example, "a child who shuts down after his parents separated" or "a teenager who gets angry quickly from a difficult home". The robot interprets this as a training need and generates a unique patient persona on the fly using Gemini, including a name, age, background, personality, and full character brief. The description prompt guides users with a concrete example and explains they can include age, personality, backstory, or any mix. Short or incomplete inputs are caught before generation — the robot asks for more detail rather than accepting a fragment. The generated patient is immediately available for a live interview session.

The generate prompt now produces structured personas matching the format of the pre-made cases — with Personality, Symptoms, and Rules sections — and responses are capped at four sentences to keep patient replies realistic and brief.

### Case Browser Redesign
The pre-made case selection flow has been fully redesigned. Cases are now presented in groups of three with a short pause between each, and the robot repeats the names after describing them so clinicians don't have to remember. Users can say "more" to hear the next group, "back" for the previous, or "say it again" to replay the current listing. The browser also handles silence gracefully — after a few no-responses, the robot goes quiet and waits for the user to re-engage.

### Intent Recognition
The robot uses a two-tier approach to understand what users say.

**Keyword matching**: The robot now recognises keywords embedded anywhere in a phrase, not just exact matches — so "yeah sure" triggers yes and "can we go back please" triggers back navigation, making the conversation more forgiving of natural speech.

**LLM classification**: When no keyword fits, the robot falls back to Gemini to classify intent from meaning — so saying "I want to talk to a teenage boy who doesn't want to be there" is understood as a custom case description and routed directly to generation, with no specific trigger word needed.

### Session Start Announcement
When a session begins, the robot now introduces the patient by name and description — for example, "You're about to meet Helmi — 12-year-old girl with social anxiety" — so the clinician has context before the interview starts.

### Idle Wake-Up
The robot now listens for speech while idle, not just for someone walking in front of the camera. Saying anything while the robot is quiet will bring it back to the greeting flow.

### Build
- Artifact confirmed via `./gradlew shadowJar`.
- Output skill package: `build/libs/OpenAIChat_1.1.0.skill`.

---

## 2026-Mar-13

### Conversation Flow Improvements
- Improved stop-intent handling during chat using flexible keyword matching.
- Added robust post-chat routing for yes/no and persona-name fallback handling.
- Improved greeting and simulation start flow for clearer turn-taking.
- Updated post-chat fallback to reprompt for a persona name instead of ending the session on incomplete replies.
- Tightened in-chat stop detection to reduce accidental stops on partial-word matches.

### Voice and Persona Updates
- Migrated personas to ElevenLabs voices.
- Renamed and remapped personas:
  - Lina -> Helmi
  - Noah -> Lauri
  - Mei -> Lin
  - Priya -> Asha
- Voice selection by persona:
  - Helmi -> Ash - Conversational, Kind and Bright
  - Lauri -> LauriVoiceV1
  - Sara -> Liza - Pleasant, Smooth and Subdued
  - Elias -> Christoffer Satu
  - Lin -> LinVoiceX9
  - Carlos -> Leo Moreno - Intentional and Natural
  - Asha -> Natasha - Professional Indian Voice
- Adjusted persona face and mask setup for more suitable age presentation.

### ElevenLabs Integration
- Added ElevenLabs API key response handling in init and parent/idle states.
- Added runtime logging for selected voice metadata during persona activation.

### Recognizer and Locale
- Set skill language to `en` in `skill.properties` for broader recognizer compatibility.
- Removed forced recognizer-language override handlers to avoid unsupported-locale warnings.

### Build
- Artifact confirmed via `./gradlew shadowJar`.
- Output skill package: `build/libs/OpenAIChat_1.1.0.skill`.
