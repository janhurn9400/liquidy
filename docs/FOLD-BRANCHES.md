# Auf- vs. zugeklappt: wo der Code unterscheidet (Stand 2026-10-15)

Ziel (Nutzer): gleiche Logik in beiden Zuständen, Unterschiede nur beim **Layout** — außer dort, wo der Nutzer eine
Eigenheit ausdrücklich will bzw. sie aus den Duo-Aufnahmen stammt.

Es gibt drei Modi: **iPhone-Stil** (kein Duo), **Duo zugeklappt**, **Duo aufgeklappt**. Die ~100 `geo.duo`-Stellen sind
großteils „Duo vs. iPhone“ — zwei verschiedene gemessene Vorlagen (iPhone-Videos vs. Duo-Videos), das ist gewollt.
Diese Liste betrifft nur die Unterscheidung **innerhalb des Duo-Stils**: `geo.unfolded` / `screenIsWide`.

## A. Reines Layout (unbedenklich)

| Stelle | Was |
|---|---|
| HomeGeometry (ganz) | Raster, Spaltenabstand, Dock-Spalte, Suchfeld-Maße je Zustand |
| HomeScreen 116, 143 | aufgeklappt zwei Seiten sichtbar → Seitenzählung um 1 verschoben |
| HomeScreen 266 | Ziehen: Seite unter dem Finger (aufgeklappt links/rechts) |
| HomeScreen 872, PageOverview 151 | Seitenübersicht: rechte Seite zoomt aus der rechten Karte |
| HomeScreen 1484–1487 | Suchvorschläge: Spaltenbreite/-position |
| TodayPage 50, 76–78, 276 | Heute-Seite: Breite, Abstand unten |
| DuoChrome 60 | Seitenpunkte unter der rechten Seite |
| HomeScreen 1285–1290 | Widget von Heute auf den Homescreen ziehen: aufgeklappt ab der Homescreen-Seite daneben, zugeklappt am Bildschirmrand + Blättern (Homescreen ist dort nicht sichtbar) |
| Folder 180 | Ordner-Panel über der Hälfte, in der der Ordner liegt |

## B. Verhalten — aus Duo-Aufnahmen gemessen (bewusst so, bitte bestätigen)

| Stelle | Aufgeklappt | Zugeklappt | Quelle im Code |
|---|---|---|---|
| HomeScreen 150, 830; TodayPage 89 | Heute-Seite verwischt nur ihre eigene Hälfte (Glasfläche dahinter), Wallpaper sonst scharf | ganzer Bildschirm unscharf wie iPhone | Aufnahme 10-08 21:28, 31 s / 19:18 |
| HomeScreen 682 | Seite links neben der Mediathek wird unscharf | Mediathek füllt den Bildschirm | Aufnahme 35,5 s |
| HomeScreen 231, 747 | Dock gleitet beim Herunterziehen zur Suche sofort mit dem Finger hinaus | Dock erst verzögert hinaus | „Duo, Nutzer 10-11“ / Video 5 |
| HomeScreen 1255 | Heute-Spalte: nach unten ziehen scrollt (Gummiband) | ganz oben nach unten ziehen öffnet die Suche (wie iPhone) | Aufnahme 14:57 |
| HomeScreen 472, 488, 502 | Lupe immer antippbar | Lupe auf der Heute-Seite nicht antippbar | folgt aus „Heute füllt den Schirm“ |
| HomeScreen 196–202 | — | Zuklappen: Außendisplay erscheint gedimmt + unscharf, wird dann scharf | Aufnahme 10-08 21:29, 62–64 s |

## C. Android-bedingt (kein App-Verhalten)

| Stelle | Was |
|---|---|
| WallpaperCropModel | Android zoomt das Wallpaper zugeklappt nicht (gemessen 10-14) |

## Vorschlag

- A bleibt.
- B: alle Unterschiede haben eine Quelle (Duo-Aufnahme) bzw. folgen direkt daraus — bitte bestätigen, dass sie so bleiben
  sollen. Keine Stelle ohne Begründung gefunden.
- Neue Unterscheidungen nur noch mit Quelle (Video/Nutzerwunsch) im Kommentar.
