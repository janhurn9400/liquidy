package io.github.janhurn9400.liquidy.ui.welcome

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.design.IconAppearance
import io.github.janhurn9400.liquidy.design.IconStyle
import io.github.janhurn9400.liquidy.ui.Inter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.design.IconVariant
import io.github.janhurn9400.liquidy.ui.home.StyledIcon
import io.github.janhurn9400.liquidy.ui.home.WithWallpaperGlass
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Whether setup has been completed. */
object WelcomeStore {
    /** Welcome currently visible (light pages → dark status bar icons). */
    var active by mutableStateOf(false)
    private const val PREFS = "welcome"
    fun done(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("done", false)
    fun markDone(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("done", true).apply()
    /** Settings › Advanced › Run Setup Again: MainActivity shows the welcome again (nothing is reset — choices stay). */
    var restartRequested by mutableStateOf(false)
}

/** Root available? (Magisk app installed or su binary in the path) */
fun hasRoot(c: Context): Boolean =
    runCatching { c.packageManager.getPackageInfo("com.topjohnwu.magisk", 0); true }.getOrDefault(false) ||
        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su").any { java.io.File(it).exists() }

private enum class Step { Hello, DefaultHome, Permissions, GlassAlign, Appearance, Foldable, StatusBar, Root, Done }

/**
 * Welcome in the style of the iOS 26 setup (YouTube recording of an iPhone 17 analysed): "Hello", then light pages with
 * an icon at the top centre, left-aligned bold title + grey text, Liquid Glass button "Continue" at the bottom and a round
 * glass back button top left. Page change like an iOS push (new page from the right, old one ⅓ to the left).
 */
@Composable
fun WelcomeFlow(isDefaultHome: () -> Boolean, hasRemoteAnimation: Boolean, sample: AppEntry?, wallpaper: ImageBitmap, onFinish: () -> Unit) {
    val context = LocalContext.current
    val root = remember { hasRoot(context) }
    val steps = remember {
        buildList {
            add(Step.Hello)
            if (!isDefaultHome()) add(Step.DefaultHome)
            add(Step.Permissions)
            // ROMs whose wallpaper zoom we can't compute: offer to line the glass up by hand
            if (!io.github.janhurn9400.liquidy.glass.GlassAlign.cropModelKnown(context)) add(Step.GlassAlign)
            add(Step.Appearance)
            if (io.github.janhurn9400.liquidy.data.FoldableStyle.supportsDuo(context)) add(Step.Foldable) // Foldables + Tablets
            add(Step.StatusBar)
            if (root) add(Step.Root) // with module: "already active", otherwise instructions
            add(Step.Done)
        }
    }
    var i by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var fromHello by remember { mutableStateOf(false) }
    fun next() { forward = true; fromHello = i == 0; if (i < steps.lastIndex) i++ else onFinish() }
    fun back() { if (i > 1) { forward = false; i-- } }
    androidx.activity.compose.BackHandler(enabled = i > 1) { back() }

    // Status bar dark on the light pages (MainActivity reads WelcomeStore.active)
    androidx.compose.runtime.DisposableEffect(Unit) { WelcomeStore.active = true; onDispose { WelcomeStore.active = false } }

    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { next() }
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // Page background (source for the buttons' glass)
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).drawBehind { drawPageBackground() })
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            AnimatedContent(
                targetState = i,
                transitionSpec = {
                    if (initialState == 0) // from Hello like the home screen after the notification centre: zooms in from 2.6×, fades in over 0.1 s
                        (scaleIn(spring(1f, ZOOM_STIFFNESS), initialScale = ZOOM_FROM) + fadeIn(tween(ZOOM_FADE_MS))) togetherWith fadeOut(tween(ZOOM_FADE_MS))
                    else if (forward)
                        slideInHorizontally(PUSH_SPRING) { it } togetherWith
                            (slideOutHorizontally(PUSH_SPRING) { -it / 3 } + fadeOut(tween(PUSH_MS)))
                    else
                        (slideInHorizontally(PUSH_SPRING) { -it / 3 } + fadeIn(tween(PUSH_MS))) togetherWith
                            slideOutHorizontally(PUSH_SPRING) { it }
                },
                label = "welcome",
            ) { idx ->
                val showBack = idx > 1
                // first page after Hello: blurred at first when zooming in, then sharp (iOS ≈ 0.35 s)
                val unsharp by transition.animateFloat(transitionSpec = { tween(ZOOM_SHARPEN_MS) }, label = "sharpen") {
                    if (it == androidx.compose.animation.EnterExitState.Visible) 0f else 1f
                }
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val b = if (idx == 1 && fromHello) unsharp * ZOOM_BLUR_DP * density else 0f
                    renderEffect = if (b > 0.5f) androidx.compose.ui.graphics.BlurEffect(b, b) else null
                }) {
                when (steps[idx]) {
                    Step.Hello -> HelloScreen { next() }
                    Step.DefaultHome -> StepPage(
                        glyph = Glyph.Home, showBack = showBack, onBack = ::back,
                        title = stringResource(R.string.welcome_home_title),
                        body = stringResource(R.string.welcome_home_body),
                        primary = stringResource(R.string.welcome_home_button),
                        onPrimary = {
                            if (isDefaultHome()) next()
                            else {
                                val rm = context.getSystemService(RoleManager::class.java)
                                runCatching { homeRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.onFailure { next() }
                            }
                        },
                        secondary = stringResource(R.string.welcome_later), onSecondary = { next() },
                    )
                    Step.Permissions -> StepPage(
                        glyph = Glyph.Lock, showBack = showBack, onBack = ::back,
                        title = stringResource(R.string.welcome_perm_title),
                        body = stringResource(R.string.welcome_perm_body),
                        primary = stringResource(R.string.welcome_continue), onPrimary = { next() },
                    ) { PermissionCard() }
                    Step.Appearance -> StepPage(
                        glyph = Glyph.Appearance, showBack = showBack, onBack = ::back,
                        title = stringResource(R.string.welcome_look_title),
                        body = stringResource(R.string.welcome_look_body),
                        primary = stringResource(R.string.welcome_continue), onPrimary = { next() },
                    ) { AppearanceChooser(sample, wallpaper) }
                    Step.Foldable -> StepPage(
                        glyph = Glyph.Fold, showBack = showBack, onBack = ::back,
                        title = stringResource(R.string.welcome_fold_title),
                        body = stringResource(if (io.github.janhurn9400.liquidy.data.FoldableStyle.isTablet(context)) R.string.welcome_fold_body_tablet else R.string.welcome_fold_body),
                        primary = stringResource(R.string.welcome_continue), onPrimary = { next() },
                    ) { FoldStyleChooser() }
                    Step.StatusBar -> StepPage(
                        glyph = Glyph.Appearance, showBack = showBack, onBack = ::back,
                        title = stringResource(R.string.welcome_status_title),
                        body = stringResource(R.string.welcome_status_body),
                        primary = stringResource(R.string.welcome_continue), onPrimary = { next() },
                    ) {
                        Card {
                            CardRow(stringResource(R.string.settings_statusbar), Color(0xFF007AFF), {
                                val w = size.width; val sw = w * 0.07f
                                drawRoundRect(Color.White, Offset(w * 0.2f, w * 0.3f), Size(w * 0.6f, w * 0.4f), CornerRadius(w * 0.08f), style = Stroke(sw))
                                drawLine(Color.White, Offset(w * 0.3f, w * 0.42f), Offset(w * 0.45f, w * 0.42f), sw, StrokeCap.Round)
                            }) {
                                GlassToggle(io.github.janhurn9400.liquidy.data.OwnStatusBar.enabled, { io.github.janhurn9400.liquidy.data.OwnStatusBar.set(context, it) })
                            }
                        }
                    }
                    Step.GlassAlign -> {
                        val live = io.github.janhurn9400.liquidy.glass.SystemWallpaper.isLive
                        val possible = io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead()
                        // after "Done" on the alignment screen → next page
                        var started by remember { mutableStateOf(false) }
                        androidx.compose.runtime.LaunchedEffect(io.github.janhurn9400.liquidy.glass.GlassAlign.open) {
                            if (io.github.janhurn9400.liquidy.glass.GlassAlign.open) started = true else if (started) { started = false; next() }
                        }
                        StepPage(
                            glyph = Glyph.Appearance, showBack = showBack, onBack = ::back,
                            title = stringResource(R.string.welcome_align_title),
                            body = stringResource(when { possible && live -> R.string.welcome_align_live; possible -> R.string.welcome_align_body
                                else -> R.string.welcome_align_unavailable }),
                            primary = stringResource(if (possible) R.string.welcome_align_start else R.string.welcome_continue),
                            onPrimary = { if (possible) io.github.janhurn9400.liquidy.glass.GlassAlign.open = true else next() },
                            secondary = if (possible) stringResource(R.string.welcome_later) else null, onSecondary = { next() },
                        )
                    }
                    Step.Root -> StepPage(
                        glyph = Glyph.Sparkle, showBack = showBack, onBack = ::back,
                        title = stringResource(R.string.welcome_root_title),
                        body = stringResource(R.string.welcome_root_body),
                        primary = stringResource(if (hasRemoteAnimation) R.string.welcome_continue else R.string.welcome_root_open_magisk),
                        onPrimary = {
                            if (hasRemoteAnimation) next()
                            else context.packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk")?.let { runCatching { context.startActivity(it) } }
                        },
                        extra = if (!hasRemoteAnimation && MODULE_URL.isNotEmpty()) stringResource(R.string.welcome_root_download) to {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MODULE_URL))) }
                        } else null,
                        secondary = if (hasRemoteAnimation) null else stringResource(R.string.welcome_later), onSecondary = { next() },
                    ) { RootSteps(active = hasRemoteAnimation) }
                    Step.Done -> StepPage(
                        glyph = Glyph.Check, showBack = showBack, onBack = ::back, centered = true,
                        title = stringResource(R.string.welcome_done_title),
                        body = stringResource(R.string.welcome_done_body),
                        primary = stringResource(R.string.welcome_get_started), onPrimary = { next() },
                    )
                }
                }
            }
        }
    }
}

