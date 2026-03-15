# Release Notes - 2026-Mar-16

## Custom Case Generation
Clinicians can now describe what they want to practise in natural language — for example, "a teenager who is reluctant to open up" or "a young child with separation anxiety". The robot interprets this as a training need and generates a unique patient persona on the fly using Gemini, including a name, background, personality, and full character brief. The generated patient is then immediately available for a live interview session.

⚠️ This feature is in active development and not yet stable. Generation is currently failing — under investigation.

## Case Browser Redesign
The pre-made case selection flow has been fully redesigned. Cases are now presented in groups of three with a short pause between each, and the robot repeats the names after describing them so clinicians don't have to remember. Users can say "more" to hear the next group, "back" for the previous, or "say it again" to replay the current listing. The browser also handles silence gracefully — after a few no-responses, the robot goes quiet and waits for the user to re-engage.

## LLM-Powered Intent Understanding
When keyword matching doesn't cover what the user said, the robot now falls back to Gemini to classify intent. This means natural, unprompted phrases are handled gracefully — for example, saying "I want to talk to a teenage boy who doesn't want to be there" at the case selection screen is understood as a custom case description and routed directly to generation, without the user needing to say a specific trigger word. Similarly, vague or indirect responses are classified and handled appropriately rather than falling through to a generic error.

## Smarter Keyword Recognition
The robot now recognises keywords embedded within longer phrases, not just exact matches. For example, saying "yeah sure" correctly triggers a yes response, and "can we go back please" correctly triggers back navigation. This makes the conversation feel significantly more natural and forgiving of how people actually speak.

## Idle Wake-Up
The robot now listens for speech while idle, not just for someone walking in front of the camera. Saying anything while the robot is quiet will bring it back to the greeting flow.

## Build
- Artifact confirmed via `./gradlew shadowJar`.
- Output skill package: `build/libs/OpenAIChat_1.1.0.skill`.
