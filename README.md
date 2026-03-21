# Furhat Child Psychiatry Simulation

A FurhatOS skill for training child psychiatry interview skills. The robot simulates AI-powered child patients with distinct psychological profiles, allowing clinicians to practise clinical interviews in a safe, repeatable environment.

## Pre-Made Cases

| Name | Age / Background | Condition | Difficulty |
|---|---|---|---|
| Helmi | 12F, Finnish | Social anxiety | Easy |
| Lauri | 14M, Finnish | Depression | Medium |
| Emmi | 8F, Finnish | Separation anxiety | Easy |
| Mei | 10F, Chinese | Generalized anxiety | Medium |
| Asha | 15F, Indian | Perfectionism and anxiety | Medium |
| Carlos | 17M, Mexican | Masked depression | Hard |
| Dmitri | 16M, Russian | Irritable depression | Hard |

All personas speak with a condition-appropriate emotional tone and never break character.

## Custom Case Generation

Clinicians can describe any patient they want to practise with. The robot uses Gemini to generate a full persona on the fly — including name, backstory, symptoms, personality, and a system prompt — matched to a face and voice by age, gender, and cultural background.

## How It Works

Each navigation state uses a two-tier intent pipeline:
1. **Keyword matching** — fast, no API call, checked first
2. **LLM classification** — Gemini classifies meaning when no keyword matches

## Requirements

- Furhat robot running FurhatOS
- Gemini API key — get one at [aistudio.google.com](https://aistudio.google.com/app/apikey)
- JDK 15 (set `org.gradle.java.home` in `gradle.properties`)
- Gradle

## Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/dara-nn/Furhat-Child-Psychiatry-Simulation.git
   cd Furhat-Child-Psychiatry-Simulation
   ```

2. **Add your Gemini API key**
   ```bash
   cp local.properties.example local.properties
   # Edit local.properties and paste your key
   ```

3. **Set your local JDK path** (create `gradle.properties` in the project root)
   ```properties
   org.gradle.java.home=/path/to/your/jdk-15
   ```

4. **Build**
   ```bash
   ./gradlew shadowJar
   ```
   This produces `OpenAIChat_1.1.0.skill` in `build/libs/`.

5. **Deploy to Furhat**
   Upload the `.skill` file via the Furhat dashboard and launch it.

6. **Automated Testing**
   This project now includes an "Automated Test Agent". This allows you to test your simulated interviews without manual speech.
   
   - **Run full test suite**:
     ```bash
     python3 tests/build_and_test.py
     ```
   - **Run individual tests**:
     - `python3 tests/test_runner.py` (Happy Path: Browse Helmi and chat)
     - `python3 tests/test_error_paths.py` (Unhappy Path: Tests silence and custom case generation)

## Project Structure

```
src/main/kotlin/furhatos/app/openaichat/
├── flow/
│   ├── chatbot/        # Gemini API integration and custom case generation
│   ├── main/           # Conversation states (greeting, idle, chat)
│   ├── keywords.kt     # Keyword lists for intent matching
│   └── parent.kt       # Shared state behaviour
└── setting/
    └── persona.kt      # Persona definitions and face/voice activation
```

## Acknowledgements

- [FurhatOS](https://furhatrobotics.com/) — robot platform
- [Google Gemini](https://deepmind.google/technologies/gemini/) — language model
- [ElevenLabs](https://elevenlabs.io/) — voices
