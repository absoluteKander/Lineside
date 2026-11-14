// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.calls

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as SysSettings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.lineside.App
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.NumberInput
import app.lineside.domain.Ball
import app.lineside.domain.Initials
import app.lineside.ui.Fmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlinx.coroutines.launch

/** What the pill shows for one call. Built off the main thread by [CallContext]. */
data class PillContent(
    val loopId: Long?,
    val number: String,
    /** Card heading: the loop's name, or the number for an unknown caller. */
    val title: String,
    /** The collapsed pill's text, e.g. "P · Call back · VM 4d" or "+ Start a loop". */
    val label: String,
    /** A line or two of context for the card: the topic, what they last said, your last note. */
    val context: List<String>,
    /** The single most useful line, for places with room for one (the call notification). */
    val headline: String? = null,
    /** Who this number is within the loop ("Radiology"), when the loop has several parties. */
    val party: String? = null,
    /** Unknown caller: open loops this number could join, most recently active first. */
    val choices: List<LoopChoice> = emptyList(),
    /** Unknown caller whose number was in a loop the user archived: offer to bring it back. */
    val archived: LoopChoice? = null,
    /** The departure board: whose move it is, its status word ("Call back") and the short detail ("VM 4d"). */
    val ball: app.lineside.domain.Ball? = null,
    val status: String = "",
    val detail: String = "",
    val topic: String = "",
    /** The loop's latest stops, oldest first, for the mini line; [needsId] is the one glowing amber. */
    val stops: List<Event> = emptyList(),
    val needsId: Long? = null,
    /** What they last said, and your own latest note: the card's quote blocks. */
    val said: List<Said> = emptyList(),
)

data class Said(val label: String, val text: String, val theirs: Boolean)

data class LoopChoice(val id: Long, val name: String, val detail: String)

/**
 * The call pill: a small draggable overlay that expands into a card with context and a one-line jot field.
 * Plain Views on purpose: an overlay window is simpler without Compose's lifecycle plumbing.
 *
 * Rules (from the spike and the user's design decisions):
 * - only ever shown while unlocked, for a call still in progress or just ended;
 * - after the call it lives 5 s unless touched; a touched card closes after 1 min idle;
 * - never takes focus or raises the keyboard unless the user taps it.
 */
/**
 * The call pill: a small draggable overlay that expands into a card with context and a one-line jot field.
 * Plain Views on purpose: an overlay window is simpler without Compose's lifecycle plumbing.
 *
 * Collapsed, it's a tiny departure board: the loop's route bullet, its last stops as a mini line (amber = needs
 * you), then the status in signage type. Expanded, it's the loop screen's header in miniature.
 *
 * Rules (from the spike and the user's design decisions):
 * - only ever shown while unlocked, for a call still in progress or just ended;
 * - after the call it lives 5 s unless touched; a touched card closes after 1 min idle;
 * - never takes focus or raises the keyboard unless the user taps it;
 * - upper half only, no phone glyph, smaller than any call button.
 */
@SuppressLint("StaticFieldLeak")
object Pill {
    private const val TAG = "Lineside"
    const val SECOND_CHANCE_MS = 5_000L
    const val IDLE_MS = 60_000L
    /** The card was still open when the call ended: give a moment to finish, not a minute. */
    const val HANGUP_IDLE_MS = 15_000L

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    // The board (collapsed pill)
    private var board: LinearLayout? = null
    private var bullet: BulletView? = null
    private var mini: MiniLineView? = null
    private var signTv: TextView? = null
    private var detailTv: TextView? = null

    // The card
    private var card: LinearLayout? = null
    private var cardBullet: BulletView? = null
    private var titleTv: TextView? = null
    private var clockTv: TextView? = null
    private var topicTv: TextView? = null
    private var cardLine: CardLineView? = null
    private var saidBox: LinearLayout? = null
    private var input: EditText? = null
    private var notesTv: TextView? = null
    private var noticeTv: TextView? = null
    private var closeBtn: TextView? = null
    private var doneBtn: TextView? = null
    private var choicesBox: LinearLayout? = null
    private var addedRow: LinearLayout? = null
    private var addedTv: TextView? = null
    private var partyInput: EditText? = null
    private var associatedAt: Long? = null // loop id this number was just added to (for Undo)
    private var idleMs = IDLE_MS

