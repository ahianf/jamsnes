# JamSNES Architecture Redesign

## Implementation contract for Fable 5

This document is a normative implementation plan, not a brainstorming note. Fable 5 shall evolve the current Java port into a small, deterministic, hardware-shaped SNES emulator with an efficient LWJGL desktop frontend. It shall make the changes incrementally, retain verified behavior after every commit, and stop treating a logical background address space as a presentation framebuffer.

The words **MUST**, **MUST NOT**, **SHOULD**, and **MAY** have their usual requirements meaning.

## Mission

Produce an architecture that is:

1. Correct enough to preserve and extend the existing CPU, PPU, APU, DMA/HDMA, cartridge, and timing tests.
2. Easy to compare with hardware documentation and the C++ reference, when the reference sources are available.
3. Allocation-free in steady-state emulation hot paths.
4. Cache-friendly: primitive, flat, bounded buffers instead of large Java object graphs.
5. Deterministic: core results depend only on ROM, reset state, and timestamped input—not wall-clock time, host audio timing, or rendering speed.
6. Frontend-independent without reintroducing selectable production renderers. LWJGL remains the only shipped frontend; small ports exist solely so the core can be tested.
7. Understandable to a learner: component boundaries represent hardware responsibilities and every timing unit is named explicitly.

This is not permission for a big-bang rewrite. Each phase below is a separate, reviewable behavior-preserving unit unless that phase explicitly introduces new hardware behavior.

## Required working discipline

Before every phase:

1. Inspect `git status --short`, the relevant Java classes, tests, and recent commits.
2. Preserve all user changes and untracked files. Do not edit `README.md` unless the user separately requests it.
3. Look for the C++ reference under `sources`. The directory is absent in the current checkout. If it remains absent, do not invent claims about C++ parity; rely on existing tests and documented hardware behavior.
4. Record a baseline before changing performance-sensitive code.

For every meaningful phase:

1. Add or update focused tests first or in the same commit.
2. Run the focused tests while iterating.
3. Run `mvn clean test` before committing.
4. Run the curated local Super Mario World smoke test when PPU, scheduling, memory, or frontend behavior changes. Never commit the ROM.
5. Commit locally with a clear message. Do not push.
6. Do not combine unrelated cleanup with a behavioral change.

Never silence a failing golden test by replacing its expected value until the exact pixel-level reason for the change is understood and documented.

## Current design: what is valid and what is not

### Why 1024 exists

The number 1024 is not itself arbitrary in an SNES PPU:

- A background tilemap can address as many as 64 by 64 entries.
- A background character can be 8 by 8 or 16 by 16 pixels.
- Therefore a logical scrolling background can span as much as 1024 by 1024 pixels.
- Mode 7 also addresses a logical 1024 by 1024 coordinate space.

Those are **logical coordinates backed by VRAM tilemap and character data**. They do not justify allocating or clearing a 1024 by 1024 raster for each background, main screen, subscreen, priority plane, source plane, or output frame.

The current implementation conflates three different concepts:

1. Background address space: potentially 1024 by 1024 logical pixels.
2. PPU composition width: at most 512 horizontal color samples.
3. Presented frame: 224 or 239 visible lines when non-interlaced, doubled to 448 or 478 when interlaced.

The redesign MUST keep these concepts separate.

### Concrete current costs

The seven 1024 by 1024 `int[][]` arrays in `PPU` contain at least 28 MiB of primitive payload before row-object overhead. The four backgrounds add approximately 24 MiB for their full `int` color and `short` descriptor rasters. These arrays also produce poor locality because Java two-dimensional arrays are arrays of separately allocated row objects.

The cost is not only memory. The current model encourages full-map rendering, broad clearing, complicated invalidation, and per-frame merging of regions that the television never displays.

### Parts worth retaining

The redesign SHOULD retain and improve these ideas:

