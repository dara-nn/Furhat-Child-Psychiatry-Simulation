package furhatos.app.openaichat.flow

import furhatos.app.openaichat.flow.chatbot.geminiServiceKey
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.envOrProperty
import furhatos.app.openaichat.setting.hostPersona
import furhatos.event.requests.RequestConfigElevenlabs
import furhatos.event.responses.ResponseConfigElevenlabs
import furhatos.flow.kotlin.*

val elevenLabsApiKey: String = envOrProperty("elevenlabs.api.key") ?: ""

val Init: State = state() {

    onEvent<RequestConfigElevenlabs>(instant = true) {
        println("Init: sending ElevenLabs API key")
        send(ResponseConfigElevenlabs.Builder().apiKey(elevenLabsApiKey).buildEvent())
    }

    onEntry {
        /** Check API key for the Gemini language model has been set */
        if (geminiServiceKey.isEmpty()) {
            println("Missing API key for Gemini language model.")
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
