package furhatos.app.openaichat.flow.chatbot

import furhatos.app.openaichat.setting.Persona
import furhatos.flow.kotlin.DialogHistory
import furhatos.flow.kotlin.Furhat
import furhatos.flow.kotlin.voice.ElevenlabsVoice
import furhatos.util.Gender
import furhatos.util.Language
import java.net.HttpURLConnection
import java.net.URL

/** Gemini API Key **/
val geminiServiceKey = "AIzaSyC5RnAkfM38jf0CYiT_h69ag_zwEWN7i7o"

class GeminiAIChatbot(val systemPrompt: String) {

    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent"

    fun getResponse(): String {
        return try {
            val textParts = mutableListOf<String>()
            
            // Add system prompt
            textParts.add(escapeJson(systemPrompt))
            
            // Add dialog history
            Furhat.dialogHistory.all.takeLast(6).forEach {
                when (it) {
                    is DialogHistory.ResponseItem -> {
                        textParts.add(escapeJson(it.response.text))
                    }
                    is DialogHistory.UtteranceItem -> {
                        textParts.add(escapeJson(it.toText()))
                    }
                }
            }
            
            // Build JSON request body - simplified format matching the curl example
            val partsArray = textParts.joinToString(",\n") { text ->
                """
                {
                  "text": "$text"
                }
                """.trimIndent()
            }
            
            val requestBody = """
            {
              "contents": [
                {
                  "parts": [
                    $partsArray
                  ]
                }
              ],
              "generationConfig": {
                "temperature": 0.7,
                "maxOutputTokens": 1024
              }
            }
            """.trimIndent()

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("x-goog-api-key", geminiServiceKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Send request
            connection.outputStream.bufferedWriter().use {
                it.write(requestBody)
            }

            // Read response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                return parseGeminiResponse(response)
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("Gemini API error: $responseCode - $errorResponse")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "I encountered an error processing your request: ${e.message}"
        }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun parseGeminiResponse(jsonResponse: String): String {
        return try {
            // Navigate to "candidates" first to avoid picking up echoed input text fields
            val candidatesIdx = jsonResponse.indexOf("\"candidates\"")
            if (candidatesIdx == -1) return "I apologize, but I couldn't generate a response."

            val textIdx = jsonResponse.indexOf("\"text\"", candidatesIdx)
            if (textIdx == -1) return "I apologize, but I couldn't generate a response."

            val colonIdx = jsonResponse.indexOf(':', textIdx + 6)
            val openQuoteIdx = jsonResponse.indexOf('"', colonIdx + 1) + 1

            // Parse the JSON string value character by character to handle escapes correctly
            val sb = StringBuilder()
            var pos = openQuoteIdx
            while (pos < jsonResponse.length) {
                val c = jsonResponse[pos]
                if (c == '"') break
                if (c == '\\' && pos + 1 < jsonResponse.length) {
                    when (jsonResponse[pos + 1]) {
                        'n', 'r' -> { sb.append(' '); pos += 2; continue }
                        't' -> { sb.append(' '); pos += 2; continue }
                        '"' -> { sb.append('"'); pos += 2; continue }
                        '\\' -> { sb.append('\\'); pos += 2; continue }
                        else -> { sb.append(jsonResponse[pos + 1]); pos += 2; continue }
                    }
                }
                sb.append(c)
                pos++
            }

            val text = sb.toString().trim()
            if (text.isNotEmpty()) text else "I apologize, but I couldn't generate a response."
        } catch (e: Exception) {
            e.printStackTrace()
            "I apologize, but I couldn't generate a response."
        }
    }
}

// ── Persona generation ────────────────────────────────────────────────────────

sealed class PersonaGenerationResult
data class GeneratedPersona(val persona: Persona) : PersonaGenerationResult()
data class NeedsClarification(val question: String) : PersonaGenerationResult()
object GenerationFailed : PersonaGenerationResult()

private fun escapeForJson(text: String): String = text
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

/** Extract the text content from a Gemini JSON response, preserving real newlines. */
private fun extractGeminiText(jsonResponse: String): String? {
    val candidatesIdx = jsonResponse.indexOf("\"candidates\"")
    if (candidatesIdx == -1) return null
    val textIdx = jsonResponse.indexOf("\"text\"", candidatesIdx)
    if (textIdx == -1) return null
    val colonIdx = jsonResponse.indexOf(':', textIdx + 6)
    val openQuoteIdx = jsonResponse.indexOf('"', colonIdx + 1) + 1
    val sb = StringBuilder()
    var pos = openQuoteIdx
    while (pos < jsonResponse.length) {
        val c = jsonResponse[pos]
        if (c == '"') break
        if (c == '\\' && pos + 1 < jsonResponse.length) {
            when (jsonResponse[pos + 1]) {
                'n'  -> { sb.append('\n'); pos += 2; continue }
                'r'  -> { sb.append('\r'); pos += 2; continue }
                't'  -> { sb.append('\t'); pos += 2; continue }
                '"'  -> { sb.append('"');  pos += 2; continue }
                '\\' -> { sb.append('\\'); pos += 2; continue }
                else -> { sb.append(jsonResponse[pos + 1]); pos += 2; continue }
            }
        }
        sb.append(c)
        pos++
    }
    val result = sb.toString().trim()
    return if (result.isNotEmpty()) result else null
}

private fun buildMetaPrompt(userDescription: String): String = """
You are helping design a simulated child patient for a psychiatry training session.
A student described their training need as: "${escapeForJson(userDescription)}"

Interpret this as a TRAINING NEED, not a literal patient specification. The student may be struggling with a certain type of patient, a demographic, or a clinical challenge. Choose demographics, condition, personality, and difficulty that will give the most educationally useful practice for that need. Fill in any unspecified fields with clinically purposeful choices.

If the training need is too vague to act on (e.g., "I want to talk to children" with no further detail), too irrelevant (e.g., a question unrelated to child psychiatry), or not a training need at all, respond ONLY with this JSON and nothing else:
{"needsClarification": true, "clarificationQuestion": "<one concise follow-up question to ask the student>"}

Otherwise respond ONLY with a JSON object (no markdown, no extra text) with these exact fields:
{
  "name": "<first name>",
  "age": <integer 6–17>,
  "gender": "<male|female|neutral>",
  "condition": "<short condition label>",
  "difficulty": "<easy|medium|hard>",
  "intro": "<opening line the patient says when greeted, 1 sentence, in character>",
  "desc": "<short 3–8 word description, e.g. '13 year old with social anxiety'>",
  "systemPrompt": "<full character brief for the LLM, similar style to the examples below — include personality, communication style, symptoms/backstory, and rules>"
}

Example systemPrompt style:
You are [name], a [age]-year-old [background]. This is a [difficulty] difficulty case.
Personality and communication style:
- [trait 1]
- [trait 2]
Symptoms and backstory:
- [symptom 1]
- [symptom 2]
Rules:
- Keep responses to a maximum of five sentences.
- Never break character or mention that you are an AI.
""".trimIndent()

private fun extractStringField(json: String, fieldName: String): String {
    val key = "\"$fieldName\""
    val keyIdx = json.indexOf(key)
    if (keyIdx == -1) return ""
    val colonIdx = json.indexOf(':', keyIdx + key.length)
    val openQuoteIdx = json.indexOf('"', colonIdx + 1) + 1
    val sb = StringBuilder()
    var pos = openQuoteIdx
    while (pos < json.length) {
        val c = json[pos]
        if (c == '"') break
        if (c == '\\' && pos + 1 < json.length) {
            when (json[pos + 1]) {
                'n'  -> { sb.append('\n'); pos += 2; continue }
                'r'  -> { sb.append('\r'); pos += 2; continue }
                't'  -> { sb.append('\t'); pos += 2; continue }
                '"'  -> { sb.append('"');  pos += 2; continue }
                '\\' -> { sb.append('\\'); pos += 2; continue }
                else -> { sb.append(json[pos + 1]); pos += 2; continue }
            }
        }
        sb.append(c)
        pos++
    }
    return sb.toString().trim()
}

private fun extractIntField(json: String, fieldName: String): Int {
    val key = "\"$fieldName\""
    val keyIdx = json.indexOf(key)
    if (keyIdx == -1) return 0
    val colonIdx = json.indexOf(':', keyIdx + key.length)
    var pos = colonIdx + 1
    while (pos < json.length && (json[pos] == ' ' || json[pos] == '\t')) pos++
    val numSb = StringBuilder()
    while (pos < json.length && json[pos].isDigit()) { numSb.append(json[pos]); pos++ }
    return numSb.toString().toIntOrNull() ?: 0
}

fun parsePersonaJson(json: String): PersonaGenerationResult {
    // Strip markdown fences if present
    val cleaned = json
        .trimIndent()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()

    // Check for clarification response
    if (cleaned.contains("\"needsClarification\"") && cleaned.contains("true")) {
        val question = extractStringField(cleaned, "clarificationQuestion")
        return NeedsClarification(question.ifBlank { "Could you tell me a bit more about what you'd like to practise?" })
    }

    return try {
        val name       = extractStringField(cleaned, "name").ifBlank { return GenerationFailed }
        val age        = extractIntField(cleaned, "age").takeIf { it in 6..17 } ?: 12
        val genderStr  = extractStringField(cleaned, "gender").lowercase()
        val intro      = extractStringField(cleaned, "intro")
        val desc       = extractStringField(cleaned, "desc").ifBlank { "$age year old patient" }
        val systemPr   = extractStringField(cleaned, "systemPrompt").ifBlank { "You are $name, $desc." }

        // Voice + face mapping
        val (voice, face, mask) = when {
            genderStr == "female" ->
                Triple(ElevenlabsVoice("Ash - Conversational, Kind and Bright", Gender.FEMALE, Language.MULTILINGUAL),
                       listOf("Billy"), "child")
            genderStr == "male" && age <= 14 ->
                Triple(ElevenlabsVoice("LauriVoiceV1", Gender.NEUTRAL, Language.MULTILINGUAL),
                       listOf("Devan"), "child")
            genderStr == "male" ->
                Triple(ElevenlabsVoice("Leo Moreno - Intentional and Natural", Gender.MALE, Language.MULTILINGUAL),
                       listOf("Devan"), "child")
            else ->
                Triple(ElevenlabsVoice("Liza - Pleasant, Smooth and Subdued", Gender.FEMALE, Language.MULTILINGUAL),
                       listOf("Billy"), "child")
        }

        val persona = Persona(
            name        = name,
            intro       = intro,
            desc        = desc,
            face        = face,
            mask        = mask,
            voice       = voice,
            systemPrompt = systemPr
        )
        GeneratedPersona(persona)
    } catch (e: Exception) {
        e.printStackTrace()
        GenerationFailed
    }
}

fun generatePersonaFromDescription(userDescription: String): PersonaGenerationResult {
    val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    return try {
        val prompt = buildMetaPrompt(userDescription)
        val requestBody = """
        {
          "contents": [{"parts": [{"text": "${escapeForJson(prompt)}"}]}],
          "generationConfig": {"temperature": 0.9, "maxOutputTokens": 1024}
        }
        """.trimIndent()

        val connection = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("x-goog-api-key", geminiServiceKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        val responseCode = connection.responseCode
        if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
            val raw = connection.inputStream.bufferedReader().readText()
            val text = extractGeminiText(raw) ?: return GenerationFailed
            parsePersonaJson(text)
        } else {
            GenerationFailed
        }
    } catch (e: Exception) {
        e.printStackTrace()
        GenerationFailed
    }
}