- `SNES` as the machine composition root.
- Separate CPU, PPU, APU/DSP, DMA, joypad, cartridge, and memory-map domains.
- Explicit master-clock accounting and rational PPU/APU clock conversion.
- A precomputed memory-bus lookup instead of scanning every mapped component on every access.
- Deterministic synthetic-ROM integration tests and local ROM frame hashes.
- Primitive register and RAM storage.
- One shipped LWJGL frontend.

## Target ownership model

Dependencies MUST point from the desktop application toward the core, never from the core toward LWJGL.

```text
Main
  └─ DesktopApplication
      ├─ SnesMachine (the deterministic core)
      │   ├─ MasterClockScheduler
      │   ├─ Cpu65816 ── TimedCpuBus ── MemoryBus
      │   ├─ Ppu ── VideoSink
      │   ├─ Apu/Spc700 ── Dsp ── AudioSink
      │   ├─ DmaController
      │   ├─ Cartridge
      │   └─ Joypad ports
      ├─ LwjglWindow/Input
      ├─ OpenGlVideoSink
      └─ OpenAlAudioSink
```

The exact class renames MAY be deferred to avoid noisy commits. The dependency direction and responsibilities are mandatory.

### Core output ports

Replace the current `IRenderer`, which mixes window lifecycle, video, audio, input ownership, and the emulation loop, with two narrow synchronous ports:

```java
public interface VideoSink {
    void present(VideoFrame frame);
}

public interface AudioSink {
    void write(short[] interleavedStereo, int offset, int sampleCount);
}
```

Contracts:

- Calls are synchronous in the initial design.
- The producer owns and reuses the supplied arrays after the method returns.
- A sink that needs the data later MUST copy it into storage it owns.
- Core packages MUST NOT import `org.lwjgl`.
- Neither port may expose `SNES`, a window, GLFW, OpenGL, or OpenAL.
- The core MUST continue to run with test sinks, but no null/no-op production frontend may be shipped.

`DesktopApplication` owns window creation, input callbacks, the run loop, cleanup, and error reporting. `Main` only validates the ROM argument and starts that application.

### Input

Joypad state belongs to the core. GLFW callbacks belong to the desktop frontend. With the initial single-threaded frontend, callbacks MAY update a small queued input state consumed before the next emulation slice. Input MUST be applied at a deterministic emulated boundary and MUST NOT be read from GLFW inside CPU or PPU code.

Do not introduce multi-threading merely to make the diagram look sophisticated. A single emulation/GLFW thread is preferred until profiling proves that another thread is necessary. macOS first-thread constraints must remain satisfied.

## Exact video model

### Frame geometry

The PPU needs a maximum of 512 horizontal samples. A complete NTSC visible image has either 224 or 239 active lines; interlace weaves two fields into 448 or 478 lines. Therefore the maximum reusable surface is:

```java
public final class VideoFrame {
    public static final int STRIDE = 512;
    public static final int MAX_HEIGHT = 478;
    public static final int MAX_PIXELS = STRIDE * MAX_HEIGHT;

    private final int[] rgba8888 = new int[MAX_PIXELS];
    private int visibleHeight;       // 224, 239, 448, or 478
    private boolean interlaced;
    private boolean overscan;
    private long frameNumber;

    // Documented zero-copy view. A sink may read it only during present().
    public int[] pixels() { return rgba8888; }
    public int stride() { return STRIDE; }
    public int visibleHeight() { return visibleHeight; }
}
```

This is 978,944 bytes of pixel payload: less than 1 MiB. Even two surfaces remain under 2 MiB.

The canonical stride SHOULD remain 512 because an individual frame can contain lines that use different horizontal modes. Low-resolution 256-pixel lines write each output pixel into two adjacent samples. High-resolution and pseudo-high-resolution lines write 512 distinct samples. This keeps line switching deterministic without reallocating or attaching a width to every line.

Non-interlaced output MUST NOT be vertically doubled merely to fit a texture. It uses 224 or 239 rows. Interlaced output uses 448 or 478 rows and tracks field parity explicitly.