/**
 * Download of the Magisk module (GitHub release, downloads the ZIP directly). Requires the repository to be public
 * (it is). The link in step 1 and the "Download Module" button use it.
 */
/** Fixed download link to the Animation Module: always points to the latest release (the file name stays the same). */
internal const val MODULE_URL = "https://github.com/janhurn9400/liquidy/releases/latest/download/liquidy-magisk.zip"

private enum class Glyph { Home, Lock, Appearance, Fold, Sparkle, Check }

/** Setup background: pure white like iOS (systemBackground light). */
private fun DrawScope.drawPageBackground() = drawRect(Color.White)

/**
 * A setup page like iOS 26: back glass button, icon centred, title + text left-aligned, optional content,
 * glass capsule "Continue" at the bottom + text button below.
 */
@Composable
private fun StepPage(
    glyph: Glyph, title: String, body: String, primary: String, onPrimary: () -> Unit,
    showBack: Boolean = false, onBack: () -> Unit = {}, centered: Boolean = false,
    secondary: String? = null, onSecondary: () -> Unit = {}, extra: Pair<String, () -> Unit>? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = SIDE.dp)) {
            Spacer(Modifier.height(TOP.dp))
            StepGlyph(glyph, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(26.dp))
            val align = if (centered) TextAlign.Center else TextAlign.Start
            BasicText(title, Modifier.fillMaxWidth(), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp,
                lineHeight = 38.sp, letterSpacing = (-0.9).sp, color = LABEL, textAlign = align))
            Spacer(Modifier.height(10.dp))
            BasicText(body, Modifier.fillMaxWidth(), style = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 22.sp,
                letterSpacing = (-0.35).sp, color = SECONDARY, textAlign = align))
            if (content != null) { Spacer(Modifier.height(28.dp)); content() }
            Spacer(Modifier.weight(1f))
            extra?.let { (label, action) -> TextButton(label, action, Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(10.dp)) }
            GlassButton(primary, onPrimary)
            Spacer(Modifier.height(12.dp))
            if (secondary != null) TextButton(secondary, onSecondary, Modifier.align(Alignment.CenterHorizontally)) else Spacer(Modifier.height(30.dp))
            Spacer(Modifier.height(BOTTOM.dp))
        }
        if (showBack) GlassBackButton(onBack, Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(label, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = ACCENT),
        modifier = modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(vertical = 4.dp, horizontal = 12.dp))
}

