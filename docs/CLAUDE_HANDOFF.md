# Handoff dossier for Claude — Studio Mobile v2

## Product vision
Studio Mobile is a freeform, interactive media canvas. It is not a conventional linear video editor. Users place image/video/file entities freely in a large 2D workspace. In interactive viewer mode, users click entities to activate/play them. In video export mode, the canvas is rendered as a video and video entities activate in a user-defined sequence; still images remain visible according to project rules. Longer term: temporal/oscillating geometry, radial opening sector, 3D entities and relations, desktop UI, labels, reusable media library, version history and collaboration.

## Immediate goal
Get a reliable Android first version for starting a real project:
1. Import images/videos.
2. Position them freely on canvas.
3. Save a single portable `.studio` file embedding original media.
4. Reopen on another device with no source media.
5. Then implement correct MP4 export and interactive HTML ZIP export.

## Current source package status
This is a foundation, not a completed app. It includes a native Android shell, freeform canvas, import, `.studio` ZIP save/open, manifest validation, SHA-256 media checks and GitHub Actions APK build. Video playback, true video composition/export, HTML viewer, desktop support, full history and global library are not implemented. The app must be treated as experimental until CI and device tests pass.

## Key technical decisions
- Android native Kotlin for storage and initial device integration.
- `ProjectDocument` is separate from UI; normalized canvas coordinates.
- `.studio` = ZIP archive with `manifest.json` and `media/`.
- SHA-256 is used for duplicate detection and integrity.
- SAF for import/open/save, avoiding broad storage permissions.
- Explicit progress/status text during operations.
- Exporter must be separate from project persistence.
- Keep schema versioned and device-independent for desktop reuse.

## Critical review points / likely fixes
1. Check Kotlin compilation first. Fix any compile issues in GitHub Actions before feature work.
2. Current image rendering decodes full bitmap on every `onDraw`: this is intentionally a prototype shortcut and must be replaced with cached downsampled thumbnails immediately.
3. `ProjectArchive.open` copies archive entries to cache and validates hashes, but needs total uncompressed size / entry-count / compression-ratio limits against malicious or accidental huge archives.
4. Add cancellation and real byte-based progress for project open; current open progress is approximate.
5. Ensure all UI state mutations happen on main thread. Current import worker reads `doc.items` while UI could theoretically interact; serialize operations or use immutable snapshots.
6. Rework media mapping on Save/Open: file path keys and IDs must remain aligned; add unit tests.
7. For reliable large projects, add temp-file save/validation and provider-safe atomic replacement.
8. Add an `AssetStore` abstraction before global dedup library; reference assets by hash and retain originals.
9. Define precise video semantics before export: background, layer ordering, visibility, whether clips loop, clip duration, audio mixing, end condition, transitions and whether videos play sequentially or in parallel.
10. Research/benchmark Android Media3 Transformer + OpenGL/MediaCodec versus FFmpeg-based pipeline. Media3 Transformer supports image-to-video, effects, conversion and progress; it does not magically implement our full freeform scene semantics by itself.
11. Add unit tests for manifest parse, corrupt archives, missing files, hash mismatch, schema mismatch and normalized coordinate round trips.
12. Make project open non-destructive: parse/validate into a temporary document, only replace the currently open project after full validation succeeds.

## Build / install instructions
See README.md. GitHub Actions artifact is named `StudioMobile-debug-apk`. It expires after 14 days, so keep source repository as the canonical record and download build artifacts promptly.

## Questions for reviewer
- Is native Android appropriate for the first milestone while keeping project schema platform-neutral?
- Should export use Media3 Transformer + custom OpenGL compositor or FFmpeg-based frame composition?
- What storage limits and progressive loading strategy should be set for large projects?
- What archive safety checks are mandatory?
- What test fixture suite will protect schema compatibility across Android and desktop?
