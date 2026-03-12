package furhatos.app.openaichat.flow.chatbot

import furhatos.app.openaichat.flow.*
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.hostPersona
import furhatos.app.openaichat.setting.personas
import furhatos.flow.kotlin.*
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import furhatos.records.Location

val MainChat = state(Parent) {

    onUserLeave { /* suppress Parent's goto(Idle) during chat and goodbye transition */ }

    onEntry {
        // Head down to mask the face change
        furhat.attend(Location(0.0, -1.0, 1.0))
        delay(500)
        activate(currentPersona)
        delay(400)
        // Head back up with the new face
        furhat.attend(Location(0.0, 0.0, 1.0))
        delay(500)
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
        val stopWords = listOf("goodbye", "bye", "stop", "end", "that's enough", "enough", "let's stop", "i want to stop", "can we stop")
        if (stopWords.any { word -> text.contains(word) }) {
            furhat.say("Okay, goodbye")
            furhat.attend(Location(0.0, -1.0, 1.0))
            delay(500)
            activate(hostPersona)
            delay(400)
            furhat.attend(Location(0.0, 0.0, 1.0))
            delay(500)
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
                furhat.say("Okay, goodbye then")
                goto(Idle)
            }
        }
    }

    onNoResponse {
        furhat.say("Okay, goodbye then")
        goto(Idle)
    }
}