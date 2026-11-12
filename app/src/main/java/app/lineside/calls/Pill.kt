// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.calls

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.lineside.App
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.NumberInput
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
)

/**
 * The call pill: a small draggable overlay that expands into a card with context and a one-line jot field.
 * Plain Views on purpose: an overlay window is simpler without Compose's lifecycle plumbing.
 *
 * Rules (from the spike and the user's design decisions):
 * - only ever shown while unlocked, for a call still in progress or just ended;
 * - after the call it lives 5 s unless touched; a touched card closes after 1 min idle;
 * - never takes focus or raises the keyboard unless the user taps it.
 */
@SuppressLint("StaticFieldLeak")
object Pill {
    private const val TAG = "Lineside"
    const val SECOND_CHANCE_MS = 5_000L
    const val IDLE_MS = 60_000L

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var pillTv: TextView? = null
    private var card: LinearLayout? = null
    private var titleTv: TextView? = null
    private var contextTv: TextView? = null
    private var input: EditText? = null
    private var notesTv: TextView? = null
    private var closeBtn: Button? = null

    private var content: PillContent? = null
    private var loopId: Long? = null
    private var expanded = false
    private var afterCall = false
    private var touched = false
    private var closeArmed = false
    private var callStart = 0L
    private var collapsedY = 0
    private val jotted = mutableListOf<String>()

    private val die = Runnable { fadeOut() }
    private val idle = Runnable { Log.d(TAG, "pill card idle 60s"); finish() }
    private val safety = Runnable { if (!afterCall) { Log.d(TAG, "no POST_CALL within 3s; removing pill"); removeNow() } }

    fun canShow(ctx: Context) = SysSettings.canDrawOverlays(ctx)

    /** Shows the pill. Synchronous when called on the main thread, so it's on screen before the phone rings. */
    fun show(ctx: Context, c: PillContent, onVisible: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        if (!canShow(app)) return
        if (Looper.myLooper() == Looper.getMainLooper()) showOnMain(app, c, onVisible) else main.post { showOnMain(app, c, onVisible) }
    }

