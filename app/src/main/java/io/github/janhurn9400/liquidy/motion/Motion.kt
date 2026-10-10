package io.github.janhurn9400.liquidy.motion

import io.github.janhurn9400.liquidy.tuning.tunable

/**
 * Alle Animationsparameter. Jeder Wert verweist auf seine ID in docs/RESEARCH.md §3.3.
 * Kein Animationswert darf im UI-Code stehen — nur hier.
 */
object Motion {

    // M-PAGE — Seite einrasten. [GEMESSEN] 4 Swipes: response 0,32–0,38 s, ζ 0,89–0,92
    val page = iosSpring("M-PAGE", "Seite einrasten", response = 0.36f, dampingFraction = 0.90f)

    // M-PAGE-THRESH — Seitenwechsel ab dieser Fingergeschwindigkeit oder diesem Versatz. [GESCHÄTZT]
    val pageFlingVelocity = tunable("M-PAGE-THRESH.velocity", "Seitenwechsel ab Geschwindigkeit", "Motion", 300f, 50f, 1500f, "pt/s")
    val pageDistanceFraction = tunable("M-PAGE-THRESH.distance", "Seitenwechsel ab Versatz", "Motion", 0.5f, 0.2f, 0.8f, "×Breite")

    // M-RUBBER — Gummiband am ersten/letzten Seitenrand: (x·d·c)/(d + c·x). [BELEGT] UIScrollView c = 0,55
    val rubberBandC = tunable("M-RUBBER.c", "Gummiband-Konstante", "Motion", 0.55f, 0.1f, 1.0f)

    // M-PRESS — Icon-Abdunkeln beim Touch-Down: sofort (≤ 1 Frame). [GEMESSEN]
    val pressInDelay = tunable("M-PRESS.delay", "Abdunkeln – Verzögerung", "Motion", 0f, 0f, 200f, "ms")
    // M-PRESS-CANCEL — Zurückblenden ohne App-Start. [GESCHÄTZT]
    val pressOut = iosSpring("M-PRESS-CANCEL", "Abdunkeln zurück", response = 0.25f, dampingFraction = 1.0f)

    // M-PILL — „Search“ ↔ Page-Dots. [GEMESSEN] Überblendung ≈ 0,15 s, Dots bleiben ≈ 0,5 s nach dem Einrasten
    val pillCrossfade = tunable("M-PILL.fade", "Pill-Überblendung", "Motion", 150f, 0f, 600f, "ms")
    val pillDotsHold = tunable("M-PILL.hold", "Dots stehen lassen", "Motion", 500f, 0f, 2000f, "ms")

    // ── Phase 2: Bearbeitungsmodus ──────────────────────────────────────────────────
    // M-LONGPRESS — Haltedauer bis Bearbeitungsmodus. [GESCHÄTZT] UIKit-Kontextmenü ≈ 0,5 s
    val longPress = tunable("M-LONGPRESS", "Long-Press-Dauer", "Bearbeiten", 500f, 200f, 1200f, "ms")

    // M-JIGGLE — [GEMESSEN] 4 Icons, Sinus, Phase je Icon zufällig, startet sofort mit voller Amplitude
    val jiggleRotation = tunable("M-JIGGLE.rot", "Wackeln – Drehung", "Bearbeiten", 1.70f, 0f, 5f, "°")
    val jiggleRotationPeriod = tunable("M-JIGGLE.rotP", "Wackeln – Periode Drehung", "Bearbeiten", 0.256f, 0.1f, 0.6f, "s")
    val jiggleTranslation = tunable("M-JIGGLE.tr", "Wackeln – Versatz", "Bearbeiten", 0.39f, 0f, 2f, "pt")
    val jigglePeriodX = tunable("M-JIGGLE.xP", "Wackeln – Periode x", "Bearbeiten", 0.268f, 0.1f, 0.6f, "s")
    val jigglePeriodY = tunable("M-JIGGLE.yP", "Wackeln – Periode y", "Bearbeiten", 0.284f, 0.1f, 0.6f, "s")
    // M-JIGGLE-STOP — Ausklingen beim Verlassen. [GESCHÄTZT]
    val jiggleStop = iosSpring("M-JIGGLE-STOP", "Wackeln ausklingen", response = 0.2f, dampingFraction = 1.0f)