/** Grouped card (iOS 26: large rounding, white). */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CARD_R.dp)).background(CELL), content = content)
}

@Composable
private fun CardRow(label: String, icon: Color, iconGlyph: DrawScope.() -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(icon)) { iconGlyph() }
        Spacer(Modifier.width(14.dp))
        BasicText(label, Modifier.weight(1f), style = TextStyle(fontFamily = Inter, fontSize = 17.sp, letterSpacing = (-0.35).sp, color = LABEL))
        trailing()
    }
}

/**
 * Photos + Calendar as switches: turning on requests the permission, turning off opens the app settings
 * (a granted permission can only be revoked there). State = actually granted, re-read on return.
 */
@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    fun granted(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val photoPerm = android.Manifest.permission.READ_MEDIA_IMAGES
    val calPerm = android.Manifest.permission.READ_CALENDAR
    var photos by remember { mutableStateOf(granted(photoPerm)) }
    var calendar by remember { mutableStateOf(granted(calPerm)) }
    var badges by remember { mutableStateOf(io.github.janhurn9400.liquidy.data.NotificationBadges.hasAccess(context)) }
    // "All files access": the only way to read the Android wallpaper's pixels (glass, blur) — without it Liquidy draws
    // its own neutral image instead of the real wallpaper. Granted later the wallpaper is picked up on return (MainActivity.onResume)
    var wallpaperAccess by remember { mutableStateOf(io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead()) }
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { photos = it }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendar = it }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                photos = granted(photoPerm); calendar = granted(calPerm)
                badges = io.github.janhurn9400.liquidy.data.NotificationBadges.hasAccess(context)
                wallpaperAccess = io.github.janhurn9400.liquidy.glass.SystemWallpaper.canRead()
            }
        }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    fun openSettings() = runCatching {
        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
    Card {
        CardRow(stringResource(R.string.welcome_perm_photos), Color(0xFFFF9F0A), {
            // Blossom made of circles
            val c = Offset(size.width / 2, size.height / 2)
            for (k in 0 until 6) { val a = k * PI / 3; drawCircle(Color.White.copy(alpha = 0.85f), size.width * 0.14f,
                Offset(c.x + (cos(a) * size.width * 0.17).toFloat(), c.y + (sin(a) * size.width * 0.17).toFloat())) }
        }) {
            GlassToggle(photos, { on -> if (on) askPhotos.launch(photoPerm) else { photos = false; openSettings() } })
        }
        Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(0.5.dp).background(Color(0x1F000000)))
        CardRow(stringResource(R.string.welcome_perm_calendar), Color(0xFFFF3B30), {
            drawRect(Color.White, Offset(size.width * 0.22f, size.height * 0.3f), Size(size.width * 0.56f, size.height * 0.46f))
            drawRect(Color.White.copy(alpha = 0.6f), Offset(size.width * 0.22f, size.height * 0.22f), Size(size.width * 0.56f, size.height * 0.06f))
        }) {
            GlassToggle(calendar, { on -> if (on) askCalendar.launch(calPerm) else { calendar = false; openSettings() } })
        }
        Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(0.5.dp).background(Color(0x1F000000)))
        CardRow(stringResource(R.string.welcome_perm_badges), Color(0xFFFF3B30), {
            // App tile with a red counter
            drawRoundRect(Color.White, Offset(size.width * 0.2f, size.height * 0.28f), Size(size.width * 0.48f, size.height * 0.48f), CornerRadius(size.width * 0.12f))
            drawCircle(Color.White, size.width * 0.15f, Offset(size.width * 0.7f, size.height * 0.3f))
            drawCircle(Color(0xFFFF3B30), size.width * 0.1f, Offset(size.width * 0.7f, size.height * 0.3f))
        }) {
            GlassToggle(badges, { on -> if (on) io.github.janhurn9400.liquidy.data.NotificationBadges.requestAccess(context) else { badges = false; io.github.janhurn9400.liquidy.data.NotificationBadges.requestAccess(context) } })
        }
        Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(0.5.dp).background(Color(0x1F000000)))
        CardRow(stringResource(R.string.welcome_perm_wallpaper), Color(0xFF5E5CE6), {
            // landscape: sun over two hills
            drawCircle(Color.White, size.width * 0.09f, Offset(size.width * 0.66f, size.height * 0.34f))
            val hills = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.74f)
                lineTo(size.width * 0.42f, size.height * 0.44f); lineTo(size.width * 0.56f, size.height * 0.62f)
                lineTo(size.width * 0.66f, size.height * 0.52f); lineTo(size.width * 0.8f, size.height * 0.74f); close()
            }
            drawPath(hills, Color.White)
        }) {
            // the system page toggles it (granting and revoking), the state is re-read on return
            GlassToggle(wallpaperAccess, { _ ->
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", context.packageName, null)))
                }
            })
        }
    }
}

