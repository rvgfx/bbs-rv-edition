# BBS RV Edition — What's Different from BBS FS?

BBS RV Edition is a personal fork of BBS FS that merges features from BBS CML Edition with new additions. It exists because I needed functionality from both branches without maintaining two separate installations.

---

## Features Added

### Ported from BBS CML Edition
- **Trigger / Region Blocks** — block-based triggers for playback and region detection
- **Export Video with Minecraft Audio** — captures and mixes in-game audio during export
- **Hotbar Clip** — animates the player hotbar slot on the film timeline
- **Ambient Audio Capture** — records the Minecraft audio output via a virtual loopback device (`SoundEngineMixin`)
- **Color Grade Clip** — keyframed overlay color, saturation, hue, brightness, contrast and lift/gamma/gain per RGB channel
- **Cinematic Effects Clip** — keyframed chromatic aberration, VHS, vintage film, radial blur, lens dirt & rain, film dust, light leak, heat waves and fisheye
- **Grain Clip** — keyframed film grain strength and size

### Ported from Blockbuster
- **Playback Button** — in-world button block that triggers film playback

### New in RV Edition
- **Dynamic `play_state` Distance Range** — the model block activation radius is now a configurable setting instead of a hardcoded command argument

### Now Default in BBS FS (no longer exclusive)
- **Show Disabled Bones in Model Editor** — was added upstream; no longer a RV-specific feature
- **Timeline Markers** — BBS FS has its own film markers now; RV uses those instead of its own implementation
