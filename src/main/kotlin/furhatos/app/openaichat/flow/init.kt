package furhatos.app.openaichat.flow

import furhatos.app.openaichat.flow.chatbot.geminiServiceKey
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.hostPersona
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.state
import furhatos.flow.kotlin.users

val Init: State = state() {
    init {
        /** Check API key for the Gemini language model has been set */
        if (geminiServiceKey.isEmpty()) {
            println("Missing API key for Gemini language model. ")
            exit()
        }

        /** Set the Persona */
        activate(hostPersona)

        /** start the interaction */
        goto(InitFlow)
    }

}

val InitFlow: State = state() {
    onEntry {
        when {
            users.hasAny() -> goto(InitialInteraction)
            !users.hasAny() -> goto(Idle)
        }
    }

}

