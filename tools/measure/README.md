# Messskripte (Phase 0)

Python-venv mit numpy, pillow, scipy, opencv-python-headless. Frames vorher extrahieren:

    ffmpeg -i docs/reference/iphone/recording1.mp4 -vf scale=402:874 -fps_mode passthrough -frame_pts 1 fr/f_%06d.png   # 1 px = 1 pt, Dateinummer/60 = Sekunde

- `corner.py` — Eckenradius-Fit (continuous corner vs. Kreis)
- `track.py` + `t_page.py` / `fit_page.py` — Seiten-Offset per Kreuzkorrelation + Feder-Fit
- `t_scale.py` + `fit_f.py` — Ordner-Skalierung per Template-Matching + Feder-Fit
- `t_kb.py` — Tastatur/Suchfeld-Offset
- `wiggle.py` + `sinfit.py` — Wackel-Rotation/-Versatz (volle Auflösung) + Sinus-Fit
- `tap.py` — Abdunkeln beim Antippen, `pill.py` — Pill-Zustand, `springfit.py` — gedämpfter Oszillator-Fit

Pfade in den Skripten zeigen noch auf das Scratchpad der Phase-0-Session; vor erneuter Nutzung anpassen.
