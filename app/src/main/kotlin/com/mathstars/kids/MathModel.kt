package com.mathstars.kids

import kotlin.math.max
import kotlin.random.Random

/** The kinds of game a child can pick. MIXED draws from the other three. */
enum class Operation(val symbol: String, val title: String, val color: Int) {
    ADD("+", "Addition", 0xFF37B24D.toInt()),
    SUBTRACT("−", "Subtraction", 0xFF1C7ED6.toInt()),
    MULTIPLY("×", "Multiplication", 0xFFF76707.toInt()),
    MIXED("?", "Mix it up!", 0xFF7048E8.toInt());
}

/** School levels. Each sets number ranges (see QuestionGenerator) and the time per question. */
enum class Grade(
    val number: Int,
    val hint: String,
    val secondsPerQuestion: Int,
    val allowsNegative: Boolean,
    val color: Int,
) {
    G1(1, "Numbers up to 20", 20, false, 0xFFF03E3E.toInt()),
    G2(2, "Numbers up to 100", 20, false, 0xFFF76707.toInt()),
    G3(3, "Up to 1,000 · tables", 25, false, 0xFFE8A200.toInt()),
    G4(4, "Up to 10,000", 35, false, 0xFF37B24D.toInt()),
    G5(5, "Big numbers", 45, false, 0xFF1C7ED6.toInt()),
    G6(6, "Negative numbers", 40, true, 0xFF7048E8.toInt());

    val title: String get() = "Grade $number"
}

data class Question(val a: Int, val b: Int, val op: Operation, val answer: Int) {
    /** e.g. "7 + 5", "−3 × (−4)" */
    val text: String get() = "${format(a, wrap = false)} ${op.symbol} ${format(b, wrap = true)}"

    companion object {
        fun format(n: Int, wrap: Boolean = false): String = when {
            n >= 0 -> n.toString()
            wrap -> "(−${-n})"
            else -> "−${-n}"
        }
    }
}

class QuestionGenerator(
    private val grade: Grade,
    private val op: Operation,
    private val random: Random = Random.Default,
) {
    private var last: Question? = null

    /** A new question, never the same as the one before. */
    fun next(): Question {
        var q = make()
        var tries = 0
        while (q == last && tries++ < 20) q = make()
        last = q
        return q
    }

    private fun make(): Question {
        val o = if (op == Operation.MIXED) {
            listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY).random(random)
        } else op
        return when (o) {
            Operation.ADD -> add()
            Operation.SUBTRACT -> subtract()
            else -> multiply()
        }
    }

    private fun r(lo: Int, hi: Int) = random.nextInt(lo, hi + 1)

    /** Grade 6: a zero makes a question too easy ("0 + (−48)"). */
    private fun nonZero(lo: Int, hi: Int): Int {
        var n = r(lo, hi)
        while (n == 0) n = r(lo, hi)
        return n
    }

    private fun maybeSwap(a: Int, b: Int, op: Operation): Question =
        if (random.nextBoolean()) q(a, b, op) else q(b, a, op)

    private fun q(a: Int, b: Int, op: Operation): Question {
        val answer = when (op) {
            Operation.ADD -> a + b
            Operation.SUBTRACT -> a - b
            else -> a * b
        }
        return Question(a, b, op, answer)
    }

    private fun add(): Question = when (grade) {
        Grade.G1 -> q(r(0, 10), r(0, 10), Operation.ADD)
        Grade.G2 -> r(10, 89).let { a -> maybeSwap(a, r(1, 100 - a), Operation.ADD) }
        Grade.G3 -> r(100, 899).let { a -> maybeSwap(a, r(10, 999 - a), Operation.ADD) }
        Grade.G4 -> r(1000, 8999).let { a -> maybeSwap(a, r(100, 9999 - a), Operation.ADD) }
        Grade.G5 -> r(10000, 59999).let { a -> maybeSwap(a, r(1000, 99999 - a), Operation.ADD) }
        Grade.G6 -> {
            var a = nonZero(-50, 50)
            var b = nonZero(-50, 50)
            if (a > 0 && b > 0) {
                if (random.nextBoolean()) a = -a else b = -b
            }
            q(a, b, Operation.ADD)
        }
    }

    private fun subtract(): Question = when (grade) {
        Grade.G1 -> r(2, 20).let { a -> q(a, r(0, minOf(a, 10)), Operation.SUBTRACT) }
        Grade.G2 -> r(10, 99).let { a -> q(a, r(1, a), Operation.SUBTRACT) }
        Grade.G3 -> r(100, 999).let { a -> q(a, r(10, a), Operation.SUBTRACT) }
        Grade.G4 -> r(1000, 9999).let { a -> q(a, r(100, a), Operation.SUBTRACT) }
        Grade.G5 -> r(10000, 99999).let { a -> q(a, r(1000, a), Operation.SUBTRACT) }
        Grade.G6 -> {
            var a = nonZero(-30, 60)
            var b = nonZero(-30, 90)
            // Make sure a negative number shows up somewhere
            if (a >= 0 && b >= 0 && a >= b) a = b.also { b = a }
            if (a == b) b += r(1, 20)
            q(a, b, Operation.SUBTRACT)
        }
    }

    private fun multiply(): Question = when (grade) {
        Grade.G1 -> maybeSwap(r(1, 5), r(1, 2), Operation.MULTIPLY)
        Grade.G2 -> maybeSwap(listOf(2, 3, 4, 5, 10).random(random), r(1, 10), Operation.MULTIPLY)
        Grade.G3 -> maybeSwap(r(2, 10), r(1, 10), Operation.MULTIPLY)
        Grade.G4 -> maybeSwap(r(11, 99), r(2, 9), Operation.MULTIPLY)
        Grade.G5 -> if (random.nextBoolean()) {
            q(r(100, 999), r(2, 9), Operation.MULTIPLY)
        } else {
            q(r(11, 49), r(11, 19), Operation.MULTIPLY)
        }
        Grade.G6 -> maybeSwap(-r(1, 12), r(-12, 12).let { if (it == 0) 1 else it }, Operation.MULTIPLY)
    }
}

