# Unfolded vs. folded: where the code differs (as of 2026-10-15)

Goal (user): the same logic in both states, differences only in **layout** — except where the user explicitly wants a
quirk or it comes from the Duo recordings.

There are three modes: **iPhone style** (not Duo), **Duo folded**, **Duo unfolded**. Most of the ~100 `geo.duo` branches are
"Duo vs. iPhone" — two different measured references (iPhone recordings vs. Duo recordings), which is intended.
This list only covers the distinction **within the Duo style**: `geo.unfolded` / `screenIsWide`.

## A. Pure layout (unproblematic)

| Location | What |
|---|---|
| HomeGeometry (whole) | grid, column pitch, dock column, search field dimensions per state |
| HomeScreen 116, 143 | unfolded two pages are visible → page count shifted by 1 |
| HomeScreen 266 | dragging: page under the finger (unfolded left/right) |
| HomeScreen 872, PageOverview 151 | page overview: the right page zooms out of the right card |
| HomeScreen 1484–1487 | search suggestions: column width/position |
| TodayPage 50, 76–78, 276 | Today page: width, bottom spacing |
| DuoChrome 60 | page dots below the right page |
| HomeScreen 1285–1290 | dragging a widget from Today to the home screen: unfolded from the home page next to it, folded at the screen edge + paging (the home screen isn't visible there) |
| Folder 180 | folder panel above the half the folder is in |

## B. Behaviour — measured from Duo recordings (intended, please confirm)

| Location | Unfolded | Folded | Source in code |
|---|---|---|---|
| HomeScreen 150, 830; TodayPage 89 | Today page blurs only its own half (glass surface behind it), wallpaper otherwise sharp | whole screen blurred like iPhone | recording 10-08 21:28, 31 s / 19:18 |
| HomeScreen 682 | the page left of the App Library blurs | App Library fills the screen | recording 35.5 s |
| HomeScreen 231, 747 | dock slides out immediately with the finger when pulling down to search | dock slides out delayed | "Duo, user 10-11" / video 5 |
| HomeScreen 1255 | Today column: pulling down scrolls (rubber band) | pulling down at the very top opens search (like iPhone) | recording 14:57 |
| HomeScreen 472, 488, 502 | magnifier always tappable | magnifier not tappable on the Today page | follows from "Today fills the screen" |
| HomeScreen 196–202 | — | folding: outer display appears dimmed + blurred, then becomes sharp | recording 10-08 21:29, 62–64 s |

## C. Caused by Android (not app behaviour)

| Location | What |
|---|---|
| WallpaperCropModel | Android doesn't zoom the wallpaper on the folded display (measured 10-14) |

## Proposal

- A stays.
- B: all differences have a source (Duo recording) or follow directly from one — please confirm they should stay.
  No branch without a reason was found.
- New distinctions only with a source (video/user request) in the comment.
