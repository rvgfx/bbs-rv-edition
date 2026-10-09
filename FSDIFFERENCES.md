# BBS RV Edition — What's Different from BBS FS?

BBS RV Edition is a personal fork of BBS FS that merges features from BBS CML Edition with new additions. It exists because I needed functionality from both branches without maintaining two separate installations.

---

## Features Added

### Ported from BBS CML Edition
- **Trigger / Region Blocks** — block-based triggers for playback and region detection
- **Hotbar Clip** — animates the player hotbar slot on the film timeline
- **Color Grade Clip** — keyframed overlay color, saturation, hue, brightness, contrast and lift/gamma/gain per RGB channel
- **Cinematic Effects Clip** — keyframed chromatic aberration, VHS, vintage film, radial blur, lens dirt & rain, film dust, light leak, heat waves and fisheye
- **Grain Clip** — keyframed film grain strength and size

### Ported from Blockbuster
- **Playback Button** — in-world button block that triggers film playback

### Inspired by BBS Lezy
- **Video Codec & Hardware Encoder Settings** — choose H.264, H.265/HEVC or AV1 and encode on the GPU (NVIDIA NVENC, AMD AMF, Intel QSV) or the CPU, with a constant-quality setting. Auto mode does a tiny test encode to check that an encoder actually works before using it and falls back to the CPU if none does. Based on BBS Lezy's encoder settings and probe; the GPU-vendor-ordered fallback and AV1 are RV additions

### New in RV Edition
- **Dynamic `play_state` Distance Range** — the model block activation radius is now a configurable setting instead of a hardcoded command argument
- **Asynchronous Video Writer** — frames are handed to ffmpeg on a separate thread through a small bounded queue, so the render thread no longer stalls on the pipe and memory stays flat on long exports
- **GPU Color Conversion** — frames are converted to YUV 4:2:0 (BT.709) and flipped in a shader before read-back, halving the data sent to ffmpeg and removing its CPU-side conversion

### Now Default in BBS FS (no longer exclusive)
- **Show Disabled Bones in Model Editor** — was added upstream; no longer a RV-specific feature
- **Timeline Markers** — BBS FS has its own film markers now; RV uses those instead of its own implementation
- **Export Video with Minecraft Audio** — BBS FS now captures and mixes Minecraft sounds into the export natively; RV's loopback capture (from CML) was removed in its favor

---

## Credits
- **BBS CML Edition** and **Blockbuster** — for the ported features listed above
- **BBS Lezy** by NotLeji (MIT License) — the video codec / hardware encoder settings and the encoder probe were based on its implementation