    // M-BADGE — Minus-Badges / Edit-Buttons erscheinen. [GESCHÄTZT]
    val badge = iosSpring("M-BADGE", "Badge erscheinen", response = 0.3f, dampingFraction = 0.8f)

    // M-DRAG-LIFT — Icon wird beim Ziehen größer. [GESCHÄTZT]
    val dragLift = iosSpring("M-DRAG-LIFT", "Icon anheben", response = 0.3f, dampingFraction = 0.75f)
    // M-REFLOW — andere Icons weichen aus. [GESCHÄTZT] nah an den gemessenen Systemfedern (0,34 s, kritisch)
    val reflow = iosSpring("M-REFLOW", "Ausweichen", response = 0.4f, dampingFraction = 0.9f)
    /** Heute-Ansicht: Widgets weichen beim Vergrößern/Verschieben federnd aus (iOS: leichtes Nachschwingen) [GESCHÄTZT]. */
    /** Seitenübersicht auf/zu: Seite schrumpft in ca. 0,25 s in ihre Karte (Aufnahme 10-08 09:55, Bild 357–370) [GEMESSEN grob]. */
    /** Widget-Stapel einrasten: gemessen ≈ 0,27 s bis zur Ruhe, kein Nachschwingen (Aufnahme 10-08 09:56, Bild 858–874). */
    /** Widget fliegt aus dem Picker an seinen Platz: ≈ 0,25 s (47,70 → 47,95 s) [GEMESSEN grob]. */
    val widgetFly = iosSpring("M-WIDGET-FLY", "Widget – Flug aus dem Picker", response = 0.55f, dampingFraction = 0.9f)
    /** Nach der Landung: 1,12× → 1,0, Inhalt scharf (47,95 → 48,2 s). */
    val widgetSettle = iosSpring("M-WIDGET-SETTLE", "Widget – Einschwingen", response = 0.45f, dampingFraction = 0.72f)
    val stackSnap = iosSpring("M-STACK", "Widget-Stapel – Einrasten", response = 0.32f, dampingFraction = 1f)
    /** Glasrahmen/Punkte des Stapels ein-/ausblenden (Bild 858 an, ≈ 896 weg) [GESCHÄTZT]. */
    /** Nach dem Einrasten wächst der Inhalt im Glasrahmen wieder auf volle Größe: ≈ 0,4 s (Bild 865–889) [GEMESSEN grob]. */
    val stackGrow = iosSpring("M-STACK.grow", "Widget-Stapel – Aufwachsen", response = 0.47f, dampingFraction = 1f)
    val stackFrame = iosSpring("M-STACK.frame", "Widget-Stapel – Rahmen", response = 0.25f, dampingFraction = 1f)
    /** „Stapel bearbeiten“ auf/zu [GESCHÄTZT] nach Aufnahme 10-08 10:21. */
    /** Panel wächst aus dem Stapel zur Mitte: ≈ 0,3 s, leicht überschwingend (Aufnahme 10-08 10:21, Bild 288–302). */
    val stackEditor = iosSpring("M-STACK.edit", "Stapel bearbeiten", response = 0.36f, dampingFraction = 0.82f)
    val pagesOverview = iosSpring("M-PAGES", "Seitenübersicht", response = 0.38f, dampingFraction = 1f)
    val todayReflow = iosSpring("M-TODAY-REFLOW", "Heute – Ausweichen", response = 0.42f, dampingFraction = 0.78f)
    val reflowDelay = tunable("M-REFLOW.delay", "Ausweichen – Verzögerung", "Bearbeiten", 100f, 0f, 500f, "ms")
    // M-DROP — Icon fällt in seinen Platz. [GESCHÄTZT]
    val drop = iosSpring("M-DROP", "Ablegen", response = 0.4f, dampingFraction = 0.85f)
    // M-EDGE — Seitenwechsel, wenn das Icon am Rand gehalten wird. [GESCHÄTZT]
    val edgeFlipDelay = tunable("M-EDGE.delay", "Rand halten bis Seitenwechsel", "Bearbeiten", 600f, 100f, 1500f, "ms")
    val edgeFlipRepeat = tunable("M-EDGE.repeat", "Weiterer Seitenwechsel nach", "Bearbeiten", 900f, 200f, 2000f, "ms")

