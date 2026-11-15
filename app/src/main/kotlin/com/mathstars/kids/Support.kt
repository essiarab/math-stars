package com.mathstars.kids

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout

/** One player's best scores, stars and choices, kept on the device (a file per player). */
class Progress(context: Context, profileId: Int) {
    companion object {
        fun fileName(profileId: Int) = "progress_$profileId"

        /** The player who is playing now. */
        fun current(context: Context) = Progress(context, Profiles.currentId(context))
    }

    private val prefs = context.getSharedPreferences(fileName(profileId), Context.MODE_PRIVATE)

    var lastGrade: Grade
        get() = Grade.entries.firstOrNull { it.name == prefs.getString("grade", null) } ?: Grade.G1
        set(value) = prefs.edit().putString("grade", value.name).apply()

    /** Young children pick from four answers; from grade 3 on they type the answer by default. */
    fun typing(grade: Grade): Boolean = prefs.getBoolean("typing_${grade.name}", grade.number >= 3)
    fun setTyping(grade: Grade, typing: Boolean) = prefs.edit().putBoolean("typing_${grade.name}", typing).apply()

    fun stars(grade: Grade, op: Operation) = prefs.getInt("stars_${grade.name}_${op.name}", 0)
    fun best(grade: Grade, op: Operation) = prefs.getInt("best_${grade.name}_${op.name}", 0)

    fun gradeStars(grade: Grade) = Operation.entries.sumOf { stars(grade, it) }
    fun totalStars() = Grade.entries.sumOf { gradeStars(it) } + Tables.ALL.sumOf { tableStars(it) }

    // Times tables
    fun tableStars(table: Int) = prefs.getInt("stars_table_$table", 0)
    fun tableBest(table: Int) = prefs.getInt("best_table_$table", 0)
    var tablesTyping: Boolean
        get() = prefs.getBoolean("typing_tables", false)
        set(value) = prefs.edit().putBoolean("typing_tables", value).apply()

    /** Saves a times-table round; true when it is a new best score. */
    fun recordTable(table: Int, score: Int, stars: Int): Boolean {
        val newBest = score > tableBest(table)
        prefs.edit().apply {
            if (newBest) putInt("best_table_$table", score)
            if (stars > tableStars(table)) putInt("stars_table_$table", stars)
        }.apply()
        return newBest
    }

    /** Saves the round; true when it is a new best score. */
    fun record(grade: Grade, op: Operation, score: Int, stars: Int): Boolean {
        val newBest = score > best(grade, op)
        prefs.edit().apply {
            if (newBest) putInt("best_${grade.name}_${op.name}", score)
            if (stars > stars(grade, op)) putInt("stars_${grade.name}_${op.name}", stars)
        }.apply()
        return newBest
    }
}

object Ui {
    const val INK = 0xFF2B2D42.toInt()
    const val MUTED = 0xFF8D99AE.toInt()
    const val GOOD = 0xFF2F9E44.toInt()
    const val BAD = 0xFFE03131.toInt()
    const val GOLD = 0xFFFAB005.toInt()

    fun dp(context: Context, value: Number): Int =
        (value.toFloat() * context.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radius: Float, strokeColor: Int = Color.TRANSPARENT, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    /** A rounded, coloured background that ripples when pressed. */
    fun paint(view: View, color: Int, radiusDp: Int = 20, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0) {
        val c = view.context
        view.background = RippleDrawable(
            ColorStateList.valueOf(0x40000000),
            rounded(color, dp(c, radiusDp).toFloat(), strokeColor, dp(c, strokeDp)),
            null,
        )
    }

    /** Keeps content clear of the status and navigation bars (Android draws edge to edge). */
    fun applyInsets(root: View) {
        val l = root.paddingLeft
        val t = root.paddingTop
        val r = root.paddingRight
        val b = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    l + insets.systemWindowInsetLeft, t + insets.systemWindowInsetTop,
                    r + insets.systemWindowInsetRight, b + insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        root.requestApplyInsets()
    }

    /** On tablets, keeps a column from stretching across the whole screen. */
    fun limitWidth(column: View, maxDp: Int = 640) {
        val c = column.context
        val screenDp = c.resources.configuration.screenWidthDp
        val lp = column.layoutParams
        if (screenDp > maxDp + 40) {
            lp.width = dp(c, maxDp)
            if (lp is FrameLayout.LayoutParams) lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
            if (lp is android.widget.LinearLayout.LayoutParams) lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        column.layoutParams = lp
    }

    fun bounce(view: View, scale: Float = 1.12f) {
        view.animate().cancel()
        view.animate().scaleX(scale).scaleY(scale).setDuration(130).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }.start()
    }

    fun shake(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, 28f, -28f, 18f, -18f, 8f, -8f, 0f)
            .setDuration(450)
            .start()
    }
}

/** Short beeps for right and wrong answers. */
class Sounds {
    private val tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 70)
    } catch (e: RuntimeException) {
        null
    }

    fun good() = play(ToneGenerator.TONE_PROP_ACK, 150)
    fun bad() = play(ToneGenerator.TONE_PROP_NACK, 250)
    fun finish() = play(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)

    private fun play(tone: Int, ms: Int) {
        try {
            tones?.startTone(tone, ms)
        } catch (_: RuntimeException) {
        }
    }

    fun release() = tones?.release()
}