The geometry rules MUST have focused tests for:

| Mode | Canonical samples | Active rows |
|---|---:|---:|
| Low resolution, normal | 512 (duplicated pairs) | 224 |
| High/pseudo-high resolution, normal | 512 | 224 |
| Low resolution, overscan | 512 (duplicated pairs) | 239 |
| High/pseudo-high resolution, overscan | 512 | 239 |
| Interlace, normal | 512 | 448 |
| Interlace, overscan | 512 | 478 |

Before locking these values into public API, verify them against the project's hardware reference and tests. The implementation MUST represent the complete 239-line overscan image; the current centered 224-line overscan crop is not the final target.

### Pixel representation

PPU composition SHOULD remain in native 15-bit SNES color plus metadata until the final brightness/output step. Do not repeatedly convert layers to RGBA.

Use flat primitive line buffers. A practical initial layout is:

```text
mainColor15[512]       subColor15[512]
mainPriority[512]      subPriority[512]
mainSource[512]        subSource[512]
mainPalette[512]       subPalette[512]
windowMaskWords[8]     claimedObjectWords[8]
```

Metadata MAY be packed into one or two `int[]` arrays if profiling shows a benefit, but clarity comes first. There MUST be no per-pixel object allocation.

After main/sub selection, color math, brightness, forced blank, and pseudo-hires handling, convert once into the reusable `VideoFrame.rgba8888` array.

### Scanline composition

The final PPU MUST compose only the visible scanline currently being produced. It MUST NOT construct full background rasters or full 1024-square main/sub screens.

For each visible scanline:

1. Latch the primitive register state required for that line at the correct emulated boundary.
2. Clear only the 512-entry line buffers.
3. Resolve the backdrop/fixed color.
4. Sample enabled backgrounds directly from VRAM into the line buffers.
5. Evaluate and draw at most the hardware-visible object/sliver limits.
6. Apply windows using reusable bit masks.
7. Resolve main/sub priorities and color math.
8. Write final pixels into the appropriate output row and field parity.

The first safe migration may still use the current captured scanline states and render at VBlank. The end state SHOULD render a line when emulation reaches its render/fetch completion boundary so mid-frame effects are naturally ordered. Moving to scanline rendering must not discard the current CGRAM/OAM/VRAM active-display side effects.

### Background sampling

Replace `Background.buffer`, `pixelDescriptors`, `tilesPriority`, and full-map invalidation with an on-demand sampler:

```java
int sampleBackgroundPixel(
        int background,
        int screenX,
        int scanline,
        PpuLineState state);
```

The returned `int` is a documented packed pixel descriptor containing transparency, native color/palette index, priority, and any source bit needed by color math. It MUST NOT be a newly allocated result object.

The sampler performs these operations with integer arithmetic:

1. Apply mosaic and offset-per-tile rules.
2. Add horizontal and vertical scroll.
3. Wrap in the logical background width and height.
4. Select the correct 32-by-32 tilemap screen and tilemap entry.
5. Resolve character size and X/Y flips.
6. Decode only the required 8-pixel tile row from VRAM.
7. Return transparency, palette index/color, and priority.

Mode 7 gets a separate sampler because its affine coordinate and wrapping rules differ. Its logical 1024-square coordinates remain valid, but it also MUST sample VRAM rather than materialize a 1024-square color image.

### Tile-row cache

Start with a clear allocation-free tile-row decoder. Add a cache only after measuring it.

If needed, use a fixed-size primitive cache keyed by VRAM address, bit depth, row, and relevant decode mode. One cache value can pack eight 8-bit palette indices into a `long`. Avoid `HashMap`, boxed keys, and one object per tile. A global VRAM generation is acceptable initially; block-level generations MAY be introduced if global invalidation becomes expensive.

The cache stores decoded indices, not colors, so CGRAM writes do not invalidate decoded character data.

### Window masks

