// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.Access
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.Loop
import app.lineside.data.LoopFull
import app.lineside.data.LoopNumber

/** What onboarding shows and changes. Every choice here is the same setting as in Settings. */
class IntroState(
    val granted: Map<Access, Boolean>,
    val onAllow: (Access) -> Unit,
    val lockOn: Boolean, val lockAvailable: Boolean, val onLock: (Boolean) -> Unit,
    val discreet: Boolean, val onDiscreet: (Boolean) -> Unit,
    val backupOn: Boolean, val onBackup: (Boolean) -> Unit,
    val nudgeOn: Boolean, val onNudge: (Boolean) -> Unit,
    /** The foss build, which can add texts from a loop's numbers. */
    val readsTexts: Boolean,
    /** [startLoop]: go straight to a new loop. */
    val onFinish: (startLoop: Boolean) -> Unit,
)

private enum class Page(val sign: String, val title: String) {
    WELCOME("Welcome", "Lineside"),
    TRY("Try it", "A loop, start to finish"),
    IN("What goes in", "Only what you put there"),
    ALLOW("Permissions", "All optional"),
    LOCK("Privacy", "Your loops, behind your lock"),
    BACKUP("Backup", "Where copies go"),
    REMIND("Reminders", "Only if you want them"),
    ALSO("Also by Absolute Candor", "GreenTime"),
    DONE("Ready", "That's everything"),
}

@Composable
fun Intro(s: IntroState) {
    val c = Line.c
    val pages = Page.entries
    var at by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = at > 0) { at-- }
    val page = pages[at]
    Column(Modifier.fillMaxSize().background(c.ground)) {
        // The signage band: the route so far, where you are, and this stop's name.
        Column(Modifier.fillMaxWidth().background(c.header).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
            Progress(pages.size, at)
            Sign(page.sign, 14.sp, c.amber, Modifier.padding(top = 16.dp))
            Text(page.title, Modifier.semantics { heading() }, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = c.headerInk)
        }
        AnimatedContent(page, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (p) {
                    Page.WELCOME -> Welcome()
                    Page.TRY -> TryIt()
                    Page.IN -> WhatGoesIn(s.readsTexts)
                    Page.ALLOW -> Allow(s)
                    Page.LOCK -> Lock(s)
                    Page.BACKUP -> Backup(s)
                    Page.REMIND -> Remind(s)
                    Page.ALSO -> Also()
                    Page.DONE -> Done()
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page == Page.DONE) {
                LineButton("Go to my loops", { s.onFinish(false) }, Modifier.weight(1f), edge = c.muted)
                LineButton("Start a loop", { s.onFinish(true) }, Modifier.weight(1f))
            } else {
                if (at > 0) LineButton("Back", { at-- }, Modifier.weight(1f), edge = c.muted)
                LineButton(if (at == 0) "Show me" else "Next", { at++ }, Modifier.weight(1f))
            }
        }
    }
}

/** The route: a stop per page, the ones behind you filled, this one amber. */
@Composable
private fun Progress(count: Int, at: Int) {
    val c = Line.c
    Canvas(Modifier.fillMaxWidth().height(14.dp).semantics { contentDescription = "Step ${at + 1} of $count" }) {
        val r = 5.dp.toPx(); val w = 3.dp.toPx()
        val step = (size.width - 2 * r) / (count - 1)
        val y = size.height / 2
        drawLine(c.headerMuted, Offset(r, y), Offset(size.width - r, y), w)
        drawLine(c.headerInk, Offset(r, y), Offset(r + step * at, y), w)
        for (i in 0 until count) {
            val o = Offset(r + step * i, y)
            when {
                i == at -> drawCircle(c.amber, r + 1.dp.toPx(), o)
                i < at -> drawCircle(c.headerInk, r, o)
                else -> { drawCircle(c.header, r, o); drawCircle(c.headerMuted, r - w / 2, o, style = Stroke(w)) }
            }
        }
    }
}

