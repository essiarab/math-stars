package com.mathstars.kids

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.window.OnBackInvokedDispatcher

class GameActivity : Activity() {
    companion object {
        const val EXTRA_GRADE = "grade"
        const val EXTRA_OP = "op"
        const val EXTRA_TYPING = "typing"
        private const val MAX_DIGITS = 7
        private val CHOICE_COLORS = intArrayOf(0xFFF03E3E.toInt(), 0xFF1C7ED6.toInt(), 0xFF37B24D.toInt(), 0xFFE8A200.toInt())
        private val PRAISE = listOf("Great job!", "Awesome!", "You got it!", "Super!", "Brilliant!", "Fantastic!", "Well done!")
    }

    private lateinit var session: GameSession
    private lateinit var sounds: Sounds
    private var typing = false

    private lateinit var root: FrameLayout
    private lateinit var hearts: TextView
    private lateinit var scoreView: TextView
    private lateinit var counter: TextView
    private lateinit var streakView: TextView
    private lateinit var timerBar: ProgressBar
    private lateinit var questionCard: LinearLayout
    private lateinit var questionView: TextView
    private lateinit var feedback: TextView
    private lateinit var answerBox: TextView
    private val choiceButtons = mutableListOf<TextView>()
    private var choices = listOf<Int>()

    // Typed answer
    private val digits = StringBuilder()
    private var negative = false

