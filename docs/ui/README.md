# UI preview

`01-splash.png`, `02-login.png`, `03-main.png` are regenerated with

    python3 tools/render_layouts.py

The tool reads the real `res/layout`, `res/values` and `res/drawable` files, so
the previews track the layouts instead of drifting away from them. For captures
from a real device, dispatch **UI screenshots (real device)** — it installs the
built APK on a Pixel 5 emulator and pushes the PNGs to the `ui-screenshots`
branch.