@Composable private fun Body(text: String) = Text(text, fontSize = 16.sp, lineHeight = 23.sp, color = Line.c.body)
@Composable private fun Small(text: String) = Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = Line.c.muted)

/** A point with a bold lead, the house style for "what Lineside never does". */
@Composable
private fun Point(lead: String, rest: String) {
    val c = Line.c
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 7.dp).size(9.dp).border(2.dp, c.ink, RoundedCornerShape(50)))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lead, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c.ink)
            if (rest.isNotEmpty()) Text(rest, fontSize = 14.sp, lineHeight = 20.sp, color = c.body)
        }
    }
}

@Composable
private fun Plate(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().background(Line.c.surface, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

// ---- Pages ----

@Composable
private fun Welcome() {
    Body(
        "Lineside keeps track of the calls you're waiting on: the clinic, the lab, the insurance claim, the contractor. " +
            "Each one is a loop. A loop holds that matter's calls, voicemails, texts and your notes on one line, and tells you whose move it is.",
    )
    Body("When it's done, you close the loop and it's gone.")
    Point("Everything stays on this phone", "Lineside has no internet access at all. It can't send anything anywhere.")
    Point("No account, no login", "Nothing to sign up for. No analytics, no crash reports.")
    Point("Never reads your call log", "It can notice a call as it happens, if you let it. Your call history stays closed to it.")
    Point("Never records a call", "No audio is kept, and nothing is transcribed.")
}

@Composable
private fun TryIt() {
    val c = Line.c
    val now = remember { System.currentTimeMillis() }
    val h = 60 * 60_000L
    // A practice loop that lives only on this screen: nothing is saved.
    val loop = remember { Loop(id = 4, name = "Dr Lee's office", topic = "Practice: test results", createdAt = now - 50 * h, lastActivityAt = now - 48 * h) }
    val numbers = remember { listOf(LoopNumber(loopId = 4, number = "5550100", key = "", label = "Front desk")) }
    val start = remember {
        listOf(
            Event(id = 1, loopId = 4, kind = EventKind.CALL_OUT, at = now - 48 * h, durationSec = 95, number = "5550100"),
            Event(id = 2, loopId = 4, kind = EventKind.NOTE, at = now - 48 * h + 120_000, text = "They'll call when the results are in."),
        )
    }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val events = when (step) {
        0 -> start
        1 -> start + listOf(
            Event(id = 3, loopId = 4, kind = EventKind.MISSED, at = now - 2 * h, number = "5550100"),
            Event(id = 4, loopId = 4, kind = EventKind.VOICEMAIL, at = now - 2 * h + 60_000, number = "5550100",
                text = "Hi, it's Dr Lee's office. Your results are in, please give us a call back."),
        )
        else -> start + listOf(
            Event(id = 3, loopId = 4, kind = EventKind.MISSED, at = now - 2 * h, number = "5550100"),
            Event(id = 4, loopId = 4, kind = EventKind.VOICEMAIL, at = now - 2 * h + 60_000, number = "5550100",
                text = "Hi, it's Dr Lee's office. Your results are in, please give us a call back."),
            Event(id = 5, loopId = 4, kind = EventKind.CALL_OUT, at = now - 5 * 60_000, durationSec = 240, number = "5550100"),
            Event(id = 6, loopId = 4, kind = EventKind.NOTE, at = now - 3 * 60_000, text = "All normal. Recheck in six months.", callOffsetSec = 150),
        )
    }
    Small("Practice on a made-up loop. Nothing here is saved.")
    // The real loop card, so what you learn here is what you'll see.
    AnimatedVisibility(step < 3, exit = fadeOut() + shrinkVertically()) {
        // The card brings its own list margins; the page already has them.
        Box(Modifier.layout { m, k ->
            val inset = 16.dp.roundToPx()
            val p = m.measure(k.copy(maxWidth = k.maxWidth + 2 * inset, minWidth = 0))
            layout(k.maxWidth, p.height - 12.dp.roundToPx()) { p.place(-inset, -12.dp.roundToPx()) }
        }) { LoopCard(LoopFull(loop, numbers, events), now, onClick = {}) }
    }
    val (say, act) = when (step) {
        0 -> "Two days ago you called the office and they said they'd call back. The lamp says Waiting: the ball is in their court." to
            "They call while you're busy"
        1 -> "They called, you missed it, and they left a voicemail. Now it says Call back, in amber: amber always means it's your move. " +
            "If you let Lineside notice calls, it logs this by itself." to "Call them back"
        2 -> "You called back and jotted down what they said. With the pill, you can write that note during the call, without leaving it. " +
            "Waiting again, since there's nothing left for you to do." to "Close the loop"
        else -> "Closed: the loop and everything in it are gone from this phone. If they might call again, Archive keeps it out of the way instead, " +
            "and Lineside offers to bring it back when they do." to "Try it again"
    }
    Body(say)
    LineButton(act, { step = if (step == 3) 0 else step + 1 }, Modifier.fillMaxWidth())
}

@Composable
private fun WhatGoesIn(readsTexts: Boolean) {
    Body("Words get into Lineside only when you put them there, at that moment. Allowing something once isn't taken as consent for later.")
    Point("Your notes", "Typed by you, on a loop or on the pill during a call.")
    Point("Calls from a loop's numbers", "If you let Lineside notice calls: the number and the time, nothing else.")
    Point("Voicemails and texts, by sharing", "Share one from your phone or messages app to Lineside. You see exactly what will be added, and to which loop, before anything is saved. A voicemail's text is kept; its audio never is.")
    if (readsTexts) Point(
        "Texts from a loop's numbers, if you switch them on",
        "Two switches, both off to start: one on the loop, one on each number. Only texts that arrive after you switch a number on are added, and every look " +
            "at your messages is written to a log you can read. Android's prompt will say \"send and view SMS messages\"; Lineside can't send texts, " +
            "that's just Android's name for the permission. RCS chats can only come in by sharing.",
    ) else Point("Texts stay in your messages app", "This version of Lineside can't read your messages at all. To add a text, or an RCS chat, share it to Lineside.")
    Plate {
        Text("Lineside isn't your medical record", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Line.c.ink)
        Text(
            "The originals stay with your provider and in your phone's own apps. Lineside holds the notes you chose to keep about them, and lets them go when you're done.",
            fontSize = 14.sp, lineHeight = 20.sp, color = Line.c.body,
        )
    }
}

@Composable
private fun Allow(s: IntroState) {
    Body("Say no to all of them and Lineside still works: you add calls and notes yourself. Each one can be changed later in Settings.")
    Group(0.dp) {
        Access.entries.forEachIndexed { i, a ->
            if (i > 0) Divider()
            AccessRow(a, s.granted[a] == true) { s.onAllow(a) }
        }
    }
    Small(
        "Android calls the first one being your \"caller ID & spam app\". Lineside never blocks a call or marks it as spam; " +
            "it only sees the number. It never asks for your call log, and never for the internet.",
    )
}

@Composable
private fun Lock(s: IntroState) {
    var confirmOff by remember { mutableStateOf(false) }
    Body("Lineside asks for your phone's own screen lock (fingerprint, face or PIN) when you open it after a while away. Nothing new to remember; Android does the checking.")
    Group(0.dp) {
        Toggle(
            "Lock Lineside",
            if (s.lockAvailable) "On. Covers Lineside and the share sheet. The pill during your own calls stays as it is."
            else "Your phone has no screen lock, so there's nothing for Lineside to lean on. Add one in your phone's settings and the app lock turns on.",
            s.lockOn && s.lockAvailable,
            // Turning it off is a decision, so it gets a second question.
            { on -> if (on) s.onLock(true) else confirmOff = true },
            enabled = s.lockAvailable,
        )
        Divider()
        Toggle(
            "Discreet call screen",
            "A ringing phone is readable by anyone nearby. Discreet shows only \"Call back\" or \"Waiting\", never a loop's topic or who within it is calling.",
            s.discreet, s.onDiscreet,
        )
    }
    if (confirmOff) LineSheet({ confirmOff = false }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Turn off the app lock?", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Line.c.ink)
            Body("Anyone holding your unlocked phone could open Lineside and read your loops: who you're waiting on, and what about.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("Turn it off", { confirmOff = false; s.onLock(false) }, Modifier.weight(1f), edge = Line.c.muted)
                LineButton("Keep it on", { confirmOff = false }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Choice(title: String, detail: String, chosen: Boolean, onChoose: () -> Unit) {
    val c = Line.c
    Row(
        Modifier.fillMaxWidth()
            .background(c.surface, RoundedCornerShape(12.dp))
            .border(if (chosen) 2.dp else 1.dp, if (chosen) c.amber else c.divider, RoundedCornerShape(12.dp))
            .selectable(chosen, role = Role.RadioButton, onClick = onChoose)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.padding(top = 4.dp).size(18.dp)) {
            drawCircle(if (chosen) c.amber else c.ink, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (chosen) drawCircle(c.amber, size.minDimension / 4)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c.ink)
            Text(detail, fontSize = 14.sp, lineHeight = 20.sp, color = c.body)
        }
    }
}

@Composable
private fun Backup(s: IntroState) {
    Body("Your phone may back up its apps to Google, or to Seedvault on GrapheneOS. Lineside stays out of that unless you choose otherwise.")
    Choice(
        "Only on this phone",
        "Android's backup gets nothing from Lineside. If this phone is lost or reset, your loops go with it, unless you've saved an export.",
        !s.backupOn,
    ) { s.onBackup(false) }
    Choice(
        "Also in my phone's encrypted backup",
        "Only into a backup encrypted with your screen lock, so the backup service can't read it, or straight to a new phone during setup. Never an unencrypted one.",
        s.backupOn,
    ) { s.onBackup(true) }
    Plate {
        Text("Encrypted export", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Line.c.ink)
        Text(
            "Any time, Settings → Backup and export saves one file with everything, archived loops and the text access log too, locked with a passphrase. " +
                "Open it on a computer or import it on another phone.",
            fontSize = 14.sp, lineHeight = 20.sp, color = Line.c.body,
        )
    }
}

@Composable
private fun Remind(s: IntroState) {
    Body("Some calls never come. Lineside can ask, once, when a loop has had no calls or notes for three weeks: still waiting, or done?")
    Group(0.dp) {
        Toggle(
            "Remind me about quiet loops",
            "One silent notification per quiet loop. Off: quiet loops just move to the bottom of your list.",
            s.nudgeOn,
            { on ->
                s.onNudge(on)
                if (on && s.granted[Access.NOTIFICATIONS] != true) s.onAllow(Access.NOTIFICATIONS)
            },
        )
    }
    Small("Lineside sends nothing else unless you ask for it.")
}

@Composable
private fun Also() {
    val c = Line.c
    Body("Lineside is made by Absolute Candor. The same rules hold for everything we make: no internet access, no account, nothing sent to us.")
    Plate {
        Text("GreenTime", fontFamily = Type.Signage, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.ink)
        Text(
            "A calendar that guards your free time. It times your tasks and drives, shows the open gaps in your week, and never fills them for you.",
            fontSize = 15.sp, lineHeight = 21.sp, color = c.body,
        )
        Text("Not out yet.", fontSize = 14.sp, color = c.muted)
    }
}

@Composable
private fun Done() {
    Body("Start a loop for whoever you're waiting on. Give it a name and their number; everything else can wait.")
    Small("Everything from this introduction is in Settings, and Settings → Introduction plays it again.")
}
