package com.mathstars.kids

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val id: Int, val name: String, val avatar: String) {
    val color: Int get() = Profiles.colorFor(avatar)
}

/** The players on this device. Each one has their own Progress file. */
object Profiles {
    const val MAX = 12
    val AVATARS = listOf("🐱", "🐶", "🦊", "🐼", "🐸", "🦁", "🐵", "🐰", "🐯", "🦄", "🐨", "🐙")
    private val COLORS = intArrayOf(
        0xFFF76707.toInt(), 0xFF1C7ED6.toInt(), 0xFFF03E3E.toInt(), 0xFF495057.toInt(),
        0xFF37B24D.toInt(), 0xFFE8A200.toInt(), 0xFF9C6644.toInt(), 0xFFE64980.toInt(),
        0xFFFD7E14.toInt(), 0xFF7048E8.toInt(), 0xFF868E96.toInt(), 0xFFAE3EC9.toInt(),
    )

    fun colorFor(avatar: String) = COLORS[AVATARS.indexOf(avatar).coerceAtLeast(0) % COLORS.size]

    private fun prefs(c: Context) = c.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun all(c: Context): List<Profile> {
        val json = prefs(c).getString("list", null) ?: return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Profile(o.getInt("id"), o.getString("name"), o.getString("avatar"))
        }
    }

    private fun save(c: Context, list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar)) }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, name: String, avatar: String): Profile {
        val p = prefs(c)
        val id = p.getInt("nextId", 1)
        p.edit().putInt("nextId", id + 1).apply()
        val profile = Profile(id, name, avatar)
        save(c, all(c) + profile)
        return profile
    }

    fun update(c: Context, profile: Profile) = save(c, all(c).map { if (it.id == profile.id) profile else it })

    /** Removes the player and all their stars and scores. */
    fun delete(c: Context, id: Int) {
        save(c, all(c).filter { it.id != id })
        c.deleteSharedPreferences(Progress.fileName(id))
        if (currentId(c) == id) prefs(c).edit().remove("current").apply()
    }

    fun currentId(c: Context) = prefs(c).getInt("current", -1)
    fun current(c: Context): Profile? = currentId(c).let { id -> all(c).firstOrNull { it.id == id } }
    fun setCurrent(c: Context, id: Int) = prefs(c).edit().putInt("current", id).apply()
}

