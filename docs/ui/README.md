# UI preview

The PNGs here are drawn by `tools/render_layouts.py` from the real
`res/layout`, `res/values` and `res/drawable` files, so they track the app
instead of drifting away from it:

| file | screen |
| --- | --- |
| `01-splash.png` | startup gate (lib check / update) |
| `02-login.png` | sign in |
| `02b-verify.png` | sign in with a rejected key (inline error) |
| `02c-dialog.png` | the "checking your key" dialog |
| `03-main.png` | loader, key good, game data ready |
| `04-file-access.png` | loader asking for all-files access before the OBB copy |
| `05-license-failed.png` | loader after the panel rejected the key |

`overview.png` is the six of them side by side.

Regenerate with:

    python3 tools/render_layouts.py

The renderer implements the parts of Android's layout system that matter for
these screens: weights, `layout_gravity`, margins/padding, styles and type
roles, `letterSpacing`, word wrap, `scaleType`, vector icons and per-screen
state overrides (so a `gone` card can be previewed as visible).

For real hardware, dispatch **UI screenshots (real device)** — it installs the
debug APK on a Pixel 5 emulator and pushes PNGs to the `ui-screenshots` branch.
That job is flaky about half the time (the emulator runner, not the app); when it
fails, re-dispatch.