/** Duo style or classic: two previews of the unfolded home screen, selection with a circle (like Appearance). */
@Composable
private fun FoldStyleChooser() {
    val context = LocalContext.current
    val duo = io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (option in listOf(true, false)) {
            val sel = duo == option
            val pop by animateFloatAsState(if (sel) 1f else 0f, spring(0.55f, 500f), label = "fold")
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    io.github.janhurn9400.liquidy.data.FoldableStyle.set(context, option)
                }) {
                FoldPreview(duo = option, Modifier.graphicsLayer { val sc = 1f + 0.04f * pop; scaleX = sc; scaleY = sc })
                Spacer(Modifier.height(10.dp))
                BasicText(stringResource(if (option) R.string.welcome_fold_duo else R.string.welcome_fold_classic),
                    style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = LABEL))
                Spacer(Modifier.height(8.dp))
                RadioCircle(pop)
            }
        }
    }
}

/** Selection circle: empty grey, selected filled blue with a checkmark (springs up). */
@Composable
private fun RadioCircle(on: Float) {
    Canvas(Modifier.size(22.dp)) {
        val r = size.width / 2
        drawCircle(Color(0xFFC7C7CC), r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        if (on > 0.01f) {
            drawCircle(ACCENT, r * on.coerceAtMost(1.1f))
            val w = 2.dp.toPx(); val a = on.coerceIn(0f, 1f)
            drawLine(Color.White.copy(alpha = a), Offset(size.width * 0.3f, size.height * 0.52f), Offset(size.width * 0.44f, size.height * 0.66f), w, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = a), Offset(size.width * 0.44f, size.height * 0.66f), Offset(size.width * 0.71f, size.height * 0.37f), w, StrokeCap.Round)
        }
    }
}

