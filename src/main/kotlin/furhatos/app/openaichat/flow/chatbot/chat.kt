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

    onResponse("can we stop", "goodbye", "bye", "bye bye", "stop", "end", "I want to stop", "let's stop", "that's enough") {
        furhat.say("Okay, goodbye")
        furhat.attend(Location(0.0, -1.0, 1.0))
        delay(500)
        activate(hostPersona)
        delay(400)
        furhat.attend(Location(0.0, 0.0, 1.0))
        delay(500)
        furhat.say {
            random {
                +"I hope that was useful practice."
                +"I hope you found that helpful."
                +"Good session. Let me know if you want to try another case."
            }
        }
        goto(AfterChat)
    }

    onResponse {
        furhat.gesture(GazeAversion(2.0))
        val response = call {
            currentPersona.chatbot.getResponse()
        } as String
        furhat.say(response)
        reentry()
    }

    onNoResponse {
        reentry()
    }
}

val AfterChat: State = state(Parent) {

    onEntry {
        furhat.ask("Would you like to talk to someone else?")
    }

    onPartialResponse<Yes> {
        raise(it.secondaryIntent)
    }

    onResponse<Yes> {
        goto(ChoosePersona())
    }

    onResponse<No> {
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
}