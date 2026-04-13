package furhatos.app.openaichat.flow.chatbot

import furhatos.app.openaichat.setting.Persona
import furhatos.flow.kotlin.DialogHistory
import furhatos.flow.kotlin.Furhat
import furhatos.flow.kotlin.voice.ElevenlabsVoice
import furhatos.util.Gender
import furhatos.util.Language
import java.net.HttpURLConnection
import java.net.URL

val geminiServiceKey: String = "AIzaSyBJw5yUCPSpBCDQ-cwP3xkkYfKd2zRbsec"

class GeminiAIChatbot(val systemPrompt: String) {

    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"

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
            connection.connectTimeout = 10000
            connection.readTimeout = 45000
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
You are helping design a fictional child or adolescent patient character for a child psychiatry training simulation.
This is an educational tool used by psychiatry students to practise clinical interview skills.
All content is fictional and used solely for clinical training purposes.
A trainee described their learning goal as: "${escapeForJson(userDescription)}"

IMPORTANT: Respond ONLY with a raw JSON object. No markdown, no code fences, no extra text before or after.
Do NOT use double-quote characters inside any JSON string value — use single quotes or rephrase instead.
JSON object with these exact fields:
{
  "name": "<first name>",
  "age": <integer 6-17>,
  "gender": "<male|female|neutral>",
  "background": "<finnish|european|eastern_eu|latin|middle_eastern|asian|african>",
  "condition": "<short psychiatric diagnostic label, e.g. ADHD, depression, anxiety, ODD, autism>",
  "difficulty": "<easy|medium|hard>",
  "intro": "<MAXIMUM 4 WORDS. The literal words the child says first — e.g. 'Hi.', 'Um... hi.', 'Hello.', 'Mm.' No stage directions, no actions, no symptoms, no personality. Just the greeting itself.>",
  "desc": "<short description, e.g. '14-year-old boy with ADHD'>",
  "systemPrompt": "<character brief — follow the structure and rules below>"
}

Structure for the systemPrompt field (use these exact section headings):
You are [name], a [age]-year-old [gender] with [condition]. [1-sentence backstory]. This is a [difficulty] difficulty case.
Personality and communication style:
- [3-4 bullet points: how they speak, their attitude, openness level, typical responses]
- Include one bullet describing the emotional tone of their voice — appropriate to their condition (e.g. flat and empty for depression, quietly sad for anxiety, tearful for separation anxiety, hollow underneath for masked emotions).
Symptoms and backstory:
- [3-4 bullet points: specific symptoms, what brought them in, what their life looks like]
Rules:
- Keep responses to a maximum of four sentences.
- Never break character or mention that you are an AI.
- NEVER include action descriptions, stage directions, or asterisk text. Only speak words.
- [1-2 case-specific rules, e.g. difficulty opening up, deflecting certain topics]
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
    // Extract JSON object by first { and last } — robust against preamble/postamble text
    val startIdx = json.indexOf('{')
    val endIdx   = json.lastIndexOf('}')
    val cleaned  = if (startIdx != -1 && endIdx > startIdx)
        json.substring(startIdx, endIdx + 1).trim()
    else
        json.trim()

    println("parsePersonaJson: cleaned = $cleaned")

    // Check for clarification response
    if (cleaned.contains("\"needsClarification\"") && cleaned.contains("true")) {
        val question = extractStringField(cleaned, "clarificationQuestion")
        return NeedsClarification(question.ifBlank { "Could you tell me a bit more about what you'd like to practise?" })
    }

    return try {
        val name = extractStringField(cleaned, "name").ifBlank {
            println("parsePersonaJson: name field blank — full cleaned JSON = $cleaned")
            return GenerationFailed
        }
        val age        = extractIntField(cleaned, "age").takeIf { it in 6..17 } ?: 12
        val genderStr  = extractStringField(cleaned, "gender").lowercase()
        val background = extractStringField(cleaned, "background").lowercase().trim()
        val intro      = extractStringField(cleaned, "intro")
        val desc       = extractStringField(cleaned, "desc").ifBlank { "$age year old patient" }
        val systemPr   = extractStringField(cleaned, "systemPrompt").ifBlank { "You are $name, $desc." }

        // ── 1. Determine (faceName, mask) ────────────────────────────────────
        val (faceName, mask) = when {
            genderStr == "female" && age >= 12 -> when (background) {
                "finnish"                  -> "White teen girl"         to "adult"
                "eastern_eu"               -> "Eastern EU teen girl"    to "adult"
                "latin"                    -> "Latin teen girl"         to "adult"
                "middle_eastern", "indian" -> "Middle east teen girl"   to "adult"
                "asian"                    -> "Asian teen girl"         to "adult"
                "african"                  -> "Black teen girl"          to "adult"
                else                       -> "White teen girl"         to "adult"
            }
            genderStr == "female"            -> "Child girl"            to "child"
            genderStr == "male" && age >= 12 -> when (background) {
                "finnish"                  -> "White teen boy"          to "adult"
                "eastern_eu"               -> "Eastern EU teen boy"     to "adult"
                "latin"                    -> "Latin teen boy"          to "adult"
                "middle_eastern", "indian" -> "Middle east teen boy"    to "adult"
                "asian"                    -> "Asian teen boy"          to "adult"
                "african"                  -> "Black teen boy"           to "adult"
                else                       -> "White teen boy"          to "adult"
            }
            genderStr == "male"              -> "Child boy"             to "child"
            age >= 12                        -> "White teen girl"       to "adult"
            else                             -> "Child girl"            to "child"
        }

        // ── 2. Derive voice from face name (ElevenLabs profile = face name) ─
        val voiceGender = when {
            faceName.contains("girl") -> Gender.FEMALE
            faceName.contains("boy")  -> Gender.MALE
            else                      -> Gender.NEUTRAL
        }
        val voice = ElevenlabsVoice(faceName, voiceGender, Language.MULTILINGUAL)
        val face  = listOf(faceName)

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
        println("parsePersonaJson: EXCEPTION ${e.javaClass.name}: ${e.message}")
        GenerationFailed
    }
}

