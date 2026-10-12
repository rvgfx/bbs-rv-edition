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
- **Per-Axis Camera Shake** — the Shake clip can give every component (X, Y, Z, yaw, pitch, roll, FOV) its own amplitude and frequency in Hz, shake the position in camera space (right, up, forward) instead of world axes, and take a seed so two shakes don't move the same way. Noise can be layered over several octaves with a roughness control for a more organic shake, a decay fades it out from the start of the clip for hits and explosions, and presets (Handheld, Explosion, Earthquake, Vehicle) fill everything in with one click. A graph in the panel previews the shake over the whole clip, one line per component, so it can be tuned without playing the film. With per axis off and the new settings at their defaults it behaves exactly as before
- **Trackside Cameras** — found under Camera rig... in the timeline's Add menu; procedurally places racing-game-style fixed cameras along a replay's path: pick the replay, a tick range, ranges for the number of cameras, distance from the path and height above the ground, and a uniformity slider (even durations and alternating sides at 1, random at 0). Cameras are kept out of blocks, placed where the target stays visible the most, and avoid repeating the previous camera's angle. Before anything is added, a viewport preview shows the target's path, the numbered camera sequence and a translate gizmo to move each camera; Generate turns them into Idle clips with a Look clip on top aiming at the replay
- **Marker Durations** — film markers can have a duration in ticks, drawn as a band on the timeline ruler so the exact length of a section is visible at a glance. The band's end line can be dragged on the ruler to change the duration (a marker sitting on it still takes the click first), and dragged clips snap to the end of the band as well as the marker. Brings back the duration band from RV's old marker implementation on top of BBS FS's markers
- **Export Marker Range** — right-clicking a marker with a duration offers to export just that section of the film, from the marker to the end of its band, without touching the editor's loop
- **Camera Rigs** — bakes a classic camera move around a replay into a single keyframe clip that always aims at it: Drone orbit (circles the target by a set number of degrees), Crane (a fixed base rising while it follows the target) and Over the shoulder (behind and to the right of the target, looking where it goes, with its heading smoothed so mouse jitter doesn't shake the shot). Distance, start/end height and tick range are set in a panel, and a viewport preview shows the target's path, the camera's path and where it aims before Generate adds the clip; every keyframe can be tweaked afterwards. Lives in the same Camera rig... menu as Trackside Cameras
- **Depth of Field Clip** — a screen effect clip that blurs what is nearer or farther than the focus, with keyframed focus distance, sharp range and strength (blur radius authored at 1080p, so the preview and the export match). Picking a target replay keeps the focus on it, turning the focus keyframes into an offset for focus pulls. Meant for vanilla rendering; Iris shader packs bring their own depth of field
- **Aspect Ratio Guide & Safe Areas** — viewport settings that mask the film preview outside a chosen aspect ratio (2.39:1, 2:1, 1.85:1, 4:3, 1:1, 9:16) and draw the 90% action and 80% title safe areas, for framing shots meant for a later crop. Editor-only, never exported

### Now Default in BBS FS (no longer exclusive)
- **Show Disabled Bones in Model Editor** — was added upstream; no longer a RV-specific feature
- **Timeline Markers** — BBS FS has its own film markers now; RV uses those instead of its own implementation (with durations added back, see above)
- **Export Video with Minecraft Audio** — BBS FS now captures and mixes Minecraft sounds into the export natively; RV's loopback capture (from CML) was removed in its favor

---

## Credits
- **BBS CML Edition** and **Blockbuster** — for the ported features listed above
- **BBS Lezy** by NotLeji (MIT License) — the video codec / hardware encoder settings and the encoder probe were based on its implementation
