package ru.mathalarm

import kotlin.random.Random

const val PROBLEMS_TO_SOLVE = 5

/** Один пример из таблицы умножения (2–9) и 7 готовых вариантов ответа: 1 верный + 6 неверных. */
data class Problem(val a: Int, val b: Int, val options: List<Int>) {
    val answer: Int get() = a * b
}

object MathGen {
    fun problem(seed: Long): Problem {
        val rnd = Random(seed)
        val a = rnd.nextInt(2, 10)
        val b = rnd.nextInt(2, 10)
        val correct = a * b

        val wrong = LinkedHashSet<Int>()

        // Правдоподобные ошибки: «сдвиг» на один множитель или на единицу/десяток.
        val nearMisses = listOf(
            a * (b + 1), a * (b - 1), (a + 1) * b, (a - 1) * b,
            correct + 1, correct - 1, correct + 10, correct - 10
        ).shuffled(rnd)
        for (c in nearMisses) {
            if (wrong.size >= 3) break
            if (c in 2..99 && c != correct) wrong.add(c)
        }

        // Остальные — случайные результаты из таблицы умножения.
        val tableProducts = (2..9).flatMap { x -> (2..9).map { y -> x * y } }
            .distinct()
            .filter { it != correct }
            .shuffled(rnd)
        for (t in tableProducts) {
            if (wrong.size >= 6) break
            wrong.add(t)
        }

        val options = (wrong.toList() + correct).shuffled(rnd)
        return Problem(a, b, options)
    }
}
