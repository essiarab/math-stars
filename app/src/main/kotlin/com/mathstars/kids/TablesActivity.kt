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

private val TABLE_COLORS = intArrayOf(
    0xFFF03E3E.toInt(), 0xFFF76707.toInt(), 0xFFE8A200.toInt(), 0xFF37B24D.toInt(),
    0xFF0CA678.toInt(), 0xFF1098AD.toInt(), 0xFF1C7ED6.toInt(), 0xFF4263EB.toInt(),
    0xFF7048E8.toInt(), 0xFFAE3EC9.toInt(), 0xFFD6336C.toInt(), 0xFF868E96.toInt(),
)

fun tableColor(n: Int) = TABLE_COLORS[(n - 1).mod(TABLE_COLORS.size)]

/** Shared bits of the two times-table screens. */
abstract class TablesBase : Activity() {
    protected lateinit var column: LinearLayout
    protected lateinit var progress: Progress

    /** False when there was no player and the screen is closing. */
    protected val ready: Boolean get() = ::progress.isInitialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Profiles.current(this) == null) {
            finish()
            return
        }
        progress = Progress.current(this)
    }

    /** A scrolling column, optionally with a [footer] that stays at the bottom. */
    protected fun setUp(footer: View? = null) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val frame = FrameLayout(this)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }
        frame.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        scroll.addView(frame)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        if (footer != null) {
            val holder = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), dp(16)) }
            holder.addView(footer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            root.addView(holder)
            Ui.limitWidth(footer)
        }
        setContentView(root)
        Ui.applyInsets(root)
    }

    protected fun dp(v: Number) = Ui.dp(this, v)

    protected fun text(s: CharSequence, sizeSp: Float, color: Int = Ui.INK, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    protected fun header(title: String, subtitle: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = text("←", 26f, Ui.INK, bold = true).apply {
            gravity = Gravity.CENTER
            Ui.paint(this, Color.WHITE, 24)
            isClickable = true
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titles.addView(text(title, 30f, 0xFF7048E8.toInt(), bold = true))
        titles.addView(text(subtitle, 15f, Ui.MUTED))
        row.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(row)
    }

    protected fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)
}

/** All the times tables, 1 to 12, with each one's stars. */
class TablesActivity : TablesBase() {
    override fun onResume() {
        super.onResume()
        if (!ready) return
        render()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun render() {
        setUp()
        Ui.limitWidth(column)
        header("Times tables", "Pick a table to learn and practice")

        val perRow = if (resources.configuration.screenWidthDp >= 600) 4 else 3
        for (row in Tables.ALL.chunked(perRow)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (n in row) line.addView(tile(n), LinearLayout.LayoutParams(0, dp(130), 1f).apply {
                marginStart = dp(5); marginEnd = dp(5)
            })
            column.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
        }
    }

    private fun tile(n: Int): View {
        val color = tableColor(n)
        val got = progress.tableStars(n)
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            Ui.paint(this, color, 24)
            elevation = dp(4).toFloat()
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@TablesActivity, TableActivity::class.java).putExtra(TableActivity.EXTRA_TABLE, n))
            }
        }
        tile.addView(text("× $n", 36f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER })
        tile.addView(text(stars(got), 22f, if (got > 0) 0xFFFFE066.toInt() else 0x99FFFFFF.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
        })
        return tile
    }
}

/** One times table: the whole table, a tip, hide-the-answers, and a practice round. */
class TableActivity : TablesBase() {
    companion object {
        const val EXTRA_TABLE = "table"
    }

    private var table = 1
    private var hideAnswers = false
    private val revealed = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        table = intent.getIntExtra(EXTRA_TABLE, 1)
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        render()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun render() {
        val color = tableColor(table)
        val play = text("Practice the $table table ▶", 24f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            Ui.paint(this, Ui.GOOD, 28)
            elevation = dp(4).toFloat()
            isClickable = true
            setOnClickListener { practice() }
        }
        val footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val typing = progress.tablesTyping
        modes.addView(toggle("👆 Pick", !typing) { progress.tablesTyping = false; render() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        modes.addView(toggle("⌨️ Type", typing) { progress.tablesTyping = true; render() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        footer.addView(modes)
        footer.addView(play, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply { topMargin = dp(10) })
        setUp(footer)
        Ui.limitWidth(column)

        val got = progress.tableStars(table)
        val best = progress.tableBest(table)
        header("The $table times table", stars(got) + if (best > 0) "   ·   best $best" else "")

        // Tip
        column.addView(text("💡 ${Tables.tip(table)}", 17f, Ui.INK).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = Ui.rounded(0xFFFFF3BF.toInt(), dp(18).toFloat())
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        // Show / hide answers
        val hide = text(if (hideAnswers) "👀 Show all answers" else "🙈 Hide answers and test yourself", 16f, color, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            Ui.paint(this, Color.WHITE, 22, color, 2)
            isClickable = true
            setOnClickListener {
                hideAnswers = !hideAnswers
                revealed.clear()
                render()
            }
        }
        column.addView(hide, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        if (hideAnswers) {
            column.addView(text("Say the answer, then tap the line to check.", 14f, Ui.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        }

        // The table itself
        for (b in Tables.MULTIPLIERS) {
            val answer = text("", 30f, color, bold = true).apply {
                gravity = Gravity.END
                minWidth = dp(80)
            }
            fun showAnswer() {
                val shown = !hideAnswers || b in revealed
                answer.text = if (shown) "${table * b}" else "?"
                answer.setTextColor(if (shown) color else 0xFFADB5BD.toInt())
            }
            showAnswer()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), 0, dp(20), 0)
                Ui.paint(this, Color.WHITE, 18)
                elevation = dp(1).toFloat()
                if (hideAnswers) {
                    isClickable = true
                    setOnClickListener {
                        if (!revealed.add(b)) revealed.remove(b)
                        showAnswer()
                        Ui.bounce(answer, 1.3f)
                    }
                }
            }
            row.addView(text("$table × $b", 28f, Ui.INK, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("=", 28f, Ui.MUTED, bold = true).apply { setPadding(dp(12), 0, dp(12), 0) })
            row.addView(answer)
            column.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)).apply {
                topMargin = dp(8)
            })
        }
    }

    private fun toggle(label: String, selected: Boolean, onClick: () -> Unit) =
        text(label, 17f, if (selected) Color.WHITE else Ui.INK, bold = true).apply {
            gravity = Gravity.CENTER
            Ui.paint(this, if (selected) Ui.INK else Color.WHITE, 24, 0xFFDEE2E6.toInt(), if (selected) 0 else 2)
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun practice() {
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_GRADE, Grade.G3.name)
                .putExtra(GameActivity.EXTRA_OP, Operation.MULTIPLY.name)
                .putExtra(GameActivity.EXTRA_TYPING, progress.tablesTyping)
                .putExtra(GameActivity.EXTRA_TABLE, table),
        )
    }
}
