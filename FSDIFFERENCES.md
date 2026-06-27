# BBS RV Edition — What's Different from BBS FS?

BBS RV Edition is a personal fork of BBS FS that merges features from BBS CML Edition with new additions. It exists because I needed functionality from both branches without maintaining two separate installations.

---

## Features Added

### Ported from BBS CML Edition
- **Trigger / Region Blocks** — block-based triggers for playback and region detection
- **Export Video with Minecraft Audio** — captures and mixes in-game audio during export
- **Hotbar Clip** — animates the player hotbar slot on the film timeline
- **Ambient Audio Capture** — records the Minecraft audio output via a virtual loopback device (`SoundEngineMixin`)

### Ported from Blockbuster
- **Playback Button** — in-world button block that triggers film playback

### New in RV Edition
- **Timeline Markers** — named, colored markers on the film timeline with optional duration ranges; support snapping, tooltips, and per-film persistence
- **Dynamic `play_state` Distance Range** — the model block activation radius is now a configurable setting instead of a hardcoded command argument

### Now Default in BBS FS (no longer exclusive)
- **Show Disabled Bones in Model Editor** — was added upstream; no longer a RV-specific feature