    // Timer: runs only while the activity is in front and a question is waiting
    private val handler = Handler(Looper.getMainLooper())
    private var totalMs = 0L
    private var remainingMs = 0L
    private var lastTick = 0L
    private var timerRunning = false
    private var inFront = false
    private var dialogOpen = false
    private var locked = false
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            remainingMs -= now - lastTick
            lastTick = now
            showTimer()
            if (remainingMs <= 0) {
                timerRunning = false
                answer(null)
            } else {
                handler.postDelayed(this, 50)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val grade = Grade.valueOf(intent.getStringExtra(EXTRA_GRADE) ?: Grade.G1.name)
        val op = Operation.valueOf(intent.getStringExtra(EXTRA_OP) ?: Operation.ADD.name)
        typing = intent.getBooleanExtra(EXTRA_TYPING, false)
        session = GameSession(grade, op)
        sounds = Sounds()

        buildUi()
        showQuestion()

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { confirmExit() }
        }
    }

    @Deprecated("Android 12 and older only; newer versions use the OnBackInvokedCallback above")
    override fun onBackPressed() = confirmExit()

    override fun onResume() {
        super.onResume()
        inFront = true
        runTimerIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        inFront = false
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        sounds.release()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rebuild the screen for the new size, keeping the round, the question and the typed answer
        handler.removeCallbacks(tick)
        val oldFeedback = feedback.text
        val oldFeedbackColor = feedback.currentTextColor
        buildUi()
        renderQuestion()
        feedback.text = oldFeedback
        feedback.setTextColor(oldFeedbackColor)
        if (locked) disableInput()
        runTimerIfNeeded()
    }

    private fun dp(v: Number) = Ui.dp(this, v)

    private fun text(sizeSp: Float, color: Int = Ui.INK, bold: Boolean = true) = TextView(this).apply {
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    // ---------- Layout ----------

    private fun buildUi() {
        choiceButtons.clear()
        // clipChildren off: bouncing views may grow past their parents for a moment
        root = FrameLayout(this).apply { clipChildren = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
            clipChildren = false
            clipToPadding = false
        }
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        Ui.limitWidth(col, 700)

        // Top bar: close, hearts, score
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        val close = text(22f, Ui.MUTED).apply {
            text = "✕"
            gravity = Gravity.CENTER
            Ui.paint(this, Color.WHITE, 24)
            isClickable = true
            setOnClickListener { confirmExit() }
        }
        top.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        hearts = text(24f).apply { setPadding(dp(12), 0, 0, 0) }
        top.addView(hearts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        scoreView = text(22f).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = Ui.rounded(Color.WHITE, dp(22).toFloat(), 0xFFFFE066.toInt(), dp(2))
        }
        top.addView(scoreView)
        col.addView(top)

        // Question number, streak and timer
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(12), dp(4), dp(6))
        }
        counter = text(16f, Ui.MUTED)
        info.addView(counter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        streakView = text(16f, 0xFFF76707.toInt())
        info.addView(streakView)
        col.addView(info)

        timerBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressBackgroundTintList = ColorStateList.valueOf(0xFFE9ECEF.toInt())
        }
        col.addView(timerBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)))

        // The question
        questionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(12))
            background = Ui.rounded(Color.WHITE, dp(28).toFloat())
            elevation = dp(4).toFloat()
        }
        questionView = text(56f).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(20, 64, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        questionCard.addView(questionView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(84)))
        if (typing) {
            answerBox = text(44f).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setAutoSizeTextTypeUniformWithConfiguration(20, 44, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
                background = Ui.rounded(0xFFF1F3F5.toInt(), dp(18).toFloat(), 0xFFDEE2E6.toInt(), dp(2))
            }
            questionCard.addView(answerBox, LinearLayout.LayoutParams(dp(220), dp(68)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
            })
        }
        feedback = text(20f).apply {
            gravity = Gravity.CENTER
            minLines = 2
        }
        questionCard.addView(feedback, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        col.addView(questionCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        // Answer area fills the rest
        val input = if (typing) keypad() else choiceGrid()
        col.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(16)
        })

        setContentView(root)
        Ui.applyInsets(root)
        showHud()
    }

    private fun choiceGrid(): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 2) {
                val i = r * 2 + c
                val b = text(40f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setAutoSizeTextTypeUniformWithConfiguration(18, 48, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    elevation = dp(4).toFloat()
                    isClickable = true
                    setOnClickListener { if (i < choices.size) answer(choices[i], this) }
                }
                choiceButtons += b
                row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        return grid
    }

    private fun keypad(): View {
        val pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val minusKey = if (session.grade.allowsNegative) "±" else "C"
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(minusKey, "0", "⌫"))
        for (keys in rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in keys) {
                val digit = k[0].isDigit()
                val b = text(30f, if (digit) Ui.INK else 0xFF7048E8.toInt()).apply {
                    text = k
                    gravity = Gravity.CENTER
                    Ui.paint(this, if (digit) Color.WHITE else 0xFFF3F0FF.toInt(), 18)
                    elevation = dp(2).toFloat()
                    isClickable = true
                    setOnClickListener { key(k) }
                }
                choiceButtons += b
                row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            pad.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val check = text(26f, Color.WHITE).apply {
            text = "Check ✓"
            gravity = Gravity.CENTER
            Ui.paint(this, Ui.GOOD, 22)
            elevation = dp(4).toFloat()
            isClickable = true
            setOnClickListener { submitTyped() }
        }
        choiceButtons += check
        pad.addView(check, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.15f).apply {
            setMargins(dp(5), dp(8), dp(5), dp(5))
        })
        return pad
    }

    // ---------- Game flow ----------

    private fun showQuestion() {
        locked = false
        digits.clear()
        negative = false
        feedback.text = ""
        if (!typing) choices = choicesFor(session.current, session.grade.allowsNegative)
        // Debug builds only: lets an automated test on a device read the question
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.util.Log.d("MathStars", "Q ${session.answered + 1}: ${session.current.text} = ${session.current.answer} choices=$choices")
        }
        renderQuestion()
        choiceButtons.forEach { it.isEnabled = true; it.alpha = 1f }
        totalMs = session.grade.secondsPerQuestion * 1000L
        remainingMs = totalMs
        timerRunning = true
        showTimer()
        runTimerIfNeeded()
        Ui.bounce(questionCard, 1.04f)
    }

    /** Draws the current question, answer buttons and typed answer (also after a screen rotation). */
    private fun renderQuestion() {
        questionView.text = "${session.current.text} = ?"
        if (typing) {
            showTyped()
        } else {
            choiceButtons.forEachIndexed { i, b ->
                b.text = choices.getOrNull(i)?.let { Question.format(it) } ?: ""
                Ui.paint(b, CHOICE_COLORS[i], 26)
            }
        }
        showHud()
        showTimer()
    }

    private fun runTimerIfNeeded() {
        handler.removeCallbacks(tick)
        if (timerRunning && inFront && !dialogOpen) {
            lastTick = SystemClock.uptimeMillis()
            handler.post(tick)
        }
    }

    private fun showTimer() {
        val fraction = if (totalMs == 0L) 0f else (remainingMs.coerceAtLeast(0).toFloat() / totalMs)
        timerBar.progress = (fraction * 1000).toInt()
        val color = when {
            fraction > 0.5f -> Ui.GOOD
            fraction > 0.25f -> 0xFFF59F00.toInt()
            else -> Ui.BAD
        }
        timerBar.progressTintList = ColorStateList.valueOf(color)
    }

    private fun showHud() {
        hearts.text = "❤️".repeat(session.lives) + "🤍".repeat(session.maxLives - session.lives)
        scoreView.text = "⭐ ${session.score}"
        // While the feedback shows, the question just answered is still the current one
        val number = if (locked) session.answered else session.answered + 1
        counter.text = "Question ${number.coerceIn(1, session.totalQuestions)} of ${session.totalQuestions}"
        streakView.text = if (session.streak >= 2) "🔥 ${session.streak} in a row" else ""
    }

    private fun disableInput() {
        choiceButtons.forEach { it.isEnabled = false }
    }

    /** [value] null: the time ran out. [pressed]: the answer button tapped, if any. */
    private fun answer(value: Int?, pressed: TextView? = null) {
        if (locked) return
        locked = true
        timerRunning = false
        handler.removeCallbacks(tick)
        disableInput()

        val secondsLeft = (remainingMs / 1000).toInt()
        val q = session.current
        val outcome = session.submit(value, secondsLeft)
        showHud()

        if (outcome.correct) {
            sounds.good()
            feedback.setTextColor(Ui.GOOD)
            val bonus = if (session.streak >= 3) "  🔥 streak bonus!" else ""
            feedback.text = "${PRAISE.random()} +${outcome.points}$bonus"
            pressed?.let { Ui.paint(it, Ui.GOOD, 26); Ui.bounce(it) }
            if (typing) answerBox.setTextColor(Ui.GOOD)
            Ui.bounce(scoreView, 1.25f)
            Ui.bounce(questionCard, 1.05f)
            handler.postDelayed({ advance() }, 1000)
        } else {
            sounds.bad()
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            feedback.setTextColor(Ui.BAD)
            val why = if (value == null) "Time's up! ⏰" else "Oops! Not quite."
            feedback.text = "$why\n${q.text} = ${Question.format(q.answer)}"
            if (!typing) {
                choiceButtons.forEachIndexed { i, b ->
                    when {
                        choices.getOrNull(i) == q.answer -> { Ui.paint(b, Ui.GOOD, 26); Ui.bounce(b) }
                        b === pressed -> Ui.paint(b, 0xFFADB5BD.toInt(), 26)
                        else -> b.alpha = 0.4f
                    }
                }
            } else {
                answerBox.setTextColor(Ui.BAD)
            }
            Ui.shake(questionCard)
            Ui.bounce(hearts, 1.3f)
            handler.postDelayed({ advance() }, 2300)
        }
    }

    private fun advance() {
        if (session.isOver) {
            finishRound()
        } else {
            session.nextQuestion()
            showQuestion()
        }
    }

    private fun finishRound() {
        val newBest = Progress.current(this).record(session.grade, session.op, session.score, session.stars)
        startActivity(
            Intent(this, ResultActivity::class.java)
                .putExtra(EXTRA_GRADE, session.grade.name)
                .putExtra(EXTRA_OP, session.op.name)
                .putExtra(EXTRA_TYPING, typing)
                .putExtra(ResultActivity.EXTRA_SCORE, session.score)
                .putExtra(ResultActivity.EXTRA_CORRECT, session.correct)
                .putExtra(ResultActivity.EXTRA_TOTAL, session.totalQuestions)
                .putExtra(ResultActivity.EXTRA_STARS, session.stars)
                .putExtra(ResultActivity.EXTRA_STREAK, session.bestStreak)
                .putExtra(ResultActivity.EXTRA_NEW_BEST, newBest)
                .putExtra(ResultActivity.EXTRA_OUT_OF_LIVES, session.lives <= 0),
        )
        finish()
    }

    // ---------- Typing ----------

    private fun key(k: String) {
        if (locked) return
        when (k) {
            "⌫" -> if (digits.isNotEmpty()) digits.deleteCharAt(digits.length - 1) else negative = false
            "C" -> { digits.clear(); negative = false }
            "±" -> negative = !negative
            else -> if (digits.length < MAX_DIGITS) {
                if (digits.toString() == "0") digits.clear()
                digits.append(k)
            }
        }
        showTyped()
    }

    private fun showTyped() {
        answerBox.setTextColor(if (digits.isEmpty() && !negative) 0xFFADB5BD.toInt() else Ui.INK)
        answerBox.text = when {
            digits.isEmpty() && !negative -> "?"
            else -> (if (negative) "−" else "") + digits
        }
    }

    private fun submitTyped() {
        if (locked) return
        if (digits.isEmpty()) {
            Ui.shake(answerBox)
            return
        }
        val n = digits.toString().toInt()
        answer(if (negative) -n else n)
    }

    /** A hardware keyboard also works when typing. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (typing && !locked) {
            when (keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { key((keyCode - KeyEvent.KEYCODE_0).toString()); return true }
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> { key((keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString()); return true }
                KeyEvent.KEYCODE_DEL -> { key("⌫"); return true }
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> { if (session.grade.allowsNegative) key("±"); return true }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { submitTyped(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Leaving ----------

    private fun confirmExit() {
        dialogOpen = true
        handler.removeCallbacks(tick)
        AlertDialog.Builder(this)
            .setTitle("Stop playing?")
            .setMessage("Your score for this round will not be saved.")
            .setPositiveButton("Stop") { _, _ -> finish() }
            .setNegativeButton("Keep playing", null)
            .setOnDismissListener {
                dialogOpen = false
                runTimerIfNeeded()
            }
            .show()
    }
}