Represent a 512-sample mask as eight `long` words, or compute it directly while walking a line. Reuse storage. Do not allocate `boolean[256]`, `boolean[512]`, or `boolean[1024]` for each layer or scanline.

### OpenGL presentation

`OpenGlVideoSink` MUST:

- Allocate one 512-by-478 texture once.
- Allocate reusable native upload storage once.
- Upload only `512 * visibleHeight` pixels.
- Use a bulk `IntBuffer.put(...)` or an equivalent measured bulk copy, not four Java `ByteBuffer.put` calls per pixel.
- Use nearest-neighbor sampling.
- Compute a 4:3 content viewport inside the actual framebuffer and letterbox/pillarbox as needed.
- Keep emulated dimensions separate from host window dimensions and pixel density.
- Reuse framebuffer-size storage; do not allocate `new int[1]` every frame.

The OpenGL code MAY remain compatibility-profile code initially. Replacing immediate-mode drawing with a static VAO/VBO and tiny shader is desirable but lower priority than fixing the PPU data model. Do it as a separate frontend-only commit.

## Audio model

The DSP owns one reusable interleaved stereo batch. When full, it calls `AudioSink.write(buffer, 0, sampleCount)` and reuses the same batch after the call returns. Eliminate `Arrays.copyOf` from the DSP hot path.

`OpenAlAudioSink` owns reusable native PCM staging and a bounded pool of OpenAL buffers. It MUST NOT create a Java `ByteBuffer` for every audio batch. Audio queue depth remains bounded and acts as the real-time pacing source.

Tests MUST cover:

- Exact stereo sample ordering and little-endian conversion.
- Reuse without stale samples.
- Queue underflow restart.
- Processed-buffer reclamation.
- Shutdown after partial initialization.

Host audio timing MUST NOT change the number or values of samples generated by the core.

## Timing and scheduler design

### Goal

Every hardware-visible bus access must occur at a defined master-clock time. PPU/APU/DMA events that happen between CPU accesses must be processed in order. The core must not depend on wall-clock time.

### Master clock

Use SNES master clocks as the canonical scheduler unit. Name variables with their unit: `masterClocks`, `ppuDots`, `apuCycles`, `audioFrames`. Do not use a generic variable named `cycles` across clock domains.

Use integer/rational accumulators for clock conversion. Floating-point time is forbidden in the core.

### Incremental migration

The current scheduler executes a small CPU budget, then advances PPU and APU while explicitly splitting HDMA and DRAM-refresh edges. It is a reasonable compatibility bridge, but it can order a peripheral event later than the CPU bus access that caused or crossed it.

Migrate toward bus-timestamped execution:

1. `SnesMachine.stepInstruction()` asks the CPU to execute one instruction or one resumable block-move/DMA unit.
2. Every timed CPU bus read/write advances the peripheral scheduler by that access's master-clock cost at the access point. The implementation must explicitly define whether each value is sampled or committed at the beginning/end edge of the access and cover that ordering with tests; it must not blindly move every read and write to the same side of elapsed time.
3. CPU internal cycles explicitly call `scheduler.elapseMasterClocks(...)`.
4. The scheduler advances PPU counters, APU fixed-point time, math-unit edges, joypad auto-read, refresh, HDMA, IRQ, and NMI events to the requested target.
5. DMA/HDMA are explicit bus masters. They do not masquerade as ordinary CPU instructions.
6. Interrupt lines are sampled at documented instruction boundaries.

Do not advance the PPU one dot at a time when no event occurs. Advance directly to the next relevant boundary: scanline end, render latch, HBlank/HDMA edge, VBlank/NMI edge, timer compare, refresh, or target time.

Scheduler tests MUST prove event ordering when one CPU instruction crosses each important boundary.

## Memory bus and memory storage

Retain a precomputed address lookup, but remove exceptions from normal hot-path dispatch. An unmapped or write-only/read-only access is ordinary hardware behavior, not an exceptional Java condition.

