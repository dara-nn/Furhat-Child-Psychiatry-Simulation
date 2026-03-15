package furhatos.app.openaichat.flow

import furhatos.app.openaichat.flow.elevenLabsApiKey
import furhatos.app.openaichat.setting.activate
import furhatos.app.openaichat.setting.hostPersona
import furhatos.event.requests.RequestConfigElevenlabs
import furhatos.event.responses.ResponseConfigElevenlabs
import furhatos.flow.kotlin.*

val Idle : State = state {
    onEntry {
        activate(hostPersona)
        furhat.attendNobody()
        furhat.listen()
    }

    onEvent<RequestConfigElevenlabs>(instant = true) {
        if (elevenLabsApiKey.isBlank()) {
            println("Missing ELEVENLABS_API_KEY. ElevenLabs voices may fall back to a default voice.")
            return@onEvent
        }
        println("Sending ElevenLabs API key (len=${elevenLabsApiKey.length}) from Idle")
        send(ResponseConfigElevenlabs.Builder().apiKey(elevenLabsApiKey).buildEvent())
    }

    onUserEnter {
        furhat.attend(it)
        goto(InitialInteraction)
    }

    onResponse {
        furhat.attend(users.random)
        goto(InitialInteraction)
    }

    onNoResponse {
        furhat.listen()
    }

}