fun generatePersonaFromDescription(userDescription: String): PersonaGenerationResult {
    val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
    println("generatePersona: CALLED with description='$userDescription'")
    return try {
        val prompt = buildMetaPrompt(userDescription)
        val escapedPrompt = escapeForJson(prompt)
        val requestBody = """{"contents":[{"parts":[{"text":"$escapedPrompt"}]}],"generationConfig":{"temperature":0.9,"maxOutputTokens":4096},"safetySettings":[{"category":"HARM_CATEGORY_DANGEROUS_CONTENT","threshold":"BLOCK_NONE"},{"category":"HARM_CATEGORY_HARASSMENT","threshold":"BLOCK_NONE"},{"category":"HARM_CATEGORY_HATE_SPEECH","threshold":"BLOCK_NONE"},{"category":"HARM_CATEGORY_SEXUALLY_EXPLICIT","threshold":"BLOCK_NONE"}]}"""
        println("generatePersona: sending request (body length=${requestBody.length})")

        val connection = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("x-goog-api-key", geminiServiceKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        val responseCode = connection.responseCode
        println("generatePersona: HTTP $responseCode")
        if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
            val raw = connection.inputStream.bufferedReader().readText()
            println("generatePersona: raw = $raw")

            // Detect safety/quota blocks — these have no "text" in candidates
            val finishReasonIdx = raw.indexOf("\"finishReason\"")
            if (finishReasonIdx != -1) {
                val colonIdx = raw.indexOf(':', finishReasonIdx)
                val valStart = raw.indexOf('"', colonIdx + 1) + 1
                val valEnd   = raw.indexOf('"', valStart)
                val finishReason = raw.substring(valStart, valEnd)
                println("generatePersona: finishReason = $finishReason")
                if (finishReason != "STOP") {
                    println("generatePersona: non-STOP finish reason — treating as GenerationFailed")
                    return GenerationFailed
                }
            }

            val text = extractGeminiText(raw) ?: run {
                println("generatePersona: extractGeminiText returned null — raw snippet = ${raw.take(500)}")
                return GenerationFailed
            }
            println("generatePersona: extracted text = $text")
            parsePersonaJson(text)
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "no error body"
            println("generatePersona: HTTP error $responseCode — body = $errorBody")
            GenerationFailed
        }
    } catch (e: Exception) {
        println("generatePersona: EXCEPTION ${e.javaClass.name}: ${e.message}")
        println(e.stackTraceToString())
        GenerationFailed
    }
}

// ── LLM classification helpers ────────────────────────────────────────────────

/**
 * Sends [prompt] to Gemini Flash and returns the trimmed lowercase text response,
 * or "unclear" on any error or empty response.
 * Temperature 0 for deterministic classification.
 */
fun callGeminiText(prompt: String): String {
    val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
    return try {
        val requestBody = """
        {
          "contents": [{"parts": [{"text": "${escapeForJson(prompt)}"}]}],
          "generationConfig": {"temperature": 0.0, "maxOutputTokens": 64}
        }
        """.trimIndent()
        val connection = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("x-goog-api-key", geminiServiceKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        connection.doOutput = true
        connection.outputStream.bufferedWriter().use { it.write(requestBody) }
        if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
            extractGeminiText(connection.inputStream.bufferedReader().readText())
                ?.trim()?.lowercase() ?: "unclear"
        } else "unclear"
    } catch (e: Exception) {
        println("callGeminiText: EXCEPTION ${e.javaClass.name}: ${e.message}")
        "unclear"
    }
}

/**
 * Classifies [userSpeech] given [lastPrompt] Furhat just said.
 * [labelsBlock] is a newline-separated list of label lines (each starting with "- ").
 * Returns one of the labels or "unclear".
 */
fun classifyIntent(lastPrompt: String, userSpeech: String, labelsBlock: String): String {
    val prompt = """
You are classifying what a user said to a conversational robot.
The system just asked: "${escapeForJson(lastPrompt)}"
The user responded: "${escapeForJson(userSpeech)}"

Classify as exactly ONE of:
$labelsBlock
- unclear

Respond with ONLY the label.
""".trimIndent()
    return callGeminiText(prompt)
}