Preferred structure:

- A fixed page table for the 24-bit address space.
- Small immutable mapping entries containing read target, write target, base translation, and access-speed class.
- Separate open-bus behavior for internal and external buses.
- Direct primitive RAM arrays.

Consider changing byte-addressed memories from `int[]` to `byte[]` only after benchmarks. `byte[]` cuts footprint by four, but every read needs unsigned conversion. Correctness and JIT measurements decide; aesthetics do not.

Public mutable arrays and flags SHOULD gradually become private with narrow inspection APIs for tests. Avoid returning live arrays from production classes unless they are explicitly documented zero-copy views.

## CPU, APU, and DSP structure

The current large classes are acceptable during parity work but are poor long-term teaching units. Split by hardware responsibility, not by arbitrary line count.

Suggested CPU boundaries:

- `Cpu65816`: registers, flags, interrupt state, instruction stepping.
- `OpcodeTable`: decode metadata and handler selection.
- `Addressing`: effective-address and operand fetch rules.
- `Alu`: pure arithmetic/flag helpers with exhaustive unit tests.
- `TimedCpuBus`: bus speed and scheduler interaction.
- `DmaController` and eight `DmaChannel` instances.

Suggested APU boundaries:

- `Spc700`: instruction execution and PSW/register state.
- `ApuMemory`: RAM, IPL overlay, I/O ports, timers, and DSP address/data ports.
- `ApuTimers`: timer dividers/stages/counters.
- `Dsp`: 32-phase signal pipeline.
- `AudioSink`: output only.

Do not turn every opcode into an allocated command object. A switch or static handler table can be excellent Java when it is explicit and allocation-free. Optimize measured dispatch, not style preferences.

Pure ALU and address-calculation helpers SHOULD be exhaustively testable without constructing a complete `SNES` instance.

## Allocation and data-layout rules

Inside steady-state `update`, instruction execution, scanline rendering, DSP phases, and frontend presentation:

- No collections are created.
- No records/classes are created per pixel, bus access, CPU instruction, PPU dot, DSP phase, scanline, audio batch, or frame.
- No array is copied merely to transfer ownership synchronously.
- No Java two-dimensional array is used for a hot raster or sample surface.
- Scratch arrays are fixed-size fields and reused.
- Records are allowed for immutable configuration created at load/reset time, not in hot loops.
- Exceptions are not control flow for valid emulated accesses.

Use Java Flight Recorder or another allocation profiler to verify these rules after warmup. Do not infer allocation behavior solely from source inspection.

## Benchmark and observability plan

Performance claims require reproducible measurements.

Add a small benchmark module or opt-in tests that report, at minimum:

1. CPU instruction throughput on deterministic RAM programs.
2. Worst-case PPU frame time for backgrounds, sprites, windows, Mode 7, mosaic, hires, and color math.
3. DSP sample throughput with all voices and echo enabled.
4. End-to-end SMW updates per second and emulated frames per second without host pacing.
5. Steady-state allocated bytes per emulated frame.
6. Heap retained after loading a ROM and rendering several hundred frames.

Benchmark rules:

- Warm up the JVM before recording.
- Record Java version, OS/architecture, commit, ROM hash when legally possible, update count, emulated frame count, wall time, and GC count.
- Separate Maven/JVM startup from measured emulation.
- Disable vsync/audio pacing for throughput measurements through a benchmark-only runner, not a production CLI mode.
- Keep correctness smoke tests separate from microbenchmarks.

Initial acceptance target on the existing Apple Silicon development machine:

- Sustained gameplay at 60 presented frames per second with audio enabled.
- At least 2x real-time unpaced core throughput for the curated SMW workload after warmup, leaving frontend and GC headroom.
- No steady-state frame-sized allocations.
- No 1024-square materialized color/priority/source surfaces.

If a target is missed, profile first. Do not guess.

## Test architecture

