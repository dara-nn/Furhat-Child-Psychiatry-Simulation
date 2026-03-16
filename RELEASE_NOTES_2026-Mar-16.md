# Release Notes - 2026-Mar-16

## Custom Case Generation
Clinicians can now describe what they want to practise in natural language — for example, "a child who shuts down after his parents separated" or "a teenager who gets angry quickly from a difficult home". The robot interprets this as a training need and generates a unique patient persona on the fly using Gemini, including a name, age, background, personality, and full character brief. The description prompt guides users with a concrete example and explains they can include age, personality, backstory, or any mix. Short or incomplete inputs are caught before generation — the robot asks for more detail rather than accepting a fragment. The generated patient is immediately available for a live interview session.

The generate prompt now produces structured personas matching the format of the pre-made cases — with Personality, Symptoms, and Rules sections — and responses are capped at four sentences to keep patient replies realistic and brief.

## Case Browser Redesign
The pre-made case selection flow has been fully redesigned. Cases are now presented in groups of three with a short pause between each, and the robot repeats the names after describing them so clinicians don't have to remember. Users can say "more" to hear the next group, "back" for the previous, or "say it again" to replay the current listing. The browser also handles silence gracefully — after a few no-responses, the robot goes quiet and waits for the user to re-engage.

## Intent Recognition
The robot uses a two-tier approach to understand what users say.

**Keyword matching**: The robot now recognises keywords embedded anywhere in a phrase, not just exact matches — so "yeah sure" triggers yes and "can we go back please" triggers back navigation, making the conversation more forgiving of natural speech.

**LLM classification**: When no keyword fits, the robot falls back to Gemini to classify intent from meaning — so saying "I want to talk to a teenage boy who doesn't want to be there" is understood as a custom case description and routed directly to generation, with no specific trigger word needed.

## Session Start Announcement
When a session begins, the robot now introduces the patient by name and description — for example, "You're about to meet Helmi — 12-year-old girl with social anxiety" — so the clinician has context before the interview starts.

## Idle Wake-Up
The robot now listens for speech while idle, not just for someone walking in front of the camera. Saying anything while the robot is quiet will bring it back to the greeting flow.

## Build
- Artifact confirmed via `./gradlew shadowJar`.
- Output skill package: `build/libs/OpenAIChat_1.1.0.skill`.
