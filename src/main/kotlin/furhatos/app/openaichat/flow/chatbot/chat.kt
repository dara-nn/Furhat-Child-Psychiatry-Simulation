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

internal fun FlowControlRunner.concealedSwitch(targetPersona: Persona) {
    furhat.attend(Location(0.0, -1.8, 1.0))
    delay(900)
    activate(targetPersona)
    delay(850)
    furhat.attend(Location(0.0, 0.0, 1.0))
    delay(450)
}

val MainChat = state(Parent) {

    onUserLeave { /* suppress Parent's goto(Idle) during chat */ }

    onEntry {
        concealedSwitch(currentPersona)
        if (currentPersona.intro.isNotEmpty()) furhat.say(currentPersona.intro)
        Furhat.dialogHistory.clear()
        reentry()
    }

    onReentry {
        furhat.listen()
    }

    onResponse {
        val text = it.text
        // ONLY stop_session keywords active — "stop", "bye", "help" etc. go to the persona
        if (text.matchesKeyword(stopSessionKeywords)) {
            furhat.say("Okay, ending the session.")
            concealedSwitch(hostPersona)
            furhat.say(
                listOf(
                    "I hope that was useful practice.",
                    "I hope you found that helpful.",
                    "Good session. Let me know if you want to try another case."
                ).random()
            )
            goto(AfterChat)
        } else {
            furhat.gesture(GazeAversion(2.0))
            val response = call { currentPersona.chatbot.getResponse() } as String
            furhat.say(response)
            reentry()
        }
    }

    onNoResponse {
        reentry()
    }
}

val AfterChat: State = state(Parent) {

    var noResponseCount  = 0
    var lastSilencePhrase = ""

    val silencePhrases = listOf(
        "I didn't catch that. Would you like to try another case?",
        "Still there? Happy to set up another case if you'd like.",
        "Just let me know — would you like to talk to someone else?"
    )

    onEntry {
        noResponseCount = 0
        lastSilencePhrase = ""
        furhat.ask("Would you like to talk to someone else?")
    }

    onResponse<Yes> { goto(ChoosePersona()) }
    onResponse<No>  { furhat.say("Okay, goodbye then."); goto(Idle) }

    onResponse {
        val text = it.text
        when {
            text.matchesKeyword(startYesKeywords)  -> goto(ChoosePersona())
            text.matchesKeyword(startNoKeywords)   -> { furhat.say("Okay, goodbye then."); goto(Idle) }
            text.matchesKeyword(exitKeywords)      -> { furhat.say("Okay, goodbye then."); goto(Idle) }
            text.matchesKeyword(helpKeywords)      -> {
                furhat.say("Would you like another case, or are you done for today?")
                reentry()
            }
            else -> {
                // Try persona name match (direct jump)
                val matched = personas.find { p ->
                    text.normalizeForKeyword().contains(p.name.lowercase()) ||
                    p.otherNames.any { alias -> text.normalizeForKeyword().contains(alias.lowercase()) }
                }
                if (matched != null) {
                    startPersona(matched)
                } else {
                    furhat.ask("Would you like to try another case, or go straight to a specific one?")
                }
            }
        }
    }

    onNoResponse {
        noResponseCount++
        if (noResponseCount < 3) {
            val phrase = pickSilencePhrase(silencePhrases, lastSilencePhrase)
            lastSilencePhrase = phrase
            furhat.ask(phrase)
        } else {
            furhat.say("Okay, goodbye then.")
            goto(Idle)
        }
    }
}
