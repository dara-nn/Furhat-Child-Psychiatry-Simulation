# Release Notes - 2026-Mar-16

## Custom Case Generation
Clinicians can now describe what they want to practise in natural language — for example, "a teenager who is reluctant to open up" or "a young child with separation anxiety". The robot interprets this as a training need and generates a unique patient persona on the fly using Gemini, including a name, background, personality, and full character brief. The generated patient is then immediately available for a live interview session.

⚠️ This feature is in active development and not yet stable. Generation is currently failing — under investigation.

## Case Browser Redesign
The pre-made case selection flow has been fully redesigned. Cases are now presented in groups of three with a short pause between each, and the robot repeats the names after describing them so clinicians don't have to remember. Users can say "more" to hear the next group, "back" for the previous, or "say it again" to replay the current listing. The browser also handles silence gracefully — after a few no-responses, the robot goes quiet and waits for the user to re-engage.

## Natural Language Understanding
The robot uses a two-tier approach to understand what users say. First, it checks for known keywords embedded anywhere in the phrase — so "yeah sure" triggers yes, and "can we go back please" triggers back navigation, even though neither is an exact match. If no keyword fits, it falls back to Gemini to classify intent from meaning — so saying something like "I want to talk to a teenage boy who doesn't want to be there" is understood as a custom case description and routed directly to generation. Together these make the conversation feel significantly more natural and forgiving.

## Idle Wake-Up
The robot now listens for speech while idle, not just for someone walking in front of the camera. Saying anything while the robot is quiet will bring it back to the greeting flow.

## Build
- Artifact confirmed via `./gradlew shadowJar`.
- Output skill package: `build/libs/OpenAIChat_1.1.0.skill`.
