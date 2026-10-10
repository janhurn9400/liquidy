package io.github.janhurn9400.liquidy.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.tuning.Tunable
import io.github.janhurn9400.liquidy.tuning.Tunables
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

object DebugState {
    var visible by mutableStateOf(false)
    fun toggle() { visible = !visible }
}

private const val THREE_FINGER_HOLD_MS = 600L // Debug-Werkzeug, kein iOS-Verhalten

/** Langes Tippen mit 3 Fingern öffnet/schließt das Panel (im Emulator: Taste D oder adb, siehe CLAUDE.md). */
fun Modifier.threeFingerLongPress(onTrigger: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val down = e.changes.count { it.pressed }
            if (down == 0) return@awaitEachGesture
            if (down >= 3) {
                val start = e.changes.filter { it.pressed }.map { it.position }
                val held = withTimeoutOrNull(THREE_FINGER_HOLD_MS) {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.size < 3) return@withTimeoutOrNull false
                        val moved = pressed.zip(start).any { (c, s) -> abs(c.position.x - s.x) + abs(c.position.y - s.y) > viewConfiguration.touchSlop }
                        if (moved) return@withTimeoutOrNull false
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                if (held == null) onTrigger()
                return@awaitEachGesture
            }
        }
    }
}

@Composable
fun DebugPanel() {
    val context = LocalContext.current
    val groups = remember { Tunables.all.map { it.group }.distinct() }
    var group by remember { mutableStateOf(groups.first()) }
    var collapsed by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(if (collapsed) Modifier else Modifier.fillMaxHeight(0.5f))
                    .background(Color(0xF01C1C1E), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tuning", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val text = Tunables.export()
                        Log.i("Tuning", "\n$text")
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Tuning", text))
                        Toast.makeText(context, "Export in Zwischenablage + Logcat (Tag „Tuning“)", Toast.LENGTH_SHORT).show()
                    }) { Text("Export") }
                    TextButton(onClick = { Tunables.resetAll(context); revision++ }) { Text("Reset") }
                    TextButton(onClick = { collapsed = !collapsed }) { Text(if (collapsed) "▲" else "▼") }
                    TextButton(onClick = { Tunables.save(context); DebugState.visible = false }) { Text("✕") }
                }
                if (!collapsed) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groups.forEach { g ->
                            FilterChip(selected = g == group, onClick = { group = g }, label = { Text(g) })
                        }
                    }
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(Tunables.all.filter { it.group == group }, key = { it.id + revision }) { t ->
                            TunableRow(t) { Tunables.save(context) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TunableRow(t: Tunable, onCommit: () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                String.format(Locale.US, "%.3f %s", t.value, t.unit),
                color = if (t.isChanged) Color(0xFFFFD60A) else Color(0xFFAEAEB2),
                fontSize = 13.sp,
            )
            if (t.isChanged) {
                TextButton(onClick = { t.value = t.default; onCommit() }) { Text("↺") }
            } else Spacer(Modifier.padding(start = 4.dp))
        }
        Text(t.id, color = Color(0xFF8E8E93), fontSize = 10.sp)
        Slider(
            value = t.value,
            onValueChange = { t.value = it },
            onValueChangeFinished = onCommit,
            valueRange = t.min..t.max,
        )
    }
}
