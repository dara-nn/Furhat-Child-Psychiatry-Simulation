# Release Notes - 2026-03-13

## Conversation Flow Improvements
- Improved stop-intent handling during chat using flexible keyword matching.
- Added robust post-chat routing for yes/no and persona-name fallback handling.
- Improved greeting and simulation start flow for clearer turn-taking.

## Voice and Persona Updates
- Migrated personas to ElevenLabs voices.
- Renamed and remapped personas:
  - Noah -> Lauri
  - Mei -> Lin
  - Priya -> Asha
- Voice selection by persona:
  - Lina -> Ash - Conversational, Kind and Bright
  - Lauri -> LauriVoiceV1
  - Sara -> Liza - Pleasant, Smooth and Subdued
  - Elias -> Christoffer Satu
  - Lin -> LinVoiceX9
  - Carlos -> Leo Moreno - Intentional and Natural
  - Asha -> Natasha - Professional Indian Voice
- Adjusted persona face and mask setup for more suitable age presentation.

## ElevenLabs Integration
- Added ElevenLabs API key response handling in init and parent/idle states.
- Added runtime logging for selected voice metadata during persona activation.

## Recognizer and Locale
- Set skill language to `en-US` in `skill.properties`.
- Removed forced recognizer-language override handlers to avoid unsupported-locale warnings.

## Build
- Artifact confirmed via `./gradlew shadowJar`.
- Output skill package: `build/libs/OpenAIChat_1.1.0.skill`.
