# Furhat Child Psychiatry Simulation

## Description

This FurhatOS skill provides a training tool for child psychiatry by simulating clinical interviews with AI-powered child patients. The skill integrates with Large Language Models (LLMs) such as OpenAI's GPT and Google's Gemini to generate realistic responses based on predefined personas representing different child psychological profiles.

The skill uses the [Simple-OpenAI](https://github.com/sashirestela/simple-openai) library for OpenAI integration and includes support for Gemini API for alternative AI responses.

## Features

- **Persona-Based Simulation**: Role-play as various child patients with distinct personalities, backgrounds, and psychological conditions.
- **AI-Powered Responses**: Leverages advanced LLMs to provide contextually appropriate and dynamic responses during interviews.
- **Interactive Training**: Practice clinical interview techniques in a safe, simulated environment.
- **Multi-Model Support**: Supports both OpenAI and Gemini models for flexibility.

## Requirements

- **Furhat Robot**: A Furhat robot to run the skill.
- **API Keys**:
  - OpenAI API key (obtain from [OpenAI](https://openai.com/api/)).
  - Gemini API key (configured in the code).
- **Software**:
  - Java/Kotlin development environment.
  - Gradle for building the project.

## Installation

1. **Clone the Repository**:
   ```bash
   git clone <repository-url>
   cd furhat-child-psychiatry-simulation
   ```

2. **Configure API Keys**:
   - Set your OpenAI API key in `openai.kt` (or the relevant configuration file).
   - The Gemini API key is hardcoded in `gemini.kt` for demonstration; update it with your own key in a production environment.

3. **Build the Project**:
   ```bash
   ./gradlew build
   ```

4. **Deploy to Furhat**:
   - Transfer the built skill file (e.g., `OpenAIChat_1.1.0.skill`) to your Furhat robot.
   - Install and run the skill on the Furhat platform.

## Usage

1. **Start the Skill**: Launch the skill on your Furhat robot.
2. **Initial Interaction**: Furhat greets the user and offers to start the simulation.
3. **Choose Persona**: Select from available child patient personas to begin the role-play.
4. **Conduct Interview**: Engage in a clinical interview. Furhat will respond as the selected persona using AI-generated dialogue.
5. **End Session**: Conclude the simulation as needed.

## Configuration

- **Personas**: Customize or add new child patient personas in the `setting/` directory.
- **AI Models**: Switch between OpenAI and Gemini by modifying the chatbot logic in `flow/chatbot/`.

## Contributing

Contributions are welcome! Please submit issues or pull requests for improvements, bug fixes, or new features.

## License

This project is licensed under the [MIT License](LICENSE). Ensure compliance with API terms from OpenAI and Google.

## Acknowledgments

- [FurhatOS](https://furhatrobotics.com/) for the robot platform.
- [Simple-OpenAI](https://github.com/sashirestela/simple-openai) for OpenAI integration.
- OpenAI and Google for their LLM APIs.