    // M-ALERT — Lösch-Dialog auf/zu. [GESCHÄTZT]
    val alert = iosSpring("M-ALERT", "Dialog", response = 0.35f, dampingFraction = 0.85f)

    // ── Phase 3: Ordner ─────────────────────────────────────────────────────────────
    // M-FOLDER — Öffnen UND Schließen. [GEMESSEN] response 0,34 s, ζ 1,0, aus dem Ordner-Icon heraus
    val folder = iosSpring("M-FOLDER", "Ordner auf/zu", response = 0.34f, dampingFraction = 1.0f)
    // M-FOLDER-HANDOFF — Überblendung geschlossenes Icon ↔ Panel in der kleinsten Phase (Fortschritt 0…x) [GESCHÄTZT]
    val folderHandoff = tunable("M-FOLDER.handoff", "Icon↔Panel-Überblendung bis Fortschritt", "Ordner", 0.12f, 0.01f, 0.5f)
    // M-MERGE — Halten über einem Icon bis zur Ordner-Absicht + Glas-Hinweis. [GESCHÄTZT]
    val mergeDelay = tunable("M-MERGE.delay", "Über Icon halten bis Ordner", "Ordner", 400f, 100f, 1200f, "ms")
    val mergeHint = iosSpring("M-MERGE", "Ordner-Hinweis", response = 0.3f, dampingFraction = 0.8f)