    private fun dp(ctx: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    /** Frosted dark surface with the thin teal outline from the design. */
    private fun frosted(ctx: Context, radiusDp: Float, alpha: Int) = GradientDrawable().apply {
        cornerRadius = dp(ctx, radiusDp).toFloat()
        setColor((alpha shl 24) or 0x1E2A32)
        setStroke(dp(ctx, 1.5f), 0xFF7FD6D6.toInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOnMain(app: Context, c: PillContent, onVisible: (() -> Unit)?) {
        if (root != null) { apply(c); onVisible?.invoke(); return }
        jotted.clear(); callStart = 0L; expanded = false; afterCall = false; touched = false; closeArmed = false
        val w = app.getSystemService(WindowManager::class.java); wm = w
        val dm = app.resources.displayMetrics

        pillTv = TextView(app).apply {
            setTextColor(0xFFFFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(app, 38f); gravity = Gravity.CENTER_VERTICAL; maxLines = 1
            setPadding(dp(app, 16f), dp(app, 8f), dp(app, 16f), dp(app, 8f))
            background = frosted(app, 19f, 0xE6)
        }
        titleTv = TextView(app).apply { setTextColor(0xFFFFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) }
        contextTv = TextView(app).apply {
            setTextColor(0xFFDFF3F3.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); maxLines = 4
            setPadding(0, dp(app, 4f), 0, dp(app, 4f))
        }
        input = EditText(app).apply {
            setHintTextColor(0xFF8C98A3.toInt()); setTextColor(0xFFFFFFFF.toInt()); isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, _, _ -> submit(app); true }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) = poke()
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        notesTv = TextView(app).apply { setTextColor(0xFFC3CCD3.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
        val done = Button(app).apply { text = "Done"; setOnClickListener { toggle(app) } }
        closeBtn = Button(app).apply { text = "Close loop"; visibility = View.GONE; setOnClickListener { closeLoop(app) } }
        val buttons = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            addView(closeBtn); addView(done)
        }
        card = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            background = frosted(app, 20f, 0xF2)
            setPadding(dp(app, 14f), dp(app, 12f), dp(app, 14f), dp(app, 8f))
            addView(titleTv); addView(contextTv); addView(input); addView(notesTv); addView(buttons)
        }
        val r = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            addView(pillTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(card, LinearLayout.LayoutParams((dm.widthPixels * 0.84).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(app, 6f) })
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
            x = saved?.first ?: (dm.widthPixels * 0.08).toInt()
            // 27%: the band Google Phone and the GrapheneOS dialer both leave empty on their call screens.
            y = saved?.second ?: (dm.heightPixels * 0.27).toInt()
        }
        params = lp

        val slop = ViewConfiguration.get(app).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        pillTv!!.setOnTouchListener { _, e ->
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
                if (onVisible != null) {
                    var fired = false
                    r.viewTreeObserver.addOnDrawListener { if (!fired) { fired = true; r.post(onVisible) } }
                }
            }
            .onFailure { Log.w(TAG, "pill addView failed", it); root = null }
    }

    private fun apply(c: PillContent) {
        content = c; loopId = c.loopId
        pillTv?.text = c.label
        pillTv?.contentDescription = "Lineside: ${c.label}"
        titleTv?.text = c.title
        contextTv?.text = c.context.joinToString("\n")
        contextTv?.visibility = if (c.context.isEmpty()) View.GONE else View.VISIBLE
        input?.hint = if (loopId == null) "Who is this? Enter starts a loop" else "Jot a note, Enter saves it"
    }

    private fun toggle(app: Context) {
        val w = wm ?: return; val r = root ?: return; val lp = params ?: return; val et = input ?: return
        val imm = app.getSystemService(InputMethodManager::class.java)
        if (expanded && afterCall) { finish(); return }
        expanded = !expanded
        card?.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded) {
            // Become focusable so the keyboard can attach, and move up so the keyboard can't cover the field.
            collapsedY = lp.y
            lp.y = minOf(lp.y, (app.resources.displayMetrics.heightPixels * 0.14).toInt())
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            w.updateViewLayout(r, lp)
            et.requestFocus()
            et.postDelayed({ imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT) }, 150)
        } else {
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
        if (text.isEmpty()) return
        et.setText("")
        val c = content ?: return
        val repo = App.of(app).repo
        val id = loopId
        if (id == null) {
            loopId = -1L // creating; further jots wait for the id
            App.of(app).scope.launch {
                val newId = repo.create(text, "", listOf(NumberInput(c.number)))
                CallStore.update(app) { it.copy(loopId = newId) }
                // If the call already ended, log it to the new loop too.
                CallStore.lastEnded?.takeIf { it.call.number == c.number }?.let { repo.addEvent(it.toEvent(newId)) }
                loopId = newId
                titleTv?.text = text
                pillTv?.text = "$text · Done when finished"
                input?.hint = "Add a note (optional)"
                notesTv?.text = "Started a loop. Add more, or tap Done."
                closeBtn?.visibility = View.GONE
            }
            return
        }
        if (id < 0) return
        val offset = if (!afterCall && callStart > 0) ((SystemClock.elapsedRealtime() - callStart) / 1000).toInt() else null
        App.of(app).scope.launch { repo.addEvent(Event(loopId = id, kind = EventKind.NOTE, at = System.currentTimeMillis(), text = text, callOffsetSec = offset)) }
        jotted += (offset?.let { "%02d:%02d ".format(it / 60, it % 60) } ?: "") + text
        notesTv?.text = jotted.joinToString("\n")
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
        val c = content ?: return@post
        if (!afterCall && !c.label.endsWith("tap to jot")) pillTv?.text = "${c.label} · tap to jot"
    }

    /** Call state went idle. POST_CALL normally follows within milliseconds; if it never does, don't leave the pill behind. */
    fun callIdle() = main.post { main.removeCallbacks(safety); main.postDelayed(safety, 3_000) }

    /**
     * The call ended (system POST_CALL). Unlocked and worth offering: the pill becomes a 5-second second
     * chance that dies unless touched. Locked, or nothing to offer: everything goes away silently.
     */
    fun afterCall(ctx: Context, c: PillContent?, locked: Boolean) = main.post {
        val app = ctx.applicationContext
        main.removeCallbacks(safety)
        if (locked || c == null) { removeNow(); return@post }
        val label = if (c.loopId == null) "+ Who was this?" else "+ Add a note"
        if (root == null) showOnMain(app, c.copy(label = label), null) else apply(c.copy(label = label))
        afterCall = true
        closeBtn?.visibility = if (loopId != null && loopId!! > 0) View.VISIBLE else View.GONE
        if (expanded) { touched = true; poke(); return@post } // mid-jot when they hung up: keep it open
        touched = false
        main.removeCallbacks(die); main.postDelayed(die, SECOND_CHANCE_MS)
    }

    private fun poke() {
        if (!afterCall || !touched) return
        main.removeCallbacks(idle); main.postDelayed(idle, IDLE_MS)
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
        main.removeCallbacks(die); main.removeCallbacks(safety); main.removeCallbacks(idle)
        val r = root ?: return
        input?.let { r.context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) }
        runCatching { wm?.removeView(r) }
        root = null; card = null; pillTv = null; input = null; notesTv = null; titleTv = null; contextTv = null; closeBtn = null
        afterCall = false; touched = false; expanded = false; closeArmed = false; content = null; loopId = null
    }
}
