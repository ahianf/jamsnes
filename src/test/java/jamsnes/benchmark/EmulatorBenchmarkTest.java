package jamsnes.benchmark;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in unpaced throughput benchmark. It is not a correctness test and is
 * skipped unless a ROM is supplied:
 *
 * <pre>
 * mvn test -Dtest=EmulatorBenchmarkTest \
 *     -Djamsnes.benchmark.rom=local-roms/smw.sfc \
 *     [-Djamsnes.benchmark.warmupUpdates=N] [-Djamsnes.benchmark.measuredUpdates=N]
 * </pre>
 *
 * The run is unpaced: the test frontend never blocks on vsync or audio, so the
 * reported real-time ratio is pure core throughput.
 */
class EmulatorBenchmarkTest {
    private static final String ROM_PROPERTY = "jamsnes.benchmark.rom";
    private static final String WARMUP_PROPERTY = "jamsnes.benchmark.warmupUpdates";
    private static final String MEASURED_PROPERTY = "jamsnes.benchmark.measuredUpdates";
    private static final int DEFAULT_WARMUP_UPDATES = 400_000;
    private static final int DEFAULT_MEASURED_UPDATES = 1_000_000;
    private static final double EMULATED_FRAMES_PER_SECOND = 60.0988;

    @Test
    void reportUnpacedThroughput() {
        String romProperty = System.getProperty(ROM_PROPERTY);
        assumeTrue(romProperty != null && !romProperty.isBlank(),
                () -> "Set -D" + ROM_PROPERTY + "=/path/to/local.rom to run the benchmark.");
        Path rom = Path.of(romProperty);
        assumeTrue(Files.isRegularFile(rom), () -> "Benchmark ROM does not exist: " + rom);

        int warmupUpdates = positiveProperty(WARMUP_PROPERTY, DEFAULT_WARMUP_UPDATES);
        int measuredUpdates = positiveProperty(MEASURED_PROPERTY, DEFAULT_MEASURED_UPDATES);

        SNES snes = new SNES(rom.toString(), new RecordingVideoSink(), new RecordingAudioSink());

        runUpdates(snes, warmupUpdates);

        long allocatedBefore = currentThreadAllocatedBytes();
        long gcCountBefore = totalGcCount();
        long framesBefore = snes.ppu.frameCounter();
        long startNanos = System.nanoTime();

        runUpdates(snes, measuredUpdates);

        long elapsedNanos = System.nanoTime() - startNanos;
        long frames = snes.ppu.frameCounter() - framesBefore;
        long allocatedBytes = currentThreadAllocatedBytes() - allocatedBefore;
        long gcCount = totalGcCount() - gcCountBefore;
        long retainedHeap = retainedHeapAfterGc();

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double emulatedFps = frames / elapsedSeconds;
        double realTimeRatio = emulatedFps / EMULATED_FRAMES_PER_SECOND;

        System.out.println("=== JamSNES unpaced benchmark ===");
        System.out.printf("java.version        %s%n", System.getProperty("java.version"));
        System.out.printf("os                  %s %s%n", System.getProperty("os.name"), System.getProperty("os.arch"));
        System.out.printf("rom                 %s%n", rom);
        System.out.printf("warmup updates      %,d%n", warmupUpdates);
        System.out.printf("measured updates    %,d%n", measuredUpdates);
        System.out.printf("wall time           %.3f s%n", elapsedSeconds);
        System.out.printf("updates/s           %,.0f%n", measuredUpdates / elapsedSeconds);
        System.out.printf("emulated frames     %,d%n", frames);
        System.out.printf("emulated fps        %,.1f%n", emulatedFps);
        System.out.printf("real-time ratio     %.2fx%n", realTimeRatio);
        System.out.printf("allocated           %,d bytes (%,.0f bytes/frame)%n",
                allocatedBytes, frames == 0 ? 0.0 : (double) allocatedBytes / frames);
        System.out.printf("gc collections      %d%n", gcCount);
        System.out.printf("retained heap       %,d bytes after GC%n", retainedHeap);
    }

    private static void runUpdates(SNES snes, int updates) {
        for (int i = 0; i < updates; i++) {
            snes.update();
        }
    }

    private static int positiveProperty(String property, int defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(property + " must be positive");
        }
        return parsed;
    }

    private static long currentThreadAllocatedBytes() {
        if (ManagementFactory.getThreadMXBean()
                instanceof com.sun.management.ThreadMXBean hotspotThreadBean) {
            return hotspotThreadBean.getCurrentThreadAllocatedBytes();
        }
        return 0;
    }

    private static long totalGcCount() {
        long count = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long collections = gc.getCollectionCount();
            if (collections > 0) {
                count += collections;
            }
        }
        return count;
    }

    private static long retainedHeapAfterGc() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