/** Mini home screen unfolded (landscape): Duo = two pages side by side + dock vertical on the right; classic = one grid + dock at the bottom. */
@Composable
private fun FoldPreview(duo: Boolean, modifier: Modifier) {
    Canvas(modifier.size(132.dp, 93.dp).shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
        .clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF8FB0F2), Color(0xFF7FD0F0))))) {
        val w = size.width; val h = size.height
        val cols = listOf(Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFF0A84FF), Color(0xFFFF375F), Color(0xFFAF52DE), Color(0xFFFFD60A))
        fun grid(x0: Float, gw: Float, rows: Int, top: Float, gh: Float) {
            val ic = gw / 4 * 0.62f
            for (r in 0 until rows) for (c in 0 until 4) {
                val cx = x0 + gw / 4 * (c + 0.5f); val cy = top + gh / rows * (r + 0.5f)
                drawRoundRect(cols[(r * 4 + c) % cols.size], Offset(cx - ic / 2, cy - ic / 2), Size(ic, ic), CornerRadius(ic * 0.26f))
            }
        }
        if (duo) {
            val dock = w * 0.09f; val pw = (w - dock) / 2
            grid(0f, pw, 5, h * 0.08f, h * 0.84f); grid(pw, pw, 5, h * 0.08f, h * 0.84f)
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w - dock * 0.9f, h * 0.2f), Size(dock * 0.7f, h * 0.6f), CornerRadius(dock * 0.35f))
            drawLine(Color.White.copy(alpha = 0.35f), Offset(w / 2 - dock / 2, 0f), Offset(w / 2 - dock / 2, h), 0.8f)
        } else {
            grid(w * 0.18f, w * 0.64f, 4, h * 0.06f, h * 0.66f)
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.2f, h * 0.78f), Size(w * 0.6f, h * 0.16f), CornerRadius(h * 0.08f))
        }
    }
}