    // ── Phase 4: Suche (Swipe nach unten) — Rev. 4, [GEMESSEN] recording3 (8 Zieh-Vorgänge, frameweise) ──────
    // Steuergröße ist der Homescreen-Versatz h (pt). Finger → h mit UIScrollView-Gummiband (M-RUBBER, d = Bildschirmhöhe) [ABGELEITET].
    // Beim Ziehen öffnet NICHTS (auch nicht die Tastatur); erst beim Loslassen entscheidet h ≥ Commit.
    val searchCommit = tunable("M-SEARCH.commit", "Suche öffnet beim Loslassen ab h", "Suche", 60f, 20f, 150f, "pt") // 40 pt bricht ab, 90 pt öffnet
    val searchBlurRamp = tunable("M-SEARCH.blurRamp", "Blur voll ab h", "Suche", 12f, 1f, 60f, "pt") // Blur setzt fast sofort ein
    // Block blendet ein UND wird dabei scharf; Sichtvergleich: bei h ≈ 52 halb sichtbar + stark unscharf, bei h ≈ 90 scharf (hin und zurück gleich)
    val searchBlockFadeStart = tunable("M-SEARCH-BLOCK.fadeStart", "Vorschläge einblenden ab h", "Suche", 15f, 0f, 150f, "pt")
    val searchBlockFadeEnd = tunable("M-SEARCH-BLOCK.fadeEnd", "Vorschläge voll sichtbar ab h", "Suche", 65f, 10f, 200f, "pt")
    val searchBlockSharpStart = tunable("M-SEARCH-BLOCK.sharpStart", "Vorschläge werden scharf ab h", "Suche", 45f, 0f, 150f, "pt")
    val searchBlockSharpEnd = tunable("M-SEARCH-BLOCK.sharpEnd", "Vorschläge ganz scharf ab h", "Suche", 95f, 20f, 250f, "pt")
    // M-SEARCH-MORPH — „Search“-Pill morpht zum Suchfeld, schon während des Ziehens [GEMESSEN] recording5:
    // Pill bleibt scharf, wandert mit ¼ von h mit; Morph startet bei h ≈ 94 pt, wächst in ≈ 0,3 s auf VOLLE Feldbreite (≈ 386 pt); zurück unter ≈ 62 pt (recording3)
    val searchPillFollow = tunable("M-SEARCH-MORPH.follow", "Pill wandert mit (× h)", "Suche", 0.25f, 0f, 1f, "×")
    val searchMorphOn = tunable("M-SEARCH-MORPH.on", "Pill → Suchfeld ab h", "Suche", 94f, 20f, 200f, "pt")
    val searchMorphOff = tunable("M-SEARCH-MORPH.off", "Suchfeld → Pill unter h", "Suche", 62f, 0f, 200f, "pt")
    val searchMorph = iosSpring("M-SEARCH-MORPH", "Pill ↔ Suchfeld", response = 0.3f, dampingFraction = 0.9f)
    // M-SEARCH-KB — Tastatur (App-gesteuert): lugt beim Morph heraus, fährt beim Loslassen mit der iOS-Tastaturfeder hoch.
    // [GEMESSEN] Feder 0,44 s / ζ 0,85 (recording1); Herauslugen ≈ 34 pt, 0,1 s nach dem Morph (recording5)
    val searchKeyboard = iosSpring("M-SEARCH-KB", "Tastatur", response = 0.44f, dampingFraction = 0.85f)
    val searchKeyboardPeek = tunable("M-SEARCH-KB.peek", "Tastatur lugt heraus beim Morph", "Suche", 34f, 0f, 120f, "pt")
    // Inhalt gestaffelt überblenden, damit „Search“ (Pill) und Platzhalter (Feld) nie doppelt stehen [GESCHÄTZT]
    val searchMorphPillOut = tunable("M-SEARCH-MORPH.pillOut", "Pill-Inhalt weg bis Morph", "Suche", 0.35f, 0.05f, 1f)
    val searchMorphFieldIn = tunable("M-SEARCH-MORPH.fieldIn", "Feld-Inhalt ab Morph", "Suche", 0.45f, 0f, 0.95f)
    // Block läuft 1:1 mit dem Homescreen: Versatz = h + base (Fit: 1,07·h − 31)
    val searchBlockBase = tunable("M-SEARCH-BLOCK.base", "Vorschläge Versatz = h + …", "Suche", -31f, -120f, 0f, "pt")
    // M-SEARCH — Öffnen: Block UND Homescreen federn zurück auf 0 pt (Homescreen bleibt verkleinert + abgedunkelt). [GEMESSEN] 0,46 s / ζ 0,81 (recording1); Homescreen-Rücklauf ≈ 0,3 s (recording3)
    val search = iosSpring("M-SEARCH", "Suche einrasten", response = 0.46f, dampingFraction = 0.81f)
    // M-SEARCH-CLOSE — Schließen/Abbrechen. [GEMESSEN] 0,28 / 0,30 s, ζ 1,0 / 0,97 (recording3)
    val searchClose = iosSpring("M-SEARCH-CLOSE", "Suche schließen", response = 0.29f, dampingFraction = 1.0f)

    // ── Phase 4: App-Mediathek ───────────────────────────────────────────────────────
    // M-SCROLL — Abbremsen wie UIScrollView.DecelerationRate.normal = 0,998/ms [BELEGT].
    // v(t) = v0·0,998^(t/ms) = v0·e^(−2,002·t/s). Compose exponentialDecay: v0·e^(−4,2·f·t) → f = 2,002/4,2 = 0,4767 [ABGELEITET]
    val scrollDecayFriction = tunable("M-SCROLL.friction", "Scroll-Abbremsen (frictionMultiplier)", "Mediathek", 0.4767f, 0.1f, 2f)
    // M-SCROLL-BOUNCE — Zurückfedern nach Überscrollen. [GESCHÄTZT] kritisch gedämpft wie die gemessenen Systemfedern
    val scrollBounce = iosSpring("M-SCROLL-BOUNCE", "Zurückfedern", response = 0.4f, dampingFraction = 1.0f)

