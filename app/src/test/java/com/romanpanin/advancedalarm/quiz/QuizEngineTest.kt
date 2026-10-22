package com.romanpanin.advancedalarm.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Проверяет, что сгенерированные задачи согласованы: ответ действительно
 * удовлетворяет выведенному уравнению, а сложность растёт по типам.
 */
class QuizEngineTest {

    @Test
    fun `answers satisfy the generated equations`() {
        repeat(300) { seed ->
            val problems = QuizEngine.generate(10, Random(seed))
            problems.forEach { problem ->
                assertTrue(
                    "Задача не распознана: ${problem.text}",
                    verify(problem)
                )
            }
        }
    }

    @Test
    fun `difficulty grows across the list`() {
        val problems = QuizEngine.generate(10, Random(7))
        assertEquals(10, problems.size)

        // Первые задачи — одно неизвестное, последние — два.
        assertEquals(1, problems[0].parts.size)
        assertEquals(1, problems[2].parts.size)
        assertEquals(1, problems[5].parts.size)
        assertEquals(2, problems[6].parts.size)
        assertEquals(2, problems[9].parts.size)

        // Системы уравнений: части подписаны x и y.
        assertEquals(listOf("x", "y"), problems[6].parts.map { it.label })

        // Квадратные уравнения: два корня, порядок не важен.
        assertTrue(problems[8].unordered)
        assertEquals(listOf("x₁", "x₂"), problems[8].parts.map { it.label })
    }

    @Test
    fun `answers are finite decimals or integers`() {
        val problems = QuizEngine.generate(10, Random(11))
        problems.forEach { problem ->
            problem.parts.forEach { part ->
                assertTrue(
                    "Нечисловой ответ: ${part.value}",
                    part.value.isFinite()
                )
                assertTrue(
                    "Ответ не вводится с клавиатуры: ${part.value}",
                    abs(part.value - Math.round(part.value)) < 1e-9 ||
                        abs(part.value * 100 - Math.round(part.value * 100)) < 1e-9
                )
            }
        }
    }

    @Test
    fun `matching accepts unordered roots only when values are equal`() {
        val problem = QuizProblem(
            text = "x² − 5x + 6 = 0",
            parts = listOf(QuizPart("x₁", 2.0), QuizPart("x₂", 3.0)),
            unordered = true
        )
        assertTrue(problem.matches(listOf(2.0, 3.0)))
        assertTrue(problem.matches(listOf(3.0, 2.0)))
        assertTrue(!problem.matches(listOf(2.0, 4.0)))
    }

    @Test
    fun `matching for labelled systems is order sensitive`() {
        val problem = QuizProblem(
            text = "x + y = 5\nx − y = 1",
            parts = listOf(QuizPart("x", 3.0), QuizPart("y", 2.0))
        )
        assertTrue(problem.matches(listOf(3.0, 2.0)))
        assertTrue(!problem.matches(listOf(2.0, 3.0)))
    }

    /**
     * Разбирает текст задачи и проверяет, что указанный ответ ей удовлетворяет.
     * Поддерживаются все четыре типа, которые выдаёт генератор.
     */
    private fun verify(problem: QuizProblem): Boolean {
        val text = problem.text.replace("−", "-").replace("²", "^2")

        // Квадратное уравнение: у него сразу два корня, меток "x" нет.
        val quadratic = Regex("^x\\^2 ?([+-] \\d+x)? ?([+-] \\d+)? = 0$").find(text)
        if (quadratic != null) {
            val b = quadratic.groupValues[1]
                .replace(" ", "")
                .removeSuffix("x")
                .toDoubleOrNull() ?: 0.0
            val c = quadratic.groupValues[2]
                .replace(" ", "")
                .toDoubleOrNull() ?: 0.0
            return problem.parts.all { part ->
                val root = part.value
                abs(root * root + b * root + c) < 1e-9
            }
        }

        val x = problem.parts.firstOrNull { it.label == "x" }?.value ?: return false

        val linear = Regex("^(\\d+) x ([+-]) (\\d+) = (-?\\d+)$").find(text)
        if (linear != null) {
            val a = linear.groupValues[1].toDouble()
            val b = linear.groupValues[3].toDouble() * sign(linear.groupValues[2])
            val c = linear.groupValues[4].toDouble()
            return abs(a * x + b - c) < 1e-9
        }

        val fraction = Regex("^\\(x ([+-]) (\\d+)\\) / (\\d+) = (-?\\d+)$").find(text)
        if (fraction != null) {
            val k = fraction.groupValues[2].toDouble() * sign(fraction.groupValues[1])
            val m = fraction.groupValues[3].toDouble()
            val n = fraction.groupValues[4].toDouble()
            return abs((x + k) / m - n) < 1e-9
        }

        val brackets = Regex("^(\\d+)\\(x ([+-]) (\\d+)\\) \\+ (\\d+) x = (-?\\d+)$").find(text)
        if (brackets != null) {
            val a = brackets.groupValues[1].toDouble()
            // В тексте уравнение записано как a(x + сдвиг) + b·x = c.
            val offset = brackets.groupValues[3].toDouble() * sign(brackets.groupValues[2])
            val b = brackets.groupValues[4].toDouble()
            val c = brackets.groupValues[5].toDouble()
            return abs(a * (x + offset) + b * x - c) < 1e-9
        }

        val system = Regex("^x \\+ y = (-?\\d+)\\nx - y = (-?\\d+)$").find(text)
        if (system != null) {
            val y = problem.parts.firstOrNull { it.label == "y" }?.value ?: return false
            val s = system.groupValues[1].toDouble()
            val d = system.groupValues[2].toDouble()
            return abs(x + y - s) < 1e-9 && abs(x - y - d) < 1e-9
        }

        val system2 = Regex("^2x \\+ y = (-?\\d+)\\nx \\+ 3y = (-?\\d+)$").find(text)
        if (system2 != null) {
            val y = problem.parts.firstOrNull { it.label == "y" }?.value ?: return false
            val p = system2.groupValues[1].toDouble()
            val q = system2.groupValues[2].toDouble()
            return abs(2 * x + y - p) < 1e-9 && abs(x + 3 * y - q) < 1e-9
        }

        return false
    }

    private fun sign(operator: String): Double = if (operator == "-") -1.0 else 1.0
}