/** Instructions for the Magisk module as a numbered list (iOS cells); if the module is active, just a checkmark. */
@Composable
internal fun RootSteps(active: Boolean, cardColor: Color = CELL, cardCorner: Float = CARD_R.toFloat()) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(cardCorner.dp)).background(cardColor)) {
        if (active) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(26.dp)) {
                    drawCircle(Color(0xFF34C759))
                    val w = 2.4.dp.toPx()
                    drawLine(Color.White, Offset(size.width * 0.28f, size.height * 0.52f), Offset(size.width * 0.44f, size.height * 0.68f), w, StrokeCap.Round)
                    drawLine(Color.White, Offset(size.width * 0.44f, size.height * 0.68f), Offset(size.width * 0.73f, size.height * 0.35f), w, StrokeCap.Round)
                }
                Spacer(Modifier.width(14.dp))
                BasicText(stringResource(R.string.welcome_root_active), style = TextStyle(fontFamily = Inter, fontSize = 17.sp, letterSpacing = (-0.35).sp, color = LABEL))
            }
            return@Column
        }
        listOf(R.string.welcome_root_step1, R.string.welcome_root_step2, R.string.welcome_root_step3, R.string.welcome_root_step4).forEachIndexed { i, res ->
            if (i > 0) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(0.5.dp).background(Color(0x1F000000)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(ACCENT), contentAlignment = Alignment.Center) {
                    BasicText("${i + 1}", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White))
                }
                Spacer(Modifier.width(14.dp))
                val style = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp, color = LABEL)
                if (res == R.string.welcome_root_step1) {
                    // "… from our GitHub page." — that part is a tappable link to the module download
                    val link = stringResource(R.string.welcome_root_github)
                    val full = stringResource(res, link)
                    val start = full.indexOf(link)
                    val text = androidx.compose.ui.text.buildAnnotatedString {
                        append(full.substring(0, start))
                        withLink(androidx.compose.ui.text.LinkAnnotation.Url(MODULE_URL,
                            androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = ACCENT, fontWeight = FontWeight.Medium)))) { append(link) }
                        append(full.substring(start + link.length))
                    }
                    BasicText(text, Modifier.weight(1f), style = style)
                } else BasicText(stringResource(res), Modifier.weight(1f), style = style)
            }
        }
    }
}

/**
 * Icon style like in the "Customise" sheet (Edit Home Screen): real app icons in the four styles,
 * directly on the page, selection as a capsule, below it Light/Dark; Tinted → hue + saturation.
 */
@Composable
private fun AppearanceChooser(sample: AppEntry?, @Suppress("UNUSED_PARAMETER") userWallpaper: ImageBitmap) {
    val context = LocalContext.current
    // On the white page: neutral mid-grey ground for the glass styles (Clear/Tinted) instead of our own wallpaper
    val wallpaper = remember { context.resources.displayMetrics.let { neutralGlassGround(it.widthPixels, it.heightPixels) } }
    fun save() = IconAppearance.save(context)
    Box(Modifier.fillMaxWidth().animateContentSize(spring(1f, 300f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            WithWallpaperGlass(wallpaper) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconStyle.entries.forEach { st ->
                        val sel = IconAppearance.style == st
                        val pick = {
                            IconAppearance.style = st
                            if (st == IconStyle.Dark && IconAppearance.variant == IconVariant.Dark) IconAppearance.variant = IconVariant.Light
                            save()
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val iconShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (PREVIEW / DesignTokens.iconSize.value))
                            Box(Modifier.size(PREVIEW.dp)
                                // on white: soft shadow + hairline, otherwise light icons disappear
                                .shadow(6.dp, iconShape, ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
                                .border(0.5.dp, Color(0x1A000000), iconShape)
                                .clip(iconShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = pick)) {
                                if (sample != null) StyledIcon(sample, wallpaper, Modifier.fillMaxSize(), style = st,
                                    dark = if (st == IconStyle.Dark) true else IconAppearance.dark)
                            }
                            Spacer(Modifier.height(8.dp))
                            SelCapsule(stringResource(when (st) { IconStyle.Default -> R.string.style_default; IconStyle.Dark -> R.string.style_dark; IconStyle.Clear -> R.string.style_clear; IconStyle.Tinted -> R.string.style_tinted }), sel, pick)
                        }
                    }
                }
            }
            AnimatedVisibility(IconAppearance.style == IconStyle.Tinted) {
                Column(Modifier.padding(top = 18.dp)) {
                    SheetSlider(Brush.horizontalGradient(SHEET_HUES), IconAppearance.hue / 360f) { IconAppearance.hue = it * 360f; save() }
                    Spacer(Modifier.height(10.dp))
                    SheetSlider(Brush.horizontalGradient(listOf(Color.White, IconAppearance.tint(1f, 1f / IconAppearance.saturation.coerceAtLeast(0.01f)))),
                        IconAppearance.saturation) { IconAppearance.saturation = it; save() }
                }
            }
            AnimatedVisibility(IconAppearance.style != IconStyle.Default) {
                val options = if (IconAppearance.style == IconStyle.Dark) listOf(IconVariant.Light to R.string.variant_always, IconVariant.Auto to R.string.variant_auto)
                else listOf(IconVariant.Light to R.string.variant_light, IconVariant.Dark to R.string.variant_dark, IconVariant.Auto to R.string.variant_auto)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (v, res) -> SelCapsule(stringResource(res), IconAppearance.variant == v) { IconAppearance.variant = v; save() } }
                }
            }
            // Haptics (only a few, iOS-typical places; can be turned off because many Android motors are "mushy")
            Spacer(Modifier.height(24.dp))
            Card {
                CardRow(stringResource(R.string.welcome_haptics), Color(0xFF8E8E93), {
                    val w = size.width; val sw = w * 0.07f
                    drawRoundRect(Color.White, Offset(w * 0.36f, w * 0.18f), Size(w * 0.28f, w * 0.64f), CornerRadius(w * 0.06f), style = Stroke(sw))
                    for (side in listOf(-1f, 1f)) drawLine(Color.White, Offset(w / 2 + side * w * 0.26f, w * 0.38f), Offset(w / 2 + side * w * 0.26f, w * 0.62f), sw, StrokeCap.Round)
                }) {
                    GlassToggle(io.github.janhurn9400.liquidy.data.Haptics.enabled, { io.github.janhurn9400.liquidy.data.Haptics.set(context, it) })
                }
            }
        }
    }
}