    // M-LIB-LIST — Mediathek → A–Z-Liste durch Herunterziehen [GEMESSEN] recording2 10,1–11,9 s / 12,97–13,55 s (frameweise):
    // Sobald man zieht, werden die Kacheln in ≈ 0,12 s unscharf; Liste blendet ab ≈ 0,3 s ein, Abdunkeln über ≈ 0,75 s.
    // Schließen („Cancel“): Liste weg in ≈ 0,17 s, Kacheln werden ab ≈ 0,19 s wieder scharf (≈ 0,38 s), gesamt ≈ 0,57 s.
    val libTilesOut = tunable("M-LIB-LIST.tilesOut", "Kacheln unscharf", "Mediathek", 120f, 0f, 600f, "ms")
    val libListInDelay = tunable("M-LIB-LIST.listDelay", "Liste einblenden ab", "Mediathek", 300f, 0f, 800f, "ms")
    val libListIn = tunable("M-LIB-LIST.listIn", "Liste einblenden", "Mediathek", 450f, 0f, 1000f, "ms")
    val libListDimIn = tunable("M-LIB-LIST.dimIn", "Abdunkeln (öffnen)", "Mediathek", 750f, 0f, 1500f, "ms")
    val libListOut = tunable("M-LIB-LIST.listOut", "Liste ausblenden", "Mediathek", 170f, 0f, 600f, "ms")
    val libTilesInDelay = tunable("M-LIB-LIST.tilesDelay", "Kacheln scharf ab", "Mediathek", 190f, 0f, 800f, "ms")
    val libTilesIn = tunable("M-LIB-LIST.tilesIn", "Kacheln scharf", "Mediathek", 380f, 0f, 1000f, "ms")
    val libListDimOut = tunable("M-LIB-LIST.dimOut", "Abdunkeln (schließen)", "Mediathek", 570f, 0f, 1500f, "ms")
    // Beim Ziehen folgt die Liste dem Finger 1:1, das Suchfeld nur mit ⅓ (Verhältnis über 0,9 s konstant 3,0)
    val libListFieldFollow = tunable("M-LIB-LIST.follow", "Suchfeld folgt (× Finger)", "Mediathek", 0.333f, 0f, 1f, "×")
    // Loslassen: Liste + Feld federn zurück, Startgeschwindigkeit 0. Fit: response ≈ 0,5 s, kritisch gedämpft
    val libListSettle = iosSpring("M-LIB-LIST-SETTLE", "Liste einrasten", response = 0.5f, dampingFraction = 1.0f)
    // M-LIB-CAT — Kategorie aufklappen [GEMESSEN] recording2 17,50–18,10 s, frameweise (Mehrskalen-Template-Match):
    // Raster + Titel zoomen als EINE Gruppe aus dem Mini-Cluster (Skala 0 → 1); Fit response 0,54 s, ζ 0,9, rms 0,005.
    // Die 4 Cluster-Icons fliegen aus ihren Mini-Plätzen in ihre Rasterplätze; der Cluster in der Kachel ist sofort leer.
    val libCategory = iosSpring("M-LIB-CAT", "Kategorie auf", response = 0.54f, dampingFraction = 0.9f)
    // Zuklappen ist deutlich schneller: Gruppe in ≈ 0,2 s im Cluster (18,60–18,81 s) [GEMESSEN grob]
    val libCategoryClose = iosSpring("M-LIB-CAT-CLOSE", "Kategorie zu", response = 0.32f, dampingFraction = 1.0f)
    // Mediathek dahinter: schrumpft um (Mitte, 0,48·Höhe) auf 0,88 (Fit über 4 Anker) und ist bei Fortschritt ≈ 0,55 weg (unscharf + ausgeblendet)
    val libCategoryScale = tunable("M-LIB-CAT.scale", "Mediathek schrumpft auf", "Mediathek", 0.88f, 0.7f, 1f, "×")
    val libCategoryScaleEnd = tunable("M-LIB-CAT.scaleEnd", "… erreicht bei Fortschritt", "Mediathek", 0.5f, 0.1f, 1f)
    val libCategoryPivotY = tunable("M-LIB-CAT.pivotY", "Schrumpf-Zentrum (× Höhe)", "Mediathek", 0.48f, 0f, 1f, "×")
    val libCategoryFadeEnd = tunable("M-LIB-CAT.fadeEnd", "Mediathek weg bei Fortschritt", "Mediathek", 0.55f, 0.1f, 1f)
    val libCategoryTitleFrom = tunable("M-LIB-CAT.titleFrom", "Titel blendet ein ab Fortschritt", "Mediathek", 0.5f, 0f, 1f)

