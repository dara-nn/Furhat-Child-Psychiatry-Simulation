package furhatos.app.openaichat.flow.chatbot

import furhatos.app.openaichat.flow.*
import furhatos.app.openaichat.flow.chatbot.classifyIntent
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
        println(">>> ROBOT_LISTENING: MAIN_CHAT")
        furhat.listen(endSil = 4000, maxSpeech = 60000)
    }

    onResponse {
        val text = it.text
        // ONLY stop_session keywords active — "stop", "bye", "help" etc. go to the persona
        if (text.isMinimalInput()) {
            furhat.listen(endSil = 4000, maxSpeech = 60000) // patient waits silently — realistic for acknowledgements
        } else if (text.matchesKeyword(stopSessionKeywords)) {
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
            val segments = parseExprSegments(response)
            for (segment in segments) {
                performExpression(segment.gesture)
                furhat.say(segment.text.replace(Regex("""\[EXPR:[^\]]*]"""), "").trim())
            }
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
        println(">>> ROBOT_LISTENING: AFTER_CHAT")
        furhat.ask("Would you like to talk to someone else?")
    }

    onResponse<Yes> { hostAckYes(); goto(ChooseMode(skipIntro = true)) }
    onResponse<No>  { hostAckNo(); furhat.say("Okay, goodbye then."); goto(Idle) }

    onResponse {
        val text = it.text
        // Persona name match first — catches "I want to talk to Lauri" before startYesKeywords
        val matched = personas.find { p ->
            text.normalizeForKeyword().contains(p.name.lowercase()) ||
            p.otherNames.any { alias -> text.normalizeForKeyword().contains(alias.lowercase()) }
        }
        when {
            matched != null                             -> { hostAck(text); startPersona(matched) }
            text.matchesKeyword(startNoKeywords)        -> { hostAck(text); furhat.say("Okay, goodbye then."); goto(Idle) }
            text.matchesKeyword(exitKeywords)           -> { hostAck(text); furhat.say("Okay, goodbye then."); goto(Idle) }
            text.matchesKeyword(switchToCustomKeywords) -> { hostAck(text); goto(DescribeCase()) }
            text.matchesKeyword(listCasesKeywords)      -> { hostAck(text); goto(BrowsePersonas) }
            text.matchesKeyword(helpKeywords)           -> {
                hostAck(text)
                furhat.say("Would you like another case, or are you done for today?")
                reentry()
            }
            text.matchesKeyword(startYesKeywords)       -> { hostAck(text); goto(ChooseMode(skipIntro = true)) }
            // LLM fallback — context-aware classification for natural phrasing
            else -> {
                hostError()
                furhat.say("Hmm…")
                val label = call {
                    classifyIntent(
                        "Would you like to talk to someone else?",
                        text,
                        "- yes\n- no\n- browse\n- custom\n- exit\n- unclear"
                    )
                } as String
                when (label) {
                    "yes"    -> goto(ChooseMode(skipIntro = true))
                    "no"     -> { furhat.say("Okay, goodbye then."); goto(Idle) }
                    "browse" -> goto(BrowsePersonas)
                    "custom" -> goto(DescribeCase())
                    "exit"   -> { furhat.say("Okay, goodbye then."); goto(Idle) }
                    else     -> { hostError(); println(">>> ROBOT_LISTENING: AFTER_CHAT"); furhat.ask("Would you like to try another case, or go straight to a specific one?") }
                }
            }
        }
    }

    onNoResponse {
        noResponseCount++
        if (noResponseCount < 3) {
            val phrase = pickSilencePhrase(silencePhrases, lastSilencePhrase)
            lastSilencePhrase = phrase
            println(">>> ROBOT_LISTENING: AFTER_CHAT")
            furhat.ask(phrase)
        } else {
            furhat.say("Okay, goodbye then.")
            goto(Idle)
        }
    }
}
