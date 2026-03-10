package furhatos.app.openaichat.flow.chatbot

import furhatos.flow.kotlin.DialogHistory
import furhatos.flow.kotlin.Furhat
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

    private fun parseGeminiResponse(jsonResponse: String): String {
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