    // M-CTXMENU — Long-Press auf ein Icon → Kontextmenü [GEMESSEN] recording4 (3 Long-Presses, frameweise):
    // Touch-Down sofort dunkel (M-PRESS); ab +0,20 s wächst das Icon langsam auf 1,23 (bis +0,73 s); ab +0,47 s blendet das Dunkel aus (0,2 s).
    // Bei +0,82 s öffnet das Menü: Icon in ≈ 0,08 s auf 1,15 und 10 pt hoch; Menü wächst aus dem Icon (≈ 0,15 s, ≈ 1 % Überschwinger,
    // Text skaliert mit); Hintergrund in ≈ 0,2 s auf 48 %. Schließen: Menü in ≈ 0,1 s weg, Hintergrund ≈ 0,22 s.
    val ctxDelay = tunable("M-CTXMENU.delay", "Menü öffnet nach", "Kontextmenü", 820f, 300f, 1500f, "ms")
    val ctxGrowDelay = tunable("M-CTXMENU.growDelay", "Icon wächst ab", "Kontextmenü", 200f, 0f, 800f, "ms")
    val ctxGrowDuration = tunable("M-CTXMENU.grow", "Icon wächst über", "Kontextmenü", 530f, 50f, 1200f, "ms")
    val ctxPressScale = tunable("M-CTXMENU.pressScale", "Icon wächst auf", "Kontextmenü", 1.23f, 1f, 1.5f, "×")
    val ctxPressFadeDelay = tunable("M-CTXMENU.fadeDelay", "Dunkel blendet aus ab", "Kontextmenü", 470f, 0f, 1000f, "ms")
    val ctxPressFade = tunable("M-CTXMENU.fade", "Dunkel blendet aus über", "Kontextmenü", 200f, 0f, 600f, "ms")
    val ctxLiftScale = tunable("M-CTXMENU.liftScale", "Icon bei offenem Menü", "Kontextmenü", 1.15f, 1f, 1.5f, "×")
    val ctxLiftY = tunable("M-CTXMENU.liftY", "Icon hebt sich um", "Kontextmenü", 10f, 0f, 30f, "pt")
    val ctxLift = iosSpring("M-CTXMENU-LIFT", "Icon anheben", response = 0.15f, dampingFraction = 1.0f)
    val ctxMenu = iosSpring("M-CTXMENU-OPEN", "Menü auf", response = 0.3f, dampingFraction = 0.8f)
    val ctxMenuFrom = tunable("M-CTXMENU.from", "Menü wächst ab Skala", "Kontextmenü", 0.3f, 0f, 1f, "×")
    val ctxMenuClose = iosSpring("M-CTXMENU-CLOSE", "Menü zu", response = 0.2f, dampingFraction = 1.0f)
    val ctxDimIn = tunable("M-CTXMENU.dimIn", "Abdunkeln (auf)", "Kontextmenü", 200f, 0f, 600f, "ms")
    val ctxDimOut = tunable("M-CTXMENU.dimOut", "Abdunkeln (zu)", "Kontextmenü", 220f, 0f, 600f, "ms")

