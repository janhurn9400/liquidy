# Measurement scripts (phase 0)

Python venv with numpy, pillow, scipy, opencv-python-headless. Extract the frames first:

    ffmpeg -i docs/reference/iphone/recording1.mp4 -vf scale=402:874 -fps_mode passthrough -frame_pts 1 fr/f_%06d.png   # 1 px = 1 pt, file number / 60 = second

- `corner.py` — corner radius fit (continuous corner vs. circle)
- `track.py` + `t_page.py` / `fit_page.py` — page offset via cross-correlation + spring fit
- `t_scale.py` + `fit_f.py` — folder scale via template matching + spring fit
- `t_kb.py` — keyboard/search field offset
- `wiggle.py` + `sinfit.py` — jiggle rotation/offset (full resolution) + sine fit
- `tap.py` — darkening on tap, `pill.py` — pill state, `springfit.py` — damped oscillator fit

Paths in the scripts still point to the scratchpad of the phase-0 session; adjust before reusing them.
