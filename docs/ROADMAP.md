# Roadmap with acceptance criteria

## Milestone 0 — source/build baseline (this package)
- [x] Versioned model and ZIP archive design.
- [x] GitHub Actions workflow for debug APK.
- [ ] Confirm successful CI build from a clean repository.
- [ ] Confirm APK installs on Redmi Note 12 Pro / Android 13.

## Milestone 1 — portable canvas (current functional target)
- [ ] Import at least 5 images and 3 videos without crash.
- [ ] Move items, save `.studio`, force-stop app, reopen project.
- [ ] Copy `.studio` to a second device without source files; reopen and verify SHA-256.
- [ ] Verify names, positions and dimensions.
- [ ] Add tests for corrupt ZIP, missing manifest, missing media, wrong hash, unsupported schema.
- [ ] Fix bitmap decode-on-draw before large projects.

## Milestone 2 — viewer mode
- [ ] Tap a video entity to play it; stop/release player when off-screen or deselected.
- [ ] Pan/zoom canvas; viewport culling and bounded thumbnail cache.
- [ ] Explicit loading/error/progress state for each long operation.

## Milestone 3 — export
- [ ] Export a still-canvas MP4 test with two images.
- [ ] Add ordered video activation with configurable duration/transition policy.
- [ ] Preserve aspect ratios, positions and audio policy.
- [ ] Export settings: resolution, frame rate, bitrate/quality.
- [ ] Verify output with playback on a second device and frame-level comparison.
- [ ] Interactive HTML ZIP export and browser compatibility matrix.

## Milestone 4 — project management
- [ ] Save As, named snapshots and restore.
- [ ] Global media library keyed by SHA-256.
- [ ] Labels, filters and project metadata.
- [ ] Version diff/merge with explicit conflict UI.

## Milestone 5 — cross-platform
- [ ] Desktop UI adapter using the same schema.
- [ ] Portable `.studio` fixtures shared across Android and desktop tests.
- [ ] 3D geometry adapter with time/spatial relations.
- [ ] Optional online collaboration and synchronization after local-first model is stable.
