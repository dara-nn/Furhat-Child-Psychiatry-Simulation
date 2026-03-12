package furhatos.app.openaichat.flow

import furhatos.app.openaichat.flow.chatbot.MainChat
import furhatos.app.openaichat.setting.Persona
import furhatos.app.openaichat.setting.hostPersona
import furhatos.app.openaichat.setting.personas
import furhatos.flow.kotlin.*
import furhatos.nlu.common.*
import furhatos.records.Location

var currentPersona: Persona = hostPersona
var askedToStartSimulation = false

// initial greeting state used when the first user appears
val InitialInteraction: State = state(Parent) {

    onEntry {
        askedToStartSimulation = false
        furhat.say("Hello")
        reentry()
    }

    onReentry {
        furhat.listen()
    }

    fun FlowControlRunner.askToStartSimulation() {
        askedToStartSimulation = true
        furhat.say(
            "I’m a child-psychiatry training assistant. " +
                "I can role-play as different child patients so you can practise clinical interviews. " +
                "Would you like to start?"
        )
        reentry()
    }

    onResponse<Yes> {
        if (askedToStartSimulation) {
            goto(ChoosePersona())
        } else {
            askToStartSimulation()
        }
    }

    onResponse("yes", "yeah", "yep", "sure", "ok", "okay") {
        if (askedToStartSimulation) {
            goto(ChoosePersona())
        } else {
            askToStartSimulation()
        }
    }

    onResponse<No> {
        if (askedToStartSimulation) {
            furhat.say("Okay, let me know if you change your mind.")
            goto(Idle)
        } else {
            askToStartSimulation()
        }
    }

    onResponse("no", "nope", "not now") {
        if (askedToStartSimulation) {
            furhat.say("Okay, let me know if you change your mind.")
            goto(Idle)
        } else {
            askToStartSimulation()
        }
    }

    // any response to the greeting triggers the self-introduction and question
    onResponse {
        if (askedToStartSimulation) {
            goto(ChoosePersona())
        } else {
            askToStartSimulation()
        }
    }

    onNoResponse {
        reentry()
    }
}

fun ChoosePersona() = state(Parent) {

    fun FlowControlRunner.presentPersonas() {
        val listing = personas.joinToString(". ") { it.fullDesc }
        furhat.say("Here are the available cases. $listing.")
        reentry()
    }

    onEntry {
        furhat.attend(users.random)
        presentPersonas()
    }

    onReentry {
        val names = personas.dropLast(1).joinToString(", ") { it.name } + ", or " + personas.last().name
        furhat.ask("Who would you like to talk to? Say the name: $names.")
    }

    onResponse("can you present them again", "could you repeat") {
        presentPersonas()
    }

    fun FlowControlRunner.startPersona(persona: Persona) {
        val intro = listOf(
            "Okay, I will let you talk to ${persona.name}.",
            "Okay, let's have a chat with ${persona.name}.",
            "Sure, we can talk to ${persona.name}."
        ).random()
        val outro = listOf(
            "When you want to end the conversation, just say goodbye, stop, or that's enough.",
            "To end the conversation, just say goodbye or stop."
        ).random()
        furhat.say("$intro $outro")
        currentPersona = persona
        goto(MainChat)
    }

    for (persona in personas) {
        onResponse(persona.intent) { startPersona(persona) }
    }

    onResponse {
        // Substring fallback — handles "I want to talk to Noah", "Lena", etc.
        val text = it.text.lowercase()
        val matched = personas.find { persona ->
            text.contains(persona.name.lowercase()) ||
            persona.otherNames.any { alias -> text.contains(alias.lowercase()) }
        }
        if (matched != null) {
            startPersona(matched)
        } else {
            val names = personas.dropLast(1).joinToString(", ") { it.name } + ", or " + personas.last().name
            furhat.ask("Sorry, I didn't catch that. Please say one of the names: $names.")
        }
    }

    onNoResponse {
        reentry()
    }
}
