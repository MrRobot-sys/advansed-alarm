package com.romanpanin.advancedalarm.quiz

import kotlin.math.abs
import kotlin.random.Random

/**
 * Генератор алгебраических задач с нарастающей сложностью:
 *
 *  1. линейные уравнения (a·x + b = c);
 *  2. уравнения с дробями и раскрытием скобок;
 *  3. системы двух уравнений;
 *  4. квадратные уравнения.
 *
 * Все ответы — целые числа или конечные десятичные дроби (1.75, 2.5),
 * чтобы их можно было ввести с обычной цифровой клавиатуры.
 */
object QuizEngine {

    fun generate(count: Int, random: Random = Random.Default): List<QuizProblem> {
        val problems = ArrayList<QuizProblem>(count)
        repeat(count) { index ->
            val position = index.toFloat() / count
            problems += when {
                position < 0.30f -> linear(random)
                position < 0.60f -> fractionOrBrackets(random)
                position < 0.80f -> system(random)
                else -> quadratic(random)
            }
        }
        return problems
    }

    /** a·x + b = c */
    private fun linear(random: Random): QuizProblem {
        val a = random.nextInt(2, 10)
        val x = random.nextInt(-12, 13)
        val b = random.nextInt(-20, 21)
        val c = a * x + b

        val left = if (b >= 0) "$a x + $b" else "$a x − ${-b}"
        return QuizProblem(
            text = "$left = $c",
            parts = listOf(QuizPart("x", x.toDouble())),
            answerText = "x = $x"
        )
    }

    /** (x + k) / m = n   либо   a·x = b с конечной десятичной дробью */
    private fun fractionOrBrackets(random: Random): QuizProblem {
        return if (random.nextBoolean()) fraction(random) else brackets(random)
    }

    /** (x + k) / m = n → x = m·n − k */
    private fun fraction(random: Random): QuizProblem {
        val m = random.nextInt(2, 10)
        val n = random.nextInt(-10, 11)
        val k = random.nextInt(-10, 11)
        val x = m * n - k

        val inside = if (k >= 0) "x + $k" else "x − ${-k}"
        return QuizProblem(
            text = "($inside) / $m = $n",
            parts = listOf(QuizPart("x", x.toDouble())),
            answerText = "x = $x"
        )
    }

    /** a(x − k) + b·x = c */
    private fun brackets(random: Random): QuizProblem {
        val a = random.nextInt(2, 7)
        val b = random.nextInt(2, 7)
        val k = random.nextInt(-8, 9)
        val x = random.nextInt(-9, 10)
        val c = a * (x - k) + b * x

        val inside = if (k >= 0) "x − $k" else "x + ${-k}"
        return QuizProblem(
            text = "$a($inside) + $b x = $c",
            parts = listOf(QuizPart("x", x.toDouble())),
            answerText = "x = $x"
        )
    }

    /** Система двух уравнений */
    private fun system(random: Random): QuizProblem {
        val x = random.nextInt(-9, 10)
        val y = random.nextInt(-9, 10)

        return if (random.nextBoolean()) {
            // x + y = s ; x − y = d
            val s = x + y
            val d = x - y
            QuizProblem(
                text = "x + y = $s\nx − y = $d",
                parts = listOf(QuizPart("x", x.toDouble()), QuizPart("y", y.toDouble())),
                answerText = "x = $x, y = $y"
            )
        } else {
            // 2x + y = p ; x + 3y = q
            val p = 2 * x + y
            val q = x + 3 * y
            QuizProblem(
                text = "2x + y = $p\nx + 3y = $q",
                parts = listOf(QuizPart("x", x.toDouble()), QuizPart("y", y.toDouble())),
                answerText = "x = $x, y = $y"
            )
        }
    }

    /** x² + b·x + c = 0 с целыми корнями */
    private fun quadratic(random: Random): QuizProblem {
        var r1 = random.nextInt(-8, 9)
        var r2 = random.nextInt(-8, 9)
        while (r1 == r2) {
            r2 = random.nextInt(-8, 9)
        }

        val b = -(r1 + r2)
        val c = r1 * r2

        val bx = when {
            b > 0 -> " + ${b}x"
            b < 0 -> " − ${-b}x"
            else -> ""
        }
        val cx = when {
            c > 0 -> " + $c"
            c < 0 -> " − ${-c}"
            else -> ""
        }

        val roots = listOf(r1, r2).sorted()
        return QuizProblem(
            text = "x²$bx$cx = 0",
            parts = listOf(
                QuizPart("x₁", roots[0].toDouble()),
                QuizPart("x₂", roots[1].toDouble())
            ),
            unordered = true,
            answerText = "x = ${roots[0]}, ${roots[1]}"
        )
    }

    /** Печать числа без лишних нулей: 3, 1.75, −2. */
    fun format(value: Double): String {
        val rounded = round(value)
        return if (abs(rounded - Math.round(rounded)) < 1e-9) {
            Math.round(rounded).toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.4f", rounded)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    private fun round(value: Double): Double = Math.round(value * 10000.0) / 10000.0
}