Production and test responsibilities must remain obvious:

- `RecordingVideoSink`: test source only; stores the last exact `VideoFrame` or selected rows and counts presentations.
- `RecordingAudioSink`: test source only; records batches/samples needed by the test.
- `FixedInputSource` or direct timestamped joypad events for deterministic input scripts.
- Synthetic ROM builders for end-to-end CPU/bus/PPU/APU behavior.

Required new test groups:

1. `VideoFrameGeometryTest`
   - All normal/overscan/interlace geometries.
   - Low-resolution horizontal duplication.
   - Mixed low/high-resolution scanlines.
   - Field parity and weave behavior.
   - No writes outside the active rows.
2. `PpuScanlineRendererTest`
   - Scroll wrapping for every tilemap/character-size combination.
   - X/Y flip, palette, priority, transparency, direct color.
   - Mosaic and offset-per-tile.
   - Main/sub windows and color math.
   - Object limits and ordering.
3. `TileRowDecoderTest`
   - 2/4/8-bpp rows, flips, address wrapping, invalidation.
4. `SchedulerBoundaryTest`
   - CPU accesses crossing render, HBlank, HDMA, refresh, timer IRQ, VBlank, NMI, and APU boundaries.
5. `FrontendUploadTest`
   - Geometry-to-texture mapping without creating a native window where possible.
   - Exact RGBA byte/int ordering.
6. Existing synthetic and local ROM tests
   - Preserve behavior through every intermediate commit.
   - Add hashes for both normal and overscan/interlace fixtures when those modes become correct.

Golden frames compare a precisely defined canonical surface and geometry. A CRC without dimensions, stride, color format, field policy, and crop is invalid.

## Migration plan and commit boundaries

### Phase 0: Measure and freeze the baseline

- Add an opt-in unpaced benchmark runner under test/benchmark sources.
- Record current SMW throughput, frame count, allocations, and retained heap.
- Record current golden geometry and CRCs.
- Make no emulator behavior change.

Suggested commit: `measure emulator baseline`

### Phase 1: Split frontend ports

- Add `VideoSink` and `AudioSink`.
- Replace `IRenderer` dependencies in `SNES`, `PPU`, `APU`, and `DSP`.
- Move GLFW loop ownership out of the video sink and into `DesktopApplication`.
- Split the current test frontend into recording video/audio sinks.
- Preserve every current pixel and sample.

Suggested commit: `separate core video and audio ports`

### Phase 2: Introduce exact reusable frame geometry

- Add `VideoFrame` with 512 stride and 478 maximum rows.
- Make LWJGL allocate a 512-by-478 texture and reusable bulk upload buffer.
- Stop vertically doubling non-interlaced output.
- Add geometry, crop, overscan, hires, and interlace tests.
- Keep the old PPU compositor temporarily if needed, copying only its valid output into the new frame.

Suggested commit: `model native snes frame geometry`

### Phase 3: Replace full-screen PPU composition with line buffers

- Add reusable main/sub line buffers.
- Render and resolve only active rows.
- Remove PPU's 1024-square main screen, subscreen, output, level, and source arrays.
- Retain existing background raster sampling temporarily so this phase isolates composition.
- Verify every PPU integration and synthetic ROM test.

Suggested commit: `compose ppu output by scanline`

### Phase 4: Sample backgrounds directly from VRAM

- Implement allocation-free tile-row decoding and logical coordinate wrapping.
- Replace one background at a time behind differential tests.
- Add Mode 7's separate direct sampler.
- Remove `Background.buffer`, `pixelDescriptors`, `tilesPriority`, and full-map render invalidation only after all modes pass.
- Add a measured fixed tile-row cache if needed.

Suggested commits:

- `sample tiled backgrounds from vram`
- `sample mode seven from vram`
- `remove materialized background rasters`

### Phase 5: Eliminate PPU hot-path allocation

