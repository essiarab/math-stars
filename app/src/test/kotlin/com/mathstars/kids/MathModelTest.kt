package com.mathstars.kids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathModelTest {
    private val ops = listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY)

    private fun sample(grade: Grade, op: Operation, n: Int = 3000): List<Question> {
        val gen = QuestionGenerator(grade, op, Random(grade.ordinal * 10 + op.ordinal))
        return List(n) { gen.next() }
    }

    @Test
    fun answersAreRight() {
        for (g in Grade.entries) for (op in Operation.entries) for (q in sample(g, op)) {
            val expected = when (q.op) {
                Operation.ADD -> q.a + q.b
                Operation.SUBTRACT -> q.a - q.b
                Operation.MULTIPLY -> q.a * q.b
                Operation.MIXED -> error("A question is never MIXED")
            }
            assertEquals(q.toString(), expected, q.answer)
            if (op != Operation.MIXED) assertEquals(op, q.op)
        }
    }

    @Test
    fun noNegativeNumbersBeforeGrade6() {
        for (g in Grade.entries.filter { !it.allowsNegative }) for (op in Operation.entries) for (q in sample(g, op)) {
            assertTrue("$g $q", q.a >= 0 && q.b >= 0 && q.answer >= 0)
        }
    }

    @Test
    fun grade6UsesNegativeNumbers() {
        for (op in ops) {
            val qs = sample(Grade.G6, op)
            assertTrue(op.name, qs.all { it.a < 0 || it.b < 0 || it.answer < 0 })
            assertTrue(op.name, qs.none { it.a == 0 || it.b == 0 })
        }
    }

    @Test
    fun rangesFitTheGrade() {
        val maxSum = mapOf(Grade.G1 to 20, Grade.G2 to 100, Grade.G3 to 999, Grade.G4 to 9999, Grade.G5 to 99999)
        for ((g, max) in maxSum) {
            for (q in sample(g, Operation.ADD)) assertTrue("$g $q", q.answer <= max)
            for (q in sample(g, Operation.SUBTRACT)) assertTrue("$g $q", q.a <= max)
        }
        for (q in sample(Grade.G1, Operation.MULTIPLY)) assertTrue("$q", q.answer <= 10)
        for (q in sample(Grade.G2, Operation.MULTIPLY)) assertTrue("$q", q.answer <= 100)
        for (q in sample(Grade.G3, Operation.MULTIPLY)) assertTrue("$q", q.a <= 10 && q.b <= 10)
    }

    @Test
    fun questionsDoNotRepeatBackToBack() {
        for (g in Grade.entries) for (op in Operation.entries) {
            sample(g, op, 500).zipWithNext().forEach { (x, y) -> assertFalse("$g $op $x", x == y) }
        }
    }

    @Test
    fun choicesAreFourDistinctAndIncludeTheAnswer() {
        val random = Random(7)
        for (g in Grade.entries) for (op in Operation.entries) for (q in sample(g, op, 500)) {
            val c = choicesFor(q, g.allowsNegative, random)
            assertEquals(4, c.size)
            assertEquals(4, c.toSet().size)
            assertTrue(q.answer in c)
            if (!g.allowsNegative) assertTrue("$q $c", c.all { it >= 0 })
        }
    }

    @Test
    fun formatting() {
        assertEquals("7 + 5", Question(7, 5, Operation.ADD, 12).text)
        assertEquals("−3 × (−4)", Question(-3, -4, Operation.MULTIPLY, 12).text)
        assertEquals("−12", Question.format(-12))
    }

    @Test
    fun perfectRoundGivesThreeStars() {
        val s = GameSession(Grade.G2, Operation.ADD, random = Random(1))
        while (!s.isOver) {
            assertTrue(s.submit(s.current.answer, secondsLeft = 10).correct)
            if (!s.isOver) s.nextQuestion()
        }
        assertEquals(10, s.correct)
        assertEquals(3, s.stars)
        assertEquals(3, s.lives)
        assertEquals(10, s.bestStreak)
        // 10 points + 5 time bonus each, +5 streak bonus from the third answer on
        assertEquals(10 * 15 + 8 * 5, s.score)
    }

    @Test
    fun threeMistakesEndTheRound() {
        val s = GameSession(Grade.G1, Operation.SUBTRACT, random = Random(2))
        repeat(3) {
            assertFalse(s.isOver)
            assertFalse(s.submit(null, 0).correct)
            if (!s.isOver) s.nextQuestion()
        }
        assertTrue(s.isOver)
        assertEquals(0, s.lives)
        assertEquals(0, s.score)
        assertEquals(0, s.stars)
    }

    @Test
    fun starsThresholds() {
        fun starsFor(right: Int): Int {
            val s = GameSession(Grade.G3, Operation.MULTIPLY, maxLives = 10, random = Random(3))
            repeat(10) { i ->
                s.submit(if (i < right) s.current.answer else Int.MIN_VALUE, 5)
                if (!s.isOver) s.nextQuestion()
            }
            return s.stars
        }
        assertEquals(3, starsFor(10))
        assertEquals(2, starsFor(9))
        assertEquals(2, starsFor(8))
        assertEquals(1, starsFor(7))
        assertEquals(1, starsFor(5))
        assertEquals(0, starsFor(4))
    }
}