    // M-WPE — Wallpaper-Editor: Homescreen schrumpft zur Vorschau / Blätter fahren hoch [GESCHÄTZT] nach recording6 (Sichtvergleich)
    val wpeAppear = iosSpring("M-WPE", "Editor auf/zu", response = 0.45f, dampingFraction = 1.0f)
    val wpeSheet = iosSpring("M-WPE-SHEET", "Blatt auf/zu", response = 0.4f, dampingFraction = 1.0f)

    // M-WIDGET-RESIZE — Griff unten rechts im Bearbeitungsmodus (recording8 39,5–48 s, frameweise Sichtung):
    // Rahmen folgt dem Finger stufenlos, Icons weichen live aus; Unschärfe folgt dem Abstand zur nächsten Größe
    // (genau auf klein/mittel/groß scharf — Nutzer-Beobachtung am iPhone). Loslassen → rastet ein, Rest-Unschärfe blendet aus.
    val widgetResize = iosSpring("M-WIDGET-RESIZE", "Widget einrasten", response = 0.35f, dampingFraction = 0.86f)
    val widgetResizeBlurOut = tunable("M-WIDGET-RESIZE.blurOut", "Inhalt wieder scharf", "Widgets", 220f, 0f, 800f, "ms")

    // M-HOME-RETURN — App schließen: Homescreen (Android-Wallpaper + Icons) erscheint unscharf und wird scharf,
    // nichts bewegt sich (iOS, Nutzer-Beobachtung). Stärke wie die offene Suche, Feder [GESCHÄTZT]
    val homeReturn = iosSpring("M-HOME-RETURN", "App schließen: scharf werden", response = 0.45f, dampingFraction = 1.0f)
    // M-APP-OPEN — App öffnen: Homescreen (Android-Wallpaper + Icons) wird unscharf, während die App aus dem Icon wächst [GESCHÄTZT]
    val appOpen = iosSpring("M-APP-OPEN", "App öffnen: unscharf werden", response = 0.35f, dampingFraction = 1.0f)
    val homeReturnBlur = tunable("M-HOME-RETURN.blur", "App schließen: Start-Unschärfe", "Motion", 20f, 0f, 60f, "pt")

    // M-APP-LAUNCH — App öffnen (nur mit Magisk-Modul) [GEMESSEN] recording9 2,77 s (Telefon im Dock, 60 fps, frameweise):
    // Karte von Anfang an deckend, App auf Kartenbreite skaliert, oben angesetzt, unten abgeschnitten. Drei kritisch gedämpfte
    // Federn: senkrechte Mitte schnell (0,15 s, rms 0,011), Breite + waagrechte Mitte 0,26 s (rms 0,007), Höhe 0,30 s (rms 0,015)
    // → Karte hebt sich erst über das Dock, dann Vollbild. (Die leichte Trapez-Verzerrung von iOS ist mit SurfaceControl nicht möglich.)
    val appLaunch = iosSpring("M-APP-LAUNCH", "App öffnen: Breite/waagrecht", response = 0.26f, dampingFraction = 1.0f)
    val appLaunchY = iosSpring("M-APP-LAUNCH-Y", "App öffnen: senkrechte Mitte", response = 0.15f, dampingFraction = 1.0f)
    val appLaunchH = iosSpring("M-APP-LAUNCH-H", "App öffnen: Höhe", response = 0.30f, dampingFraction = 1.0f)

    /** Sorgt dafür, dass das Objekt initialisiert ist und alle Tunables registriert sind. */
    fun touch() = Unit
}
