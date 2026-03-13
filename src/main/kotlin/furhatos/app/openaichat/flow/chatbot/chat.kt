package furhatos.app.openaichat.flow.chatbot

import furhatos.app.openaichat.flow.*
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.hostPersona
import furhatos.app.openaichat.setting.Persona
import furhatos.app.openaichat.setting.personas
import furhatos.flow.kotlin.*
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import furhatos.records.Location

var afterChatNoResponseCount = 0

val MainChat = state(Parent) {

    onUserLeave { /* suppress Parent's goto(Idle) during chat and goodbye transition */ }

    fun FlowControlRunner.concealedSwitch(targetPersona: Persona) {
        // Conceal the face before switching mask/character to reduce visible pop.
        furhat.attend(Location(0.0, -1.8, 1.0))
        delay(900)
        activate(targetPersona)
        delay(850)
        furhat.attend(Location(0.0, 0.0, 1.0))
        delay(450)
    }

    onEntry {
        concealedSwitch(currentPersona)
        if (currentPersona.intro.isNotEmpty()) {
            furhat.say(currentPersona.intro)
        }
        Furhat.dialogHistory.clear()
        reentry()
    }

    onReentry {
        furhat.listen()
    }

    onResponse {
        val text = it.text.lowercase()
        val shouldStop =
            Regex("\\b(goodbye|bye|stop)\\b").containsMatchIn(text) ||
            text.contains("that's enough") ||
            text.contains("enough for now") ||
            text.contains("let's stop") ||
            text.contains("i want to stop") ||
            text.contains("can we stop")
        if (shouldStop) {
            furhat.say("Okay, goodbye")
            concealedSwitch(hostPersona)
            val hostLine = listOf(
                "I hope that was useful practice.",
                "I hope you found that helpful.",
                "Good session. Let me know if you want to try another case."
            ).random()
            furhat.say(hostLine)
            goto(AfterChat)
        } else {
            furhat.gesture(GazeAversion(2.0))
            val response = call {
                currentPersona.chatbot.getResponse()
            } as String
            furhat.say(response)
            reentry()
        }
    }

    onNoResponse {
        reentry()
    }
}

val AfterChat: State = state(Parent) {

    onEntry {
        afterChatNoResponseCount = 0
        furhat.ask("Would you like to talk to someone else?")
    }

    onResponse<Yes> { goto(ChoosePersona()) }
    onResponse("yes", "yeah", "yep", "sure", "ok", "okay", "of course") { goto(ChoosePersona()) }

    onResponse<No> {
        furhat.say("Okay, goodbye then")
        goto(Idle)
    }
    onResponse("no", "nope", "not now", "that's fine", "goodbye", "bye") {
        furhat.say("Okay, goodbye then")
        goto(Idle)
    }

    for (persona in personas) {
        onResponse(persona.intent) {
            furhat.say("Okay, I will let you talk to ${persona.name}")
            currentPersona = persona
            goto(MainChat)
        }
    }

    onResponse {
        val text = it.text.lowercase()
        val matched = personas.find { persona ->
            text.contains(persona.name.lowercase()) ||
            persona.otherNames.any { alias -> text.contains(alias.lowercase()) }
        }
        when {
            matched != null -> {
                furhat.say("Okay, I will let you talk to ${matched.name}")
                currentPersona = matched
                goto(MainChat)
            }
            text.contains("yes") || text.contains("sure") || text.contains("another") -> goto(ChoosePersona())
            else -> {
                val names = personas.dropLast(1).joinToString(", ") { it.name } + ", or " + personas.last().name
                furhat.ask("If you want another case, please say one name: $names.")
            }
        }
    }

    onNoResponse {
        afterChatNoResponseCount += 1
        if (afterChatNoResponseCount < 3) {
            furhat.ask("I didn't catch that. Would you like to talk to someone else?")
        } else {
            furhat.say("Okay, goodbye then")
            goto(Idle)
        }
    }
}