/** Mid-grey, slightly cool gradient as a "wallpaper" for glass icons on light pages (white motif stays legible). */
private fun neutralGlassGround(w: Int, h: Int): ImageBitmap {
    // Glass surfaces draw the wallpaper 1:1 in screen coordinates → image at screen size (RGB_565, once)
    val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.RGB_565)
    android.graphics.Canvas(b).drawPaint(android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF8E97A6.toInt(), 0xFFB9C0CB.toInt(), android.graphics.Shader.TileMode.CLAMP)
    })
    return b.asImageBitmap()
}

/** Caption, when selected with a grey capsule behind it (Customise principle, on a light ground). */
@Composable
private fun SelCapsule(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).clip(CircleShape).background(if (selected) Color(0x14000000) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(fontFamily = Inter, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 15.sp,
            color = if (selected) LABEL else SECONDARY))
    }
}

/** Thick colour slider like in the Customise sheet: 30 pt track, knob = ring in the tint. */
@Composable
private fun SheetSlider(track: Brush, value: Float, onChange: (Float) -> Unit) {
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp)
            .pointerInput(Unit) { detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { ch, _ -> change((ch.position.x / size.width).coerceIn(0f, 1f)) } }
    ) {
        val th = 30.dp.toPx(); val cy = size.height / 2; val r = 15.dp.toPx()
        drawRoundRect(track, Offset(0f, cy - th / 2), Size(size.width, th), CornerRadius(th / 2))
        drawRoundRect(Color(0x1A000000), Offset(0f, cy - th / 2), Size(size.width, th), CornerRadius(th / 2), style = Stroke(0.5.dp.toPx()))
        val x = r + (size.width - 2 * r) * value
        drawCircle(IconAppearance.tint(1f), r, Offset(x, cy))
        drawCircle(Color.White, r - 1.5.dp.toPx(), Offset(x, cy), style = Stroke(3.dp.toPx()))
    }
}