    private var content: PillContent? = null
    private var loopId: Long? = null
    private var expanded = false
    private var afterCall = false
    private var touched = false
    private var closeArmed = false
    private var callStart = 0L
    private var collapsedY = 0
    /** Wall-clock time the pill appeared. Anything the loop logged after this is this call, already drawn as NOW and the jots. */
    private var shownAt = 0L
    /** Notes jotted on this call: seconds into the call (null when unknown) and the text. */
    private val jotted = mutableListOf<Pair<Int?, String>>()

    private val die = Runnable { fadeOut() }
    private val idle = Runnable { Log.d(TAG, "pill card idle, closing"); finish() }
    private val safety = Runnable { if (!afterCall) { Log.d(TAG, "no POST_CALL within 3s; removing pill"); removeNow() } }
    private val tick = object : Runnable {
        override fun run() { renderClock(); if (expanded && callStart > 0 && !afterCall) main.postDelayed(this, 1_000) }
    }

    fun canShow(ctx: Context) = SysSettings.canDrawOverlays(ctx)

    /** A hardware Enter can fire an editor action twice (press and release). Act on the first only. */
    private var lastAction = 0L
    private fun once(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastAction < 250) return false
        lastAction = now; return true
    }

    /** Shows the pill. Synchronous when called on the main thread, so it's on screen before the phone rings. */
    fun show(ctx: Context, c: PillContent, onVisible: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        if (!canShow(app)) return
        if (Looper.myLooper() == Looper.getMainLooper()) showOnMain(app, c, onVisible) else main.post { showOnMain(app, c, onVisible) }
    }

    private fun dp(ctx: Context, v: Float) = PillStyle.dp(ctx, v).toInt()

    /** The signage band's surface, with a thin amber edge so it reads as Lineside and never as a call control. */
    private fun band(ctx: Context, radiusDp: Float) = GradientDrawable().apply {
        cornerRadius = dp(ctx, radiusDp).toFloat()
        setColor(PillStyle.BAND)
        setStroke(dp(ctx, 1.5f), 0x8CFFB547.toInt())
    }

    private fun field(app: Context, hint: String) = EditText(app).apply {
        this.hint = hint
        setHintTextColor(PillStyle.MUTED); setTextColor(PillStyle.INK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        isSingleLine = true; minHeight = dp(app, 44f)
        background = GradientDrawable().apply { setColor(0) }.let { fill ->
            // Just an amber rule under the text.
            android.graphics.drawable.LayerDrawable(arrayOf(fill, GradientDrawable().apply { setColor(PillStyle.AMBER) })).apply {
                setLayerInsetTop(1, dp(app, 42f)); setLayerHeight(1, dp(app, 2f)); setLayerGravity(1, Gravity.BOTTOM)
            }
        }
        setPadding(dp(app, 2f), 0, dp(app, 2f), 0)
    }

    private fun wrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun full(top: Float = 0f, ctx: Context? = null) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (ctx != null) topMargin = dp(ctx, top) }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOnMain(app: Context, c: PillContent, onVisible: (() -> Unit)?) {
        // A new call while the last one's card is still up: close that card properly (half-typed notes are kept), start fresh.
        if (root != null && (afterCall || content?.number != c.number)) { if (afterCall) finish() else removeNow() }
        if (root != null) { apply(c); onVisible?.invoke(); return }
        jotted.clear(); callStart = 0L; expanded = false; afterCall = false; touched = false; closeArmed = false
        shownAt = System.currentTimeMillis()
        val w = app.getSystemService(WindowManager::class.java); wm = w
        val dm = app.resources.displayMetrics

        // ---- The board ----
        bullet = BulletView(app, 26f)
        mini = MiniLineView(app)
        signTv = PillStyle.sign(TextView(app), 16f, PillStyle.INK).apply { maxLines = 1 }
        detailTv = PillStyle.monoText(TextView(app), 11f, PillStyle.SOFT).apply { maxLines = 1; isAllCaps = true }
        board = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = band(app, 21f)
            minimumHeight = dp(app, 42f)
            setPadding(dp(app, 5f), dp(app, 4f), dp(app, 14f), dp(app, 4f))
            addView(bullet, wrap())
            addView(mini, wrap().apply { marginStart = dp(app, 8f) })
            addView(signTv, wrap().apply { marginStart = dp(app, 10f) })
            addView(detailTv, wrap().apply { marginStart = dp(app, 8f) })
            // Never wider than most of the screen; long names and parties ellipsize in the detail.
            detailTv!!.ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // ---- The card ----
        cardBullet = BulletView(app, 28f)
        titleTv = PillStyle.sign(TextView(app), 21f, PillStyle.INK, semi = true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { } // swallow: tapping the title shouldn't fall through to the call screen
        }
        clockTv = PillStyle.monoText(TextView(app), 11f, PillStyle.MUTED).apply { isAllCaps = true }
        val head = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(cardBullet, wrap())
            addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(app, 10f); marginEnd = dp(app, 8f) })
            addView(clockTv, wrap())
        }
        topicTv = TextView(app).apply { setTextColor(PillStyle.SOFT); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); maxLines = 2 }
        cardLine = CardLineView(app)
        saidBox = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        input = field(app, "Jot a note · Enter saves").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, _, _ -> if (once()) submit(app); true }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) = poke()
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        notesTv = TextView(app).apply { setTextColor(PillStyle.BODY); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.3f); visibility = View.GONE }
        noticeTv = TextView(app).apply { setTextColor(PillStyle.SOFT); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); visibility = View.GONE }
        addedTv = PillStyle.sign(TextView(app), 14f, PillStyle.AMBER)
        val undo = PillStyle.sign(TextView(app), 14f, PillStyle.INK).apply {
            text = "Undo"; PillStyle.hollow(this, PillStyle.RULE); setPadding(dp(app, 14f), 0, dp(app, 14f), 0)
            setOnClickListener { undoAssociate(app) }
        }
        addedRow = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
            addView(addedTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); addView(undo)
        }
        partyInput = field(app, "Who's on this number? (optional)").apply {
            visibility = View.GONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            // A hardware Enter fires this on press and release; act once, then move on to the note field.
            setOnEditorActionListener { _, id, ev ->
                Log.d(TAG, "party action id=$id ev=${ev?.action}")
                if (once()) {
                    saveParty(app)
                    input?.let { it.requestFocus(); app.getSystemService(InputMethodManager::class.java).showSoftInput(it, 0) }
                }
                true
            }
        }
        choicesBox = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        doneBtn = PillStyle.sign(TextView(app), 16f, PillStyle.INK).apply {
            text = "Done"; setPadding(dp(app, 18f), 0, dp(app, 18f), 0); setOnClickListener { toggle(app) }
        }
        closeBtn = PillStyle.sign(TextView(app), 16f, PillStyle.INK).apply {
            text = "Close loop"; visibility = View.GONE; setPadding(dp(app, 16f), 0, dp(app, 16f), 0); setOnClickListener { closeLoop(app) }
        }
        val buttons = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            addView(closeBtn, wrap().apply { marginEnd = dp(app, 10f) }); addView(doneBtn, wrap())
        }
        card = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            background = band(app, 18f)
            setPadding(dp(app, 16f), dp(app, 14f), dp(app, 16f), dp(app, 12f))
            addView(head, full())
            addView(topicTv, full(4f, app))
            addView(cardLine, full(12f, app).apply { marginStart = dp(app, 4f); marginEnd = dp(app, 4f) })
            addView(addedRow, full(8f, app))
            addView(partyInput, full(4f, app))
            addView(saidBox, full(4f, app))
            addView(notesTv, full(10f, app))
            addView(input, full(6f, app))
            addView(choicesBox, full(12f, app))
            addView(noticeTv, full(8f, app))
            addView(buttons, full(12f, app))
        }
        val r = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            addView(board, wrap())
            addView(card, LinearLayout.LayoutParams((dm.widthPixels * 0.9).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(app, 6f) })
        }
        root = r
        apply(c)

        val saved = App.of(app).settings.pillPosition
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = saved?.first ?: (dm.widthPixels * 0.05).toInt()
            // 27%: the band Google Phone and the GrapheneOS dialer both leave empty on their call screens.
            y = saved?.second ?: (dm.heightPixels * 0.27).toInt()
        }
        params = lp

        val slop = ViewConfiguration.get(app).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        board!!.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; dragging = false
                    if (afterCall && !touched) { touched = true; main.removeCallbacks(die); r.animate().cancel(); r.alpha = 1f }
                    poke()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) { lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); w.updateViewLayout(r, lp) }
                }
                MotionEvent.ACTION_UP -> if (dragging) App.of(app).settings.pillPosition = lp.x to lp.y else toggle(app)
            }
            true
        }

        runCatching { w.addView(r, lp) }
            .onSuccess {
                Log.d(TAG, "pill shown")
                if (onVisible != null) {
                    var fired = false
                    r.viewTreeObserver.addOnDrawListener { if (!fired) { fired = true; r.post(onVisible) } }
                }
            }
            .onFailure { Log.w(TAG, "pill addView failed", it); root = null }
    }

    private fun apply(c: PillContent) {
        content = c; loopId = c.loopId
        renderChoices(c)
        render()
    }

    private val known get() = loopId != null
    private val live get() = callStart != 0L && !afterCall

    /** Draws everything from [content] and the call's state. Safe to call any time. */
    private fun render() {
        val c = content ?: return
        val app = root?.context ?: return
        val color = PillStyle.loop(app, loopId)
        val name = c.title.substringBefore(" · ")
        val initials = if (known) Initials.of(name) else "?"
        // This call and its notes are drawn once, as NOW and the jots, even after the loop has logged them.
        val stops = if (known) c.stops.filter { it.at < shownAt - 10_000 } else emptyList()

        // The board
        bullet?.bind(color, initials, hollow = !known)
        mini?.bind(color, stops, c.needsId, live && known)
        val sign = when {
            !known && c.archived != null -> "Restore ${c.archived.name}?"
            afterCall && !known -> "+ Who was this?"
            afterCall -> "+ Add a note"
            !known -> "+ Start a loop"
            loopId!! < 0 -> "Saving…"
            c.status.isBlank() -> name
            else -> c.status
        }
        val needsYou = known && !afterCall && c.ball == Ball.CALL_BACK
        signTv?.text = sign
        signTv?.setTextColor(when {
            sign.startsWith("+") || sign.startsWith("Restore") -> PillStyle.INK
            needsYou -> PillStyle.AMBER
            c.ball == Ball.WAITING -> PillStyle.WAITING
            else -> PillStyle.INK
        })
        val detail = when {
            afterCall -> ""
            live -> "Tap to jot"
            !known -> Fmt.last4(c.number)
            else -> listOfNotNull(c.party, c.detail.ifBlank { null }).joinToString(" · ")
        }
        detailTv?.text = detail
        detailTv?.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
        detailTv?.maxWidth = (app.resources.displayMetrics.widthPixels * 0.32).toInt()
        board?.contentDescription = "Lineside: " + listOf(if (known) c.title else "unknown caller", sign, detail)
            .filter { it.isNotBlank() }.joinToString(", ") + ". Tap for details."

        // The card
        cardBullet?.bind(color, initials, hollow = !known)
        titleTv?.text = c.title
        topicTv?.text = c.topic
        topicTv?.visibility = if (c.topic.isBlank() || !known) View.GONE else View.VISIBLE
        val nowLabel = when { live -> "NOW"; afterCall -> "JUST NOW"; else -> null }
        cardLine?.bind(color, stops, c.needsId, if (known) jotted.size else 0, if (known) nowLabel else null)
        renderSaid(app, c)
        renderNotes()
        input?.hint = if (!known) "Who is this? Enter starts a loop" else "Jot a note · Enter saves"
        closeBtn?.visibility = if (afterCall && (loopId ?: -1) > 0) View.VISIBLE else View.GONE
        closeBtn?.let { PillStyle.hollow(it, color) }
        doneBtn?.let { PillStyle.hollow(it, if (known) color else PillStyle.RULE) }
        renderClock()
    }

    private fun renderClock() {
        val tv = clockTv ?: return
        tv.text = when {
            afterCall -> "Call ended"
            callStart > 0 -> ((SystemClock.elapsedRealtime() - callStart) / 1000).let { "On call %d:%02d".format(it / 60, it % 60) }
            callStart < 0 -> "On call"
            else -> "Ringing"
        }
    }

    /** "They said" (amber) and "Your note" (quiet) blocks: the words you need on this call. */
    private fun renderSaid(app: Context, c: PillContent) {
        val box = saidBox ?: return
        box.removeAllViews()
        val items = if (known) c.said.filter { s -> jotted.none { it.second == s.text } } else emptyList()
        box.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        for (s in items) box.addView(LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(app, 10f).toFloat(); setColor(PillStyle.QUOTE) }
            setPadding(dp(app, 11f), dp(app, 8f), dp(app, 11f), dp(app, 9f))
            addView(PillStyle.sign(TextView(app), 13f, if (s.theirs) PillStyle.AMBER else PillStyle.SOFT).apply { text = s.label })
            addView(TextView(app).apply {
                text = if (s.theirs) "“${s.text}”" else s.text
                setTextColor(0xFFE3ECEE.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setLineSpacing(0f, 1.3f); maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }, full(8f, app))
    }

    /** Notes jotted on this call, each stamped amber with the time into the call. */
    private fun renderNotes() {
        val tv = notesTv ?: return
        if (jotted.isEmpty()) { tv.visibility = View.GONE; return }
        tv.visibility = View.VISIBLE
        tv.text = android.text.SpannableStringBuilder().apply {
            jotted.forEachIndexed { i, (off, text) ->
                if (i > 0) append("\n")
                if (off != null) {
                    val st = length
                    append("%d:%02d".format(off / 60, off % 60))
                    setSpan(android.text.style.TypefaceSpan(PillStyle.mono(tv.context)), st, length, 0)
                    setSpan(android.text.style.ForegroundColorSpan(PillStyle.AMBER), st, length, 0)
                    setSpan(android.text.style.RelativeSizeSpan(0.85f), st, length, 0)
                    append("  ")
                }
                append(text)
            }
        }
    }

    /** "Or add to an open loop": the three most recent, then all of them on request. Each row carries its loop's bullet. */
    private fun renderChoices(c: PillContent, all: Boolean = false) {
        val box = choicesBox ?: return
        box.removeAllViews()
        if (loopId != null || (c.choices.isEmpty() && c.archived == null)) { box.visibility = View.GONE; return }
        box.visibility = View.VISIBLE
        val app = box.context
        // The number was in an archived loop: name it (nothing more of it), and offer to bring it back.
        c.archived?.let { a ->
            val color = PillStyle.loop(app, a.id)
            box.addView(PillStyle.sign(TextView(app), 13f, PillStyle.SOFT).apply { text = "This number was in an archived loop" })
            box.addView(LinearLayout(app).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                PillStyle.hollow(TextView(app), color).background.let { background = it }
                minimumHeight = dp(app, 52f)
                setPadding(dp(app, 6f), dp(app, 6f), dp(app, 12f), dp(app, 6f))
                isClickable = true
                addView(BulletView(app, 22f).apply { bind(color, Initials.of(a.name), hollow = true) }, wrap())
                addView(LinearLayout(app).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(PillStyle.sign(TextView(app), 18f, PillStyle.INK, semi = true).apply { text = a.name; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    addView(TextView(app).apply { text = a.detail; setTextColor(PillStyle.SOFT); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); maxLines = 1 })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(app, 10f) })
                addView(PillStyle.sign(TextView(app), 14f, PillStyle.INK).apply { text = "Restore" })
                contentDescription = "Restore ${a.name} and add this call to it"
                setOnClickListener { restore(app.applicationContext, a) }
            }, full(8f, app))
        }
        if (c.choices.isEmpty()) return
        box.addView(PillStyle.sign(TextView(app), 13f, PillStyle.SOFT).apply { text = "Or add to an open loop" })
        val shown = if (all) c.choices else c.choices.take(3)
        for (ch in shown) {
            val color = PillStyle.loop(app, ch.id)
            box.addView(LinearLayout(app).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                PillStyle.hollow(TextView(app), color).background.let { background = it } // hollow, amber while pressed
                minimumHeight = dp(app, 52f)
                setPadding(dp(app, 6f), dp(app, 6f), dp(app, 12f), dp(app, 6f))
                isClickable = true
                addView(BulletView(app, 22f).apply { bind(color, Initials.of(ch.name), hollow = false) }, wrap())
                addView(LinearLayout(app).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(PillStyle.sign(TextView(app), 18f, PillStyle.INK, semi = true).apply { text = ch.name; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    if (ch.detail.isNotBlank()) addView(TextView(app).apply { text = ch.detail; setTextColor(PillStyle.SOFT); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(app, 10f) })
                contentDescription = "Add this number to ${ch.name}"
                setOnClickListener { associate(app.applicationContext, ch) }
            }, full(8f, app))
        }
        if (!all && c.choices.size > 3) box.addView(PillStyle.sign(TextView(app), 14f, PillStyle.INK).apply {
            text = "All open loops (${c.choices.size})"; PillStyle.hollow(this, PillStyle.RULE)
            setOnClickListener { poke(); renderChoices(c, all = true) }
        }, full(8f, app))
    }

    /** The unknown number joins an existing loop. This call and anything jotted from now on pool into it. */
    private fun associate(app: Context, ch: LoopChoice) {
        val c = content ?: return
        poke()
        loopId = ch.id; associatedAt = ch.id
        val ended = afterCall
        content = c.copy(loopId = ch.id, title = ch.name)
        App.of(app).scope.launch {
            val repo = App.of(app).repo
            repo.addNumber(ch.id, c.number)
            CallStore.update(app) { it.copy(loopId = ch.id) }
            if (ended) CallStore.lastEnded?.takeIf { it.call.number == c.number }?.let { repo.addEvent(it.toEvent(ch.id)) }
            CallContext.content(app, c.number, ch.id)?.let { fresh -> main.post { if (loopId == ch.id) { content = fresh; render() } } }
        }
        addedTv?.text = "Added ${Fmt.last4(c.number)} to this loop"
        addedRow?.visibility = View.VISIBLE
        partyInput?.visibility = View.VISIBLE
        choicesBox?.visibility = View.GONE
        render()
        partyInput?.requestFocus()
    }

    /** The archived loop comes back, this call goes on it, and the card fills with its context. */
    private fun restore(app: Context, a: LoopChoice) {
        val c = content ?: return
        poke()
        loopId = a.id
        val ended = afterCall
        content = c.copy(loopId = a.id, title = a.name, archived = null, choices = emptyList())
        App.of(app).scope.launch {
            val repo = App.of(app).repo
            repo.restore(a.id)
            CallStore.update(app) { it.copy(loopId = a.id) }
            if (ended) CallStore.lastEnded?.takeIf { it.call.number == c.number }?.let { repo.addEvent(it.toEvent(a.id)) }
            CallContext.content(app, c.number, a.id)?.let { fresh -> main.post { if (loopId == a.id) { content = fresh; notice("Restored ${a.name}."); render() } } }
        }
        choicesBox?.visibility = View.GONE
        render()
    }

    private fun undoAssociate(app: Context) {
        val id = associatedAt ?: return
        val c = content ?: return
        val startedAt = CallStore.lastEnded?.takeIf { afterCall && it.call.number == c.number }?.call?.startedAt
        App.of(app).scope.launch {
            App.of(app).repo.removeNumber(id, c.number, startedAt)
            CallStore.update(app) { it.copy(loopId = null) }
            CallContext.content(app, c.number, null)?.let { fresh ->
                main.post {
                    loopId = null; associatedAt = null
                    addedRow?.visibility = View.GONE; partyInput?.visibility = View.GONE; partyInput?.setText("")
                    apply(fresh)
                    input?.requestFocus()
                }
            }
        }
    }

    private fun saveParty(app: Context) {
        val id = loopId?.takeIf { it > 0 } ?: return
        val label = partyInput?.text?.toString()?.trim().orEmpty()
        val c = content ?: return
        if (label.isEmpty()) return
        App.of(app).scope.launch { App.of(app).repo.setNumberLabel(id, c.number, label) }
        content = c.copy(party = label, title = "${c.title.substringBefore(" · ")} · $label")
        render()
    }

    private fun toggle(app: Context) {
        val w = wm ?: return; val r = root ?: return; val lp = params ?: return; val et = input ?: return
        val imm = app.getSystemService(InputMethodManager::class.java)
        if (expanded && afterCall) { finish(); return }
        // Closing the card keeps whatever was typed: Done must never throw a note away.
        if (expanded && et.text.isNotBlank()) submit(app)
        expanded = !expanded
        card?.visibility = if (expanded) View.VISIBLE else View.GONE
        // Open, the card's header does the board's job.
        board?.visibility = View.VISIBLE
        if (expanded) {
            // Become focusable so the keyboard can attach, and move up so the keyboard can't cover the field.
            collapsedY = lp.y
            lp.y = minOf(lp.y, (app.resources.displayMetrics.heightPixels * 0.10).toInt())
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            w.updateViewLayout(r, lp)
            et.requestFocus()
            et.postDelayed({ imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT) }, 150)
            main.removeCallbacks(tick); main.post(tick)
        } else {
            main.removeCallbacks(tick)
            imm.hideSoftInputFromWindow(et.windowToken, 0)
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            lp.y = collapsedY
            w.updateViewLayout(r, lp)
        }
    }

    fun expand(ctx: Context) = main.post { if (root != null && !expanded) toggle(ctx.applicationContext) }

    /** Enter in the jot field. Unknown caller: the first line is their name and starts a loop. */
    private fun submit(app: Context) {
        val et = input ?: return
        val text = et.text.toString().trim()
        Log.d(TAG, "submit len=${text.length} loopId=$loopId afterCall=$afterCall")
        if (text.isEmpty()) return
        et.setText("")
        val c = content ?: return
        val repo = App.of(app).repo
        val id = loopId
        if (id == null) {
            loopId = -1L // creating; further jots wait for the id
            content = c.copy(loopId = -1L, title = text, choices = emptyList())
            choicesBox?.visibility = View.GONE
            render()
            App.of(app).scope.launch {
                val newId = repo.create(text, "", listOf(NumberInput(c.number)))
                CallStore.update(app) { it.copy(loopId = newId) }
                // If the call already ended, log it to the new loop too.
                CallStore.lastEnded?.takeIf { it.call.number == c.number }?.let { repo.addEvent(it.toEvent(newId)) }
                main.post {
                    loopId = newId
                    content = content?.copy(loopId = newId, status = "Started", detail = "")
                    input?.hint = "Add a note (optional)"
                    notice("Started a loop. Add more, or tap Done.")
                    render()
                }
            }
            return
        }
        if (id < 0) return
        val offset = if (!afterCall && callStart > 0) ((SystemClock.elapsedRealtime() - callStart) / 1000).toInt() else null
        App.of(app).scope.launch { repo.addEvent(Event(loopId = id, kind = EventKind.NOTE, at = System.currentTimeMillis(), text = text, callOffsetSec = offset)) }
        jotted += offset to text
        render()
    }

    private fun notice(text: String?) {
        noticeTv?.text = text.orEmpty()
        noticeTv?.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun closeLoop(app: Context) {
        val id = loopId?.takeIf { it > 0 } ?: return
        if (!closeArmed) { closeArmed = true; closeBtn?.text = "Tap again to close"; poke(); return }
        App.of(app).scope.launch { App.of(app).repo.close(id) }
        removeNow()
    }

    /**
     * The phone reports the call answered. Starts the clock for note stamps. [late]: we only started watching
     * mid-call (opened from the lock-screen notification), so we don't know when it was answered; no stamps.
     */
    fun markInCall(late: Boolean = false) = main.post {
        if (callStart == 0L) callStart = if (late) -1L else SystemClock.elapsedRealtime()
        render()
        if (expanded) { main.removeCallbacks(tick); main.post(tick) }
    }

    /** Call state went idle. POST_CALL normally follows within milliseconds; if it never does, don't leave the pill behind. */
    fun callIdle() = main.post { main.removeCallbacks(safety); main.postDelayed(safety, 3_000) }

    /**
     * The call ended (system POST_CALL). Unlocked and worth offering: the pill becomes a 5-second second
     * chance that dies unless touched. Locked, or nothing to offer: everything goes away silently.
     */
    fun afterCall(ctx: Context, c: PillContent?, locked: Boolean) = main.post {
        val app = ctx.applicationContext
        main.removeCallbacks(safety); main.removeCallbacks(tick)
        if (locked || c == null) { removeNow(); return@post }
        afterCall = true
        // Keep what this pill already knows (a loop just started or joined from it) over the fresh lookup.
        val merged = if (root != null && loopId != null && c.loopId == null) content ?: c else c
        if (root == null) { showOnMain(app, merged, null); afterCall = true; render() } else apply(merged)
        if (expanded) {
            // Mid-jot when they hung up: keep it open briefly to finish, and say nothing will be lost.
            touched = true; idleMs = HANGUP_IDLE_MS; poke()
            notice("Closes shortly. Your note is kept.")
            return@post
        }
        idleMs = IDLE_MS
        touched = false
        main.removeCallbacks(die); main.postDelayed(die, SECOND_CHANCE_MS)
    }

    private fun poke() {
        if (!afterCall || !touched) return
        main.removeCallbacks(idle); main.postDelayed(idle, idleMs)
    }

    /**
     * Closing a card the user opened after the call. Unknown caller never named: save a dated placeholder
     * loop so the next call from this number is recognised. Ignored pills never get here.
     */
    private fun finish() {
        val r = root ?: return
        val app = r.context.applicationContext
        val c = content
        val pending = input?.text?.toString()?.trim().orEmpty()
        if (partyInput?.visibility == View.VISIBLE && !partyInput?.text.isNullOrBlank()) saveParty(app)
        if (afterCall && touched && loopId == null && c != null) {
            val name = pending.ifEmpty { "Unnamed caller " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date()) }
            App.of(app).scope.launch {
                val id = App.of(app).repo.create(name, "", listOf(NumberInput(c.number)))
                CallStore.lastEnded?.takeIf { it.call.number == c.number }?.let { App.of(app).repo.addEvent(it.toEvent(id)) }
            }
        } else if (pending.isNotEmpty() && (loopId ?: -1) > 0) {
            // Half-typed note: keep it rather than lose it.
            val id = loopId!!
            App.of(app).scope.launch { App.of(app).repo.addEvent(Event(loopId = id, kind = EventKind.NOTE, at = System.currentTimeMillis(), text = pending)) }
        }
        removeNow()
    }

    /** Lineside itself was opened: the after-call card has done its job (anything half-typed is kept). */
    fun finishIfAfterCall() = main.post { if (afterCall) finish() }

    private fun fadeOut() {
        val r = root ?: return
        if (touched) return
        r.animate().alpha(0f).setDuration(400).withEndAction { removeNow() }.start()
    }

    fun removeNow() {
        main.removeCallbacks(die); main.removeCallbacks(safety); main.removeCallbacks(idle); main.removeCallbacks(tick)
        val r = root ?: return
        input?.let { r.context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) }
        runCatching { wm?.removeView(r) }
        root = null; card = null; board = null; bullet = null; mini = null; signTv = null; detailTv = null
        cardBullet = null; titleTv = null; clockTv = null; topicTv = null; cardLine = null; saidBox = null
        input = null; notesTv = null; noticeTv = null; closeBtn = null; doneBtn = null
        choicesBox = null; addedRow = null; addedTv = null; partyInput = null; associatedAt = null; idleMs = IDLE_MS
        afterCall = false; touched = false; expanded = false; closeArmed = false; content = null; loopId = null; callStart = 0L
        jotted.clear()
    }
}
