# Architecture decision record — Studio Mobile v2

Date: 2026-10-15  
Status: proposed foundation, implementation in progress.

## Context
The earlier prototype serialized a project into a self-contained HTML document and failed to reopen a saved ~62 MB project. The new version must separate editable project data, rendering and delivery formats.

## Decisions
1. **Project file is a ZIP container with `.studio` extension.** ZIP is ubiquitous, streamable and inspectable. `manifest.json` describes the project; original bytes live under `media/`.
2. **Manifest is independent from UI.** Canvas coordinates are normalized (0..1) and canvas dimensions are explicit, which allows different screen sizes and future desktop UI.
3. **Media imported through Android Storage Access Framework.** No broad storage permission; user explicitly selects files.
4. **SHA-256 per imported media.** Duplicate detection works inside the current project in this foundation. A future global media library can use the same digest as its content key.
5. **Lazy resource principle.** Project open extracts media to cache and validates hashes. UI should only decode images currently needed. Current prototype still decodes visible images on draw and must be optimized before large projects.
6. **Separate project, interactive presentation and video export.** `.studio` is editable source; HTML/ZIP is a viewer package; MP4 is a rendered result. These are not interchangeable.
7. **Modular direction.** Keep `ProjectDocument` and `ProjectArchive` separate from `MainActivity`; future modules can add geometry, labels, history, desktop adapters and exporters.

## Why not HTML as the save format?
HTML is a delivery/rendering format with browser security, memory and size constraints. It couples document state to UI code and makes schema validation/migration awkward. The previous invalid-file report supports moving persistence to a versioned data format.

## Video export decision
Do not bundle FFmpeg WebAssembly as if that alone were an export engine. Android Media3 Transformer is a strong candidate for basic media transforms and image-to-video clips; custom composition of a freeform canvas with sequential video activation needs a render/composition layer and validation. Official docs describe Transformer APIs and progress callbacks. For arbitrary overlay/layout sequencing, benchmark Media3/MediaCodec plus OpenGL against a maintained FFmpeg option before committing. The old FFmpegKit upstream is retired; community continuations exist but need license/security review.

## Risks
- ZIP bombs / path traversal: reject absolute and `..` paths; cap manifest size. A future release should enforce total uncompressed size, entry count and compression ratio limits.
- Cache eviction: project file remains canonical; opened media in cache can be rebuilt.
- Atomic save: SAF destination writes may be interrupted. Future improvement: write temp archive, validate, then replace where provider permits.
- Memory: avoid decoding full-resolution bitmaps per frame; implement thumbnail cache and viewport culling.
- Integrity: hashes verify extracted media; future format should include manifest hash/signature if required.
- Version migration: schemaVersion currently accepts only v1; migration framework still needed.
- Testing: this source package has not been compiled on this runtime; rely on GitHub Actions result and device smoke tests.

## References
- Android Storage Access Framework: https://developer.android.com/guide/topics/providers/document-provider
- Media3 Transformer: https://developer.android.com/media/media3/transformer
- Media3 Transformer getting started: https://developer.android.com/media/media3/transformer/getting-started
- Media3 stable release notes: https://developer.android.com/jetpack/androidx/releases/media3
- FFmpegKit original retirement / continuation info: https://github.com/arthenica/ffmpeg-kit