/** "Who's playing?": the first screen. Tap a player to play; long-press to edit or delete. */
class ProfilesActivity : Activity() {
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { isFillViewport = true }
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
        // A brand new install: go straight to making the first player
        if (savedInstanceState == null && Profiles.all(this).isEmpty()) column.post { showEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun dp(v: Number) = Ui.dp(this, v)

    private fun text(s: CharSequence, sizeSp: Float, color: Int = Ui.INK, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    private fun render() {
        column.removeAllViews()
        Ui.limitWidth(column)

        column.addView(text("Math Stars", 38f, 0xFF7048E8.toInt(), bold = true).apply { gravity = Gravity.CENTER })
        column.addView(text("Who's playing?", 26f, Ui.INK, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        })

        val profiles = Profiles.all(this)
        val cards = profiles.map { profileCard(it) }.toMutableList()
        if (profiles.size < Profiles.MAX) cards += addCard()

        val perRow = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        for (row in cards.chunked(perRow)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { line.addView(it, cardParams()) }
            // Empty space keeps the last row's cards the same size as the others
            repeat(perRow - row.size) { line.addView(View(this), cardParams()) }
            column.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
        }

        if (profiles.isNotEmpty()) {
            column.addView(text("Press and hold a player to change or delete them", 14f, Ui.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
            })
        }
    }

    private fun cardParams() = LinearLayout.LayoutParams(0, dp(190), 1f).apply {
        marginStart = dp(6); marginEnd = dp(6)
    }

    private fun profileCard(p: Profile): View {
        val progress = Progress(this, p.id)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            Ui.paint(this, Color.WHITE, 26, p.color, 3)
            elevation = dp(3).toFloat()
            isClickable = true
            setOnClickListener { play(p) }
            setOnLongClickListener { manage(p); true }
        }
        card.addView(text(p.avatar, 52f).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded((p.color and 0x00FFFFFF) or 0x26000000, dp(44).toFloat())
        }, LinearLayout.LayoutParams(dp(88), dp(88)))
        card.addView(text(p.name, 22f, p.color, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, 0)
        })
        card.addView(text("⭐ ${progress.totalStars()}  ·  ${progress.lastGrade.title}", 15f, Ui.INK).apply { gravity = Gravity.CENTER })
        return card
    }

    private fun addCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            Ui.paint(this, 0x80FFFFFF.toInt(), 26, 0xFFCED4DA.toInt(), 3)
            isClickable = true
            setOnClickListener { showEditor(null) }
        }
        card.addView(text("+", 52f, Ui.MUTED, bold = true).apply { gravity = Gravity.CENTER })
        card.addView(text("Add player", 20f, Ui.MUTED, bold = true).apply { gravity = Gravity.CENTER })
        return card
    }

    private fun play(p: Profile) {
        Profiles.setCurrent(this, p.id)
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun manage(p: Profile) {
        AlertDialog.Builder(this)
            .setTitle("${p.avatar}  ${p.name}")
            .setItems(arrayOf("✏️  Change name or animal", "🗑️  Delete player")) { _, which ->
                if (which == 0) showEditor(p) else confirmDelete(p)
            }
            .show()
    }

    private fun confirmDelete(p: Profile) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${p.name}?")
            .setMessage("All of ${p.name}'s stars and best scores will be erased. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Profiles.delete(this, p.id)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** New player ([existing] null) or changes to one: name, animal, grade. */
    private fun showEditor(existing: Profile?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }

        box.addView(text("Name", 16f, Ui.MUTED, bold = true))
        val name = EditText(this).apply {
            setText(existing?.name ?: "")
            hint = "Type a name"
            textSize = 22f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(16))
            setSingleLine()
        }
        box.addView(name)

        // Animal
        box.addView(text("Pick an animal", 16f, Ui.MUTED, bold = true).apply { setPadding(0, dp(16), 0, dp(4)) })
        val used = Profiles.all(this).filter { it.id != existing?.id }.map { it.avatar }.toSet()
        var avatar = existing?.avatar ?: (Profiles.AVATARS.firstOrNull { it !in used } ?: Profiles.AVATARS[0])
        val avatarViews = mutableListOf<TextView>()
        fun paintAvatars() = avatarViews.forEach { v ->
            val sel = v.text == avatar
            v.background = Ui.rounded(if (sel) (Profiles.colorFor(avatar) and 0x00FFFFFF) or 0x40000000 else Color.TRANSPARENT,
                dp(14).toFloat(), if (sel) Profiles.colorFor(avatar) else Color.TRANSPARENT, if (sel) dp(2) else 0)
        }
        for (row in Profiles.AVATARS.chunked(6)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (a in row) {
                val v = text(a, 30f).apply {
                    gravity = Gravity.CENTER
                    isClickable = true
                    setOnClickListener { avatar = a; paintAvatars() }
                }
                avatarViews += v
                line.addView(v, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            box.addView(line)
        }
        paintAvatars()

        // Grade
        box.addView(text("School grade", 16f, Ui.MUTED, bold = true).apply { setPadding(0, dp(16), 0, dp(4)) })
        var grade = existing?.let { Progress(this, it.id).lastGrade } ?: Grade.G1
        val gradeViews = mutableListOf<Pair<Grade, TextView>>()
        fun paintGrades() = gradeViews.forEach { (g, v) ->
            val sel = g == grade
            v.setTextColor(if (sel) Color.WHITE else g.color)
            v.background = Ui.rounded(if (sel) g.color else Color.WHITE, dp(12).toFloat(), g.color, dp(2))
        }
        val gradeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (g in Grade.entries) {
            val v = text(g.number.toString(), 20f, g.color, bold = true).apply {
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener { grade = g; paintGrades() }
            }
            gradeViews += g to v
            gradeRow.addView(v, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        box.addView(gradeRow)
        paintGrades()

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New player" else "Change player")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            // Our own click handler so an empty name keeps the dialog open
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim()
                if (n.isEmpty()) {
                    name.error = "Type a name first"
                    return@setOnClickListener
                }
                val saved = if (existing == null) {
                    Profiles.add(this, n, avatar)
                } else {
                    existing.copy(name = n, avatar = avatar).also { Profiles.update(this, it) }
                }
                Progress(this, saved.id).lastGrade = grade
                dialog.dismiss()
                render()
            }
        }
        dialog.show()
    }
}