/** Own filled glyphs (no SF Symbols): primary shape blue, secondary parts blue 30 % — hierarchical like in the iOS setup. */
@Composable
private fun StepGlyph(g: Glyph, modifier: Modifier = Modifier) {
    Canvas(modifier.size(GLYPH.dp)) {
        val s = size.width
        val soft = ACCENT.copy(alpha = 0.3f)
        when (g) {
            Glyph.Home -> { // phone: soft area, frame, app grid
                drawRoundRect(soft, Offset(s * 0.24f, s * 0.04f), Size(s * 0.52f, s * 0.92f), CornerRadius(s * 0.12f))
                drawRoundRect(ACCENT, Offset(s * 0.24f, s * 0.04f), Size(s * 0.52f, s * 0.92f), CornerRadius(s * 0.12f), style = Stroke(s * 0.045f))
                for (r in 0 until 3) for (c in 0 until 3) drawRoundRect(ACCENT, Offset(s * (0.32f + c * 0.13f), s * (0.17f + r * 0.14f)),
                    Size(s * 0.09f, s * 0.09f), CornerRadius(s * 0.025f))
            }
            Glyph.Lock -> {
                drawArc(soft, 180f, 180f, false, Offset(s * 0.3f, s * 0.1f), Size(s * 0.4f, s * 0.56f), style = Stroke(s * 0.09f, cap = StrokeCap.Round))
                drawLine(soft, Offset(s * 0.3f, s * 0.38f), Offset(s * 0.3f, s * 0.46f), s * 0.09f, StrokeCap.Round)
                drawLine(soft, Offset(s * 0.7f, s * 0.38f), Offset(s * 0.7f, s * 0.46f), s * 0.09f, StrokeCap.Round)
                drawRoundRect(ACCENT, Offset(s * 0.17f, s * 0.42f), Size(s * 0.66f, s * 0.5f), CornerRadius(s * 0.1f))
                drawCircle(Color.White, s * 0.055f, Offset(s / 2, s * 0.62f))
                drawLine(Color.White, Offset(s / 2, s * 0.64f), Offset(s / 2, s * 0.76f), s * 0.05f, StrokeCap.Round)
            }
            Glyph.Appearance -> { // half light, half dark
                drawCircle(soft, s * 0.44f)
                drawArc(ACCENT, 90f, 180f, true, Offset(s * 0.06f, s * 0.06f), Size(s * 0.88f, s * 0.88f))
            }
            Glyph.Fold -> { // opened book: two halves, hinge in the middle
                drawRoundRect(soft, Offset(s * 0.06f, s * 0.18f), Size(s * 0.42f, s * 0.64f), CornerRadius(s * 0.08f))
                drawRoundRect(ACCENT, Offset(s * 0.52f, s * 0.18f), Size(s * 0.42f, s * 0.64f), CornerRadius(s * 0.08f))
                drawLine(ACCENT, Offset(s / 2, s * 0.12f), Offset(s / 2, s * 0.88f), s * 0.035f, StrokeCap.Round)
            }
            Glyph.Sparkle -> { // curved four-pointed star + small star
                fun star(cx: Float, cy: Float, r: Float, color: Color) {
                    val k = r * 0.16f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy - r)
                        quadraticTo(cx + k, cy - k, cx + r, cy); quadraticTo(cx + k, cy + k, cx, cy + r)
                        quadraticTo(cx - k, cy + k, cx - r, cy); quadraticTo(cx - k, cy - k, cx, cy - r); close()
                    }
                    drawPath(path, color)
                }
                star(s * 0.44f, s * 0.54f, s * 0.38f, ACCENT)
                star(s * 0.8f, s * 0.2f, s * 0.15f, soft)
            }
            Glyph.Check -> {
                drawCircle(ACCENT, s * 0.46f)
                val w = s * 0.085f
                drawLine(Color.White, Offset(s * 0.3f, s * 0.52f), Offset(s * 0.44f, s * 0.66f), w, StrokeCap.Round)
                drawLine(Color.White, Offset(s * 0.44f, s * 0.66f), Offset(s * 0.71f, s * 0.36f), w, StrokeCap.Round)
            }
        }
    }
}

private val HUES = (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 0.6f, 0.95f))) }
private val CELL = Color(0xFFF2F2F7)
private val PUSH = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1f)
private val PUSH_SPRING = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 1f, stiffness = 158f) // (2π/0,5 s)²
private const val SIDE = 28
private const val TOP = 64
private const val BOTTOM = 12
private const val GLYPH = 64
private const val CARD_R = 24
private const val PANEL_R = 32
private const val PANEL_BLUR = 14
private const val PREVIEW = 61f
private val SHEET_HUES = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
private const val PUSH_MS = 460
// Hello → first page, measured against the notification centre (screen recording 10-08 02:34, 60 fps, frame 334–371)
private const val ZOOM_FROM = 2.6f
private val ZOOM_STIFFNESS = (2 * kotlin.math.PI.toFloat() / 0.3f).let { it * it }
private const val ZOOM_FADE_MS = 110
private const val ZOOM_SHARPEN_MS = 350
private const val ZOOM_BLUR_DP = 12f
