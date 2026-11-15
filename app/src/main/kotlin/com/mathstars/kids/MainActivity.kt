package com.mathstars.kids

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Home screen: pick a grade, how to answer, and a game. */
class MainActivity : Activity() {
    private lateinit var progress: Progress
    private lateinit var profile: Profile
    private lateinit var column: LinearLayout
    private var grade = Grade.G1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val player = Profiles.current(this)
        if (player == null) {
            // No player chosen (or it was deleted): back to "Who's playing?"
            startActivity(Intent(this, ProfilesActivity::class.java))
            finish()
            return
        }
        profile = player
        progress = Progress(this, player.id)
        grade = progress.lastGrade

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val frame = FrameLayout(this)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(20)
            setPadding(p, p, p, p)
        }
        frame.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        scroll.addView(frame)
        setContentView(scroll)
        Ui.applyInsets(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(v: Number) = Ui.dp(this, v)

    private fun text(s: CharSequence, sizeSp: Float, color: Int = Ui.INK, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun render() {
        column.removeAllViews()
        Ui.limitWidth(column)

        // Title
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("Math Stars", 38f, 0xFF7048E8.toInt(), bold = true))
        titles.addView(text("Hi ${profile.name}! Let's collect stars!", 16f, Ui.MUTED))
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val total = text("⭐ ${progress.totalStars()}", 20f, Ui.INK, bold = true).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = Ui.rounded(Color.WHITE, dp(24).toFloat(), 0xFFFFE066.toInt(), dp(2))
        }
        header.addView(total)
        column.addView(header, lp())

        // Who is playing; tap to switch to another player
        val player = text("${profile.avatar}  ${profile.name}   ⇄ Switch player", 17f, profile.color, bold = true).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            Ui.paint(this, Color.WHITE, 24, profile.color, 2)
            isClickable = true
            setOnClickListener { switchPlayer() }
        }
        column.addView(player, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })

        // Grades
        column.addView(text("1 · Pick your grade", 20f, bold = true), lp(24))
        val grades = Grade.entries
        for (row in grades.chunked(if (resources.configuration.screenWidthDp >= 600) 3 else 2)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (g in row) line.addView(gradeCard(g), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(5); marginEnd = dp(5)
            })
            column.addView(line, lp(10))
        }

        // Answer mode
        column.addView(text("2 · How do you answer?", 20f, bold = true), lp(24))
        val typing = progress.typing(grade)
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modes.addView(modeButton("👆 Pick", !typing) { progress.setTyping(grade, false); render() },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
        modes.addView(modeButton("⌨️ Type", typing) { progress.setTyping(grade, true); render() },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
        column.addView(modes, lp(10))

        // Games
        column.addView(text("3 · Choose a game", 20f, bold = true), lp(24))
        for (op in Operation.entries) column.addView(opButton(op), lp(10).apply { marginStart = dp(5); marginEnd = dp(5) })
    }

    private fun gradeCard(g: Grade): View {
        val selected = g == grade
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            Ui.paint(this, if (selected) g.color else Color.WHITE, 20, g.color, if (selected) 0 else 3)
            elevation = if (selected) dp(6).toFloat() else dp(1).toFloat()
            isClickable = true
            setOnClickListener {
                grade = g
                progress.lastGrade = g
                render()
            }
        }
        val fg = if (selected) Color.WHITE else g.color
        card.addView(text(g.title, 22f, fg, bold = true))
        card.addView(text(g.hint, 13f, if (selected) 0xEEFFFFFF.toInt() else Ui.MUTED))
        card.addView(text("⭐ ${progress.gradeStars(g)} / ${Operation.entries.size * 3}", 14f, if (selected) Color.WHITE else Ui.INK, bold = true).apply {
            setPadding(0, dp(4), 0, 0)
        })
        return card
    }

    private fun modeButton(label: String, selected: Boolean, onClick: () -> Unit) =
        text(label, 18f, if (selected) Color.WHITE else Ui.INK, bold = true).apply {
            gravity = Gravity.CENTER
            Ui.paint(this, if (selected) Ui.INK else Color.WHITE, 28, 0xFFDEE2E6.toInt(), if (selected) 0 else 2)
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun opButton(op: Operation): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(18), dp(12))
            Ui.paint(this, op.color, 24)
            elevation = dp(4).toFloat()
            isClickable = true
            setOnClickListener { start(op) }
        }
        val badge = text(op.symbol, 34f, op.color, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(Color.WHITE, dp(32).toFloat())
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(64), dp(64)))

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }
        labels.addView(text(op.title, 22f, Color.WHITE, bold = true))
        // An example that shows the level: skip the easy ones like 5 + 0 or 4 × 1
        val sample = QuestionGenerator(grade, op).let { gen ->
            List(20) { gen.next() }.firstOrNull { kotlin.math.abs(it.a) >= 2 && kotlin.math.abs(it.b) >= 2 } ?: gen.next()
        }
        val best = progress.best(grade, op)
        labels.addView(text("like ${sample.text}" + if (best > 0) "  ·  best $best" else "", 14f, 0xEEFFFFFF.toInt()))
        row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val stars = progress.stars(grade, op)
        row.addView(text("★".repeat(stars) + "☆".repeat(3 - stars), 24f, if (stars > 0) 0xFFFFE066.toInt() else 0x99FFFFFF.toInt(), bold = true))
        return row
    }

    private fun switchPlayer() {
        if (isTaskRoot) startActivity(Intent(this, ProfilesActivity::class.java))
        finish()
    }

    private fun start(op: Operation) {
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_GRADE, grade.name)
                .putExtra(GameActivity.EXTRA_OP, op.name)
                .putExtra(GameActivity.EXTRA_TYPING, progress.typing(grade)),
        )
    }
}
