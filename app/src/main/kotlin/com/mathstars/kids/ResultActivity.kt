package com.mathstars.kids

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.random.Random

class ResultActivity : Activity() {
    companion object {
        const val EXTRA_SCORE = "score"
        const val EXTRA_CORRECT = "correct"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_STARS = "stars"
        const val EXTRA_STREAK = "streak"
        const val EXTRA_NEW_BEST = "newBest"
        const val EXTRA_OUT_OF_LIVES = "outOfLives"
    }

    private lateinit var sounds: Sounds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sounds = Sounds()
        build(animate = savedInstanceState == null)
    }

    override fun onDestroy() {
        super.onDestroy()
        sounds.release()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        build(animate = false)
    }

    private fun dp(v: Number) = Ui.dp(this, v)

    private fun text(s: CharSequence, sizeSp: Float, color: Int = Ui.INK, bold: Boolean = true) = TextView(this).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    private fun build(animate: Boolean) {
        val grade = Grade.valueOf(intent.getStringExtra(GameActivity.EXTRA_GRADE) ?: Grade.G1.name)
        val op = Operation.valueOf(intent.getStringExtra(GameActivity.EXTRA_OP) ?: Operation.ADD.name)
        val table = intent.getIntExtra(GameActivity.EXTRA_TABLE, 0).takeIf { it in Tables.ALL }
        val score = intent.getIntExtra(EXTRA_SCORE, 0)
        val correct = intent.getIntExtra(EXTRA_CORRECT, 0)
        val total = intent.getIntExtra(EXTRA_TOTAL, 10)
        val stars = intent.getIntExtra(EXTRA_STARS, 0)
        val streak = intent.getIntExtra(EXTRA_STREAK, 0)
        val newBest = intent.getBooleanExtra(EXTRA_NEW_BEST, false)
        val outOfLives = intent.getBooleanExtra(EXTRA_OUT_OF_LIVES, false)

        val root = FrameLayout(this)
        val confetti = ConfettiView(this)
        root.addView(confetti, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val holder = FrameLayout(this)
        holder.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        scroll.addView(holder)
        root.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        Ui.limitWidth(col, 520)

        val name = Profiles.current(this)?.name?.let { ", $it" } ?: ""
        val title = when (stars) {
            3 -> "Superstar$name! 🌟"
            2 -> "Great job$name! 🎉"
            1 -> "Good try$name! 👍"
            else -> "Keep practicing$name! 💪"
        }
        col.addView(text(title, 34f, 0xFF7048E8.toInt()))
        val sub = if (outOfLives) "Out of hearts — try again!" else if (table != null) "The $table times table" else "${grade.title} · ${op.title}"
        col.addView(text(sub, 16f, Ui.MUTED, bold = false).apply { setPadding(0, dp(4), 0, 0) })

        // Stars
        val starRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        for (i in 0 until 3) {
            text("★", if (i == 1) 88f else 70f, if (i < stars) Ui.GOLD else 0xFFDEE2E6.toInt()).also {
                starRow.addView(it)
                if (animate && i < stars) {
                    it.scaleX = 0f; it.scaleY = 0f
                    it.animate().scaleX(1f).scaleY(1f).setStartDelay(300L + i * 350L).setDuration(350)
                        .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
                }
            }
        }
        col.addView(starRow)

        // Numbers
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = Ui.rounded(Color.WHITE, dp(24).toFloat())
            elevation = dp(3).toFloat()
        }
        card.addView(text("⭐ $score points", 30f))
        card.addView(text("✅ $correct of $total right", 20f, Ui.INK, bold = false).apply { setPadding(0, dp(8), 0, 0) })
        if (streak >= 2) card.addView(text("🔥 $streak in a row", 20f, Ui.INK, bold = false).apply { setPadding(0, dp(4), 0, 0) })
        if (newBest && score > 0) {
            card.addView(text("🏆 New best score!", 22f, 0xFFF76707.toInt()).apply { setPadding(0, dp(10), 0, 0) })
        } else {
            card.addView(text("Best: ${Progress.current(this).let { p -> if (table != null) p.tableBest(table) else p.best(grade, op) }}", 16f, Ui.MUTED, bold = false).apply { setPadding(0, dp(8), 0, 0) })
        }
        col.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        // Buttons
        val again = text("Play again ▶", 24f, Color.WHITE).apply {
            Ui.paint(this, Ui.GOOD, 28)
            elevation = dp(4).toFloat()
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@ResultActivity, GameActivity::class.java).putExtras(intent))
                finish()
            }
        }
        col.addView(again, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply { topMargin = dp(24) })
        val menu = text("Choose another game", 20f, Ui.INK).apply {
            Ui.paint(this, Color.WHITE, 28, 0xFFDEE2E6.toInt(), 2)
            isClickable = true
            setOnClickListener { finish() }
        }
        col.addView(menu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply { topMargin = dp(12) })

        setContentView(root)
        Ui.applyInsets(scroll)

        if (animate) {
            if (stars >= 1) sounds.finish()
            if (stars >= 2) confetti.post { confetti.burst(if (stars == 3) 180 else 90) }
        }
    }
}

/** Falling paper pieces for a good round. */
class ConfettiView(context: Context) : View(context) {
    private class Piece(var x: Float, var y: Float, val vx: Float, var vy: Float, var angle: Float, val spin: Float, val color: Int, val w: Float, val h: Float)

    private val pieces = mutableListOf<Piece>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(0xFFF03E3E.toInt(), 0xFF1C7ED6.toInt(), 0xFF37B24D.toInt(), 0xFFFAB005.toInt(), 0xFF7048E8.toInt(), 0xFFF76707.toInt())
    private var lastFrame = 0L

    fun burst(count: Int) {
        val d = resources.displayMetrics.density
        repeat(count) {
            pieces += Piece(
                x = Random.nextFloat() * width,
                y = -Random.nextFloat() * height * 0.6f,
                vx = (Random.nextFloat() - 0.5f) * 120 * d,
                vy = (150 + Random.nextFloat() * 200) * d,
                angle = Random.nextFloat() * 360,
                spin = (Random.nextFloat() - 0.5f) * 720,
                color = colors.random(),
                w = (6 + Random.nextFloat() * 6) * d,
                h = (10 + Random.nextFloat() * 8) * d,
            )
        }
        lastFrame = System.nanoTime()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        if (pieces.isEmpty()) return
        val now = System.nanoTime()
        val dt = ((now - lastFrame) / 1e9f).coerceAtMost(0.05f)
        lastFrame = now
        val it = pieces.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 60 * resources.displayMetrics.density * dt
            p.angle += p.spin * dt
            if (p.y > height + p.h * 2) { it.remove(); continue }
            paint.color = p.color
            canvas.save()
            canvas.rotate(p.angle, p.x, p.y)
            canvas.drawRect(p.x - p.w / 2, p.y - p.h / 2, p.x + p.w / 2, p.y + p.h / 2, paint)
            canvas.restore()
        }
        if (pieces.isNotEmpty()) postInvalidateOnAnimation()
    }
}
