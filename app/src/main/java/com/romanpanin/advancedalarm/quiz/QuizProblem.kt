package com.romanpanin.advancedalarm.quiz

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Одна величина, которую нужно найти (x, y, x₁, x₂). */
data class QuizPart(
    val label: String,
    val value: Double
)

/** Алгебраическая задача с одним или несколькими ответами. */
data class QuizProblem(
    val text: String,
    val parts: List<QuizPart>,
    /** true — порядок ответов не важен (корни квадратного уравнения). */
    val unordered: Boolean = false,
    /** Текстовое представление правильного ответа (для подсказки). */
    val answerText: String = ""
) {

    fun matches(values: List<Double>): Boolean {
        if (values.size != parts.size) return false
        if (unordered) {
            val actual = values.sorted()
            val expected = parts.map { it.value }.sorted()
            return actual.zip(expected).all { (a, e) -> abs(a - e) < EPSILON }
        }
        return values.zip(parts).all { (value, part) -> abs(value - part.value) < EPSILON }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_TEXT, text)
        put(KEY_UNORDERED, unordered)
        put(KEY_ANSWER, answerText)
        put(KEY_PARTS, JSONArray().apply {
            parts.forEach { part ->
                put(JSONObject().apply {
                    put(KEY_LABEL, part.label)
                    put(KEY_VALUE, part.value)
                })
            }
        })
    }

    companion object {
        private const val EPSILON = 1e-6
        private const val KEY_TEXT = "text"
        private const val KEY_PARTS = "parts"
        private const val KEY_UNORDERED = "unordered"
        private const val KEY_ANSWER = "answer"
        private const val KEY_LABEL = "label"
        private const val KEY_VALUE = "value"

        fun fromJson(json: JSONObject): QuizProblem {
            val partsArray = json.optJSONArray(KEY_PARTS) ?: JSONArray()
            val parts = buildList {
                for (index in 0 until partsArray.length()) {
                    val part = partsArray.getJSONObject(index)
                    add(QuizPart(part.optString(KEY_LABEL), part.optDouble(KEY_VALUE)))
                }
            }
            return QuizProblem(
                text = json.optString(KEY_TEXT),
                parts = parts,
                unordered = json.optBoolean(KEY_UNORDERED, false),
                answerText = json.optString(KEY_ANSWER)
            )
        }
    }
}