- Replace per-line/per-frame arrays with reusable primitives and bit masks.
- Replace hot state objects with primitive snapshots or reusable mutable state holders.
- Verify with an allocation recording after JVM warmup.

Suggested commit: `eliminate ppu frame allocations`

### Phase 6: Make audio transfer reusable

- Introduce `AudioSink.write(array, offset, count)`.
- Remove DSP `Arrays.copyOf` batches.
- Pool OpenAL buffers and reuse native staging.
- Preserve all DSP sample tests.

Suggested commit: `reuse dsp and openal audio buffers`

### Phase 7: Timestamp CPU bus accesses

- Introduce the event-driven master scheduler and timed bus boundary.
- Migrate one timing domain at a time: PPU counters, APU ratio, refresh, HDMA, timers, interrupts, auto joypad.
- Add crossing-boundary tests before removing the old aggregate scheduler.

This phase is high risk and MUST be split across several behavior commits.

### Phase 8: Decompose monoliths only where it improves testing

- Extract pure ALU/addressing logic and APU memory/timer responsibilities.
- Keep opcode dispatch explicit and allocation-free.
- Avoid package churn with no correctness or performance benefit.

### Phase 9: Re-measure and document

- Run the same baseline workload and profiler configuration from Phase 0.
- Report before/after throughput, memory, allocations, and frame geometry.
- Package the executable jar and inspect its contents.
- Update tracked user documentation only with authorization and without overwriting concurrent edits.

Suggested commit: `document emulator architecture results`

## Explicit non-goals

Fable 5 MUST NOT:

- Reintroduce a production headless/no-renderer backend or renderer-selection CLI.
- Render the PPU on the GPU. OpenGL presents completed pixels; SNES rendering rules remain deterministic Java core logic.
- Add a dependency-injection framework.
- Introduce an ECS, reactive framework, actor system, or generalized plugin architecture.
- Allocate 4K/HD textures because host displays are large.
- Add speculative multi-threading.
- Replace integer clock arithmetic with floating point.
- Rewrite all CPU/APU opcodes for stylistic consistency.
- Change golden outputs and timing expectations without a hardware-level explanation.
- Treat the missing C++ `sources` directory as evidence that current behavior is correct.

## Definition of done

The redesign is complete only when all of the following are true:

- LWJGL is still the only shipped frontend.
- The core has narrow video and audio ports and no LWJGL imports.
- `IRenderer` no longer exists.
- No production class named `NoRenderer` or generic framebuffer renderer exists.
- Every full output pixel surface is a flat, reusable array bounded by 512 by 478; at most a small fixed front/back set may exist.
- No main/sub/source/priority/background color raster is 1024 by 1024.
- Logical 1024-square backgrounds and Mode 7 wrap correctly through direct VRAM sampling.
- Non-interlaced frames are not vertically doubled.
- Overscan, hires/pseudo-hires, interlace, and mixed-resolution scanlines have explicit geometry tests.
- PPU/DSP/frontend steady-state hot paths allocate no frame-sized objects or arrays.
- Audio batches do not use `Arrays.copyOf` or allocate a native buffer per callback.
- Hardware-visible CPU bus accesses are timestamped against the master scheduler.
- All existing tests plus the new geometry/scheduler tests pass.
- The curated local ROM smoke reaches the same or deliberately corrected gameplay state.
- Unpaced core throughput is at least 2x real time on the baseline machine after warmup.
- The executable jar contains LWJGL production classes and no test sinks.
- Every phase is committed locally with no push.

## Final instruction to Fable 5

Implement this plan in order. Prefer the smallest change that establishes the next invariant. At each phase, show evidence: changed ownership, exact dimensions, focused tests, full test result, ROM smoke result when applicable, allocation/profile evidence, and the local commit. When the hardware behavior is uncertain, investigate before coding. When performance is uncertain, measure before optimizing. Never preserve a wasteful representation merely because it resembles the C++ port, and never discard a verified hardware quirk merely because a cleaner abstraction is easier.
