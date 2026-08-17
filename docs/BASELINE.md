# Emulator baseline (architecture redesign Phase 0)

Recorded 2026-08-16 before any redesign change, on the Apple Silicon
development machine.

## Environment

- Java: OpenJDK 26.0.1 (Zulu 26.30+11-CA)
- OS: Mac OS X aarch64 (Darwin 24.6.0, Apple M1)
- Commit: `24c770e document emulator architecture redesign`
- Workload ROM: `local-roms/smw.sfc`
  (SHA-256 `0838e531fe22c077528febe14cb3ff7c492f1f5fa8de354192bdff7137c27f5b`, not committed)

## Unpaced throughput

Runner: `EmulatorBenchmarkTest` (opt-in, test frontend, no vsync/audio pacing).

```
mvn test -Dtest=EmulatorBenchmarkTest -Djamsnes.benchmark.rom=local-roms/smw.sfc
```

| Metric | Value |
|---|---|
| Warmup updates | 400,000 |
| Measured updates | 1,000,000 |
| Wall time | 2.001 s |
| Updates per second | 499,723 |
| Emulated frames | 261 |
| Emulated fps | 130.4 |
| Real-time ratio | 2.17x |
| Allocated during measurement | 175,428,776 bytes (672,141 bytes/frame) |
| GC collections during measurement | 1 |
| Retained heap after GC | 71,035,824 bytes |

## Golden smoke frame

Runner: `LocalRomSmokeTest`, 500,000 updates, default TestFrontend surface.

Canonical surface at baseline: 1024x1024 `int` RGBA8888 frame buffer
(`Background.BUFFER_SIZE` square), CRC computed over all pixels in row-major
order, each pixel emitted as four bytes R, G, B, A (most significant byte
first). Only the top-left 512x448 region carries display output (each visible
scanline doubled vertically, each low-resolution pixel doubled horizontally);
the remainder is untouched zeros but is included in the CRC.

```
mvn test -Dtest=LocalRomSmokeTest \
    -Djamsnes.smoke.rom=local-roms/smw.sfc \
    -Djamsnes.smoke.frameCrc32=0xb9c231a7
```

| Metric | Value |
|---|---|
| Frame CRC32 (full 1024x1024 RGBA surface) | `0xb9c231a7` |
| Displayed geometry | 512x448 (224 lines doubled, 256 samples doubled) |

The CRC is only meaningful together with the geometry above; it must be
re-derived (with an explanation) whenever the canonical surface changes.

**Phase 1 surface redefinition:** the canonical surface became the visible
region only — 512x448 RGBA8888 pixels at stride 512 (`VideoFrame`), same byte
order. The displayed pixels are bit-identical to baseline; only the untouched
zero padding left the CRC. Golden value for the same workload: `0x71839150`
(verified equal to the baseline frame's 512x448 sub-region CRC before and
after the port split).

## Test suite at baseline

`mvn clean test`: 819 tests, 0 failures, 1 skipped (local ROM smoke without
ROM property).
