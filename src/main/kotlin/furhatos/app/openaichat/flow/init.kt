package furhatos.app.openaichat.flow

import furhatos.app.openaichat.flow.chatbot.geminiServiceKey
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.hostPersona
import furhatos.event.requests.RequestConfigElevenlabs
import furhatos.event.responses.ResponseConfigElevenlabs
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.state
import furhatos.flow.kotlin.users

val elevenLabsApiKey: String = "sk_9b839c21f8506ffcd4cdb38c2724a36ca5fc70dd127a8ef0"

val Init: State = state() {

    onEvent<RequestConfigElevenlabs>(instant = true) {
        println("Init: sending ElevenLabs API key")
        send(ResponseConfigElevenlabs.Builder().apiKey(elevenLabsApiKey).buildEvent())
    }

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