/** Four answer buttons: the right one and three believable mistakes. */
fun choicesFor(q: Question, allowNegative: Boolean, random: Random = Random.Default): List<Int> {
    val ans = q.answer
    val candidates = mutableListOf(ans + 1, ans - 1, ans + 2, ans - 2, ans + 10, ans - 10)
    when (q.op) {
        Operation.MULTIPLY -> candidates += listOf(q.a * (q.b + 1), q.a * (q.b - 1), (q.a + 1) * q.b, (q.a - 1) * q.b, q.a + q.b)
        Operation.SUBTRACT -> candidates += q.a + q.b
        Operation.ADD -> candidates += listOf(q.a - q.b, ans + 100, ans - 100)
        Operation.MIXED -> Unit
    }
    if (allowNegative) candidates += -ans
    candidates.shuffle(random)

    val set = linkedSetOf(ans)
    for (c in candidates) {
        if (set.size == 4) break
        if (allowNegative || c >= 0) set += c
    }
    var spread = 3
    while (set.size < 4) {
        val c = ans + random.nextInt(-spread, spread + 1)
        if (allowNegative || c >= 0) set += c
        spread++
    }
    return set.shuffled(random)
}

/** One round: [totalQuestions] questions, the round ends early when the hearts run out. */
class GameSession(
    val grade: Grade,
    val op: Operation,
    val totalQuestions: Int = 10,
    val maxLives: Int = 3,
    random: Random = Random.Default,
) {
    data class Outcome(val correct: Boolean, val points: Int, val question: Question)

    private val generator = QuestionGenerator(grade, op, random)

    var current: Question = generator.next(); private set
    var lives = maxLives; private set
    var score = 0; private set
    var correct = 0; private set
    var answered = 0; private set
    var streak = 0; private set
    var bestStreak = 0; private set

    val isOver: Boolean get() = lives <= 0 || answered >= totalQuestions

    /** [value] null means the time ran out. */
    fun submit(value: Int?, secondsLeft: Int): Outcome {
        check(!isOver) { "The round is over" }
        val ok = value == current.answer
        answered++
        var points = 0
        if (ok) {
            correct++
            streak++
            bestStreak = max(bestStreak, streak)
            points = 10 + secondsLeft.coerceAtLeast(0) / 2 + if (streak >= 3) 5 else 0
            score += points
        } else {
            streak = 0
            lives--
        }
        return Outcome(ok, points, current)
    }

    fun nextQuestion(): Question {
        current = generator.next()
        return current
    }

    /** 3 stars: all right; 2: 80 %; 1: half. */
    val stars: Int
        get() = when {
            correct >= totalQuestions -> 3
            correct * 10 >= totalQuestions * 8 -> 2
            correct * 2 >= totalQuestions -> 1
            else -> 0
        }
}
