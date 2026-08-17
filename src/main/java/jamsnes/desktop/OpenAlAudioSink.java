package jamsnes.desktop;

import jamsnes.apu.dsp.DSP;
import jamsnes.audio.AudioSink;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Plays core audio batches through OpenAL. The bounded queue is the real-time
 * pacing source: writes block until a queue slot frees up, and the optional
 * event pump keeps the desktop window responsive while waiting. Native PCM
 * staging and the OpenAL buffer objects are allocated once and reused; no
 * per-batch Java or native allocation happens in steady state.
 */
final class OpenAlAudioSink implements AudioSink, AutoCloseable {
    static final int SAMPLE_RATE = DSP.OUTPUT_SAMPLE_RATE_HZ;
    static final int MAX_QUEUED_BUFFERS = 3;
    /** Sized to the DSP's whole sound buffer so any batch fits the staging. */
    static final int MAX_BATCH_SAMPLES = 0x10000;
    private static final long QUEUE_WAIT_NANOS = 1_000_000;

    private final Runnable eventPump;
    private final int[] bufferPool = new int[MAX_QUEUED_BUFFERS];
    private int pooledBuffers;
    private ByteBuffer pcmStaging;
    private long device;
    private long context;
    private int source;
    private boolean initialized;

    OpenAlAudioSink() {
        this(() -> {
        });
    }

    OpenAlAudioSink(Runnable eventPump) {
        this.eventPump = eventPump;
    }

    @Override
    public void write(short[] interleavedStereo, int offset, int sampleCount) {
        eventPump.run();
        if (sampleCount <= 0) {
            return;
        }
        ensureInitialized();
        if (!waitForFreeBuffer()) {
            return;
        }

        int buffer = bufferPool[--pooledBuffers];
        fillPcm16StereoLittleEndian(pcmStaging, interleavedStereo, offset, sampleCount);
        AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, pcmStaging, SAMPLE_RATE);
        AL10.alSourceQueueBuffers(source, buffer);
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source);
        }
    }

    static boolean queueIsFull(int queuedBuffers) {
        return queuedBuffers >= MAX_QUEUED_BUFFERS;
    }

    /**
     * Packs {@code sampleCount} 16-bit samples into little-endian PCM bytes,
     * reusing {@code staging}. On return the buffer is flipped and its limit
     * covers exactly the packed bytes, so no stale samples from a previous
     * batch remain visible.
     */
    static void fillPcm16StereoLittleEndian(ByteBuffer staging, short[] samples, int offset, int sampleCount) {
        staging.clear();
        for (int i = 0; i < sampleCount; i++) {
            short sample = samples[offset + i];
            staging.put((byte) (sample & 0xff));
            staging.put((byte) ((sample >>> 8) & 0xff));
        }
        staging.flip();
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        device = ALC10.alcOpenDevice((ByteBuffer) null);
        if (device == NULL) {
            throw new IllegalStateException("Could not open OpenAL device");
        }
        context = ALC10.alcCreateContext(device, (IntBuffer) null);
        if (context == NULL) {
            ALC10.alcCloseDevice(device);
            device = NULL;
            throw new IllegalStateException("Could not create OpenAL context");
        }
        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(ALC.createCapabilities(device));
        source = AL10.alGenSources();
        pcmStaging = BufferUtils.createByteBuffer(MAX_BATCH_SAMPLES * Short.BYTES);
        for (pooledBuffers = 0; pooledBuffers < MAX_QUEUED_BUFFERS; pooledBuffers++) {
            bufferPool[pooledBuffers] = AL10.alGenBuffers();
        }
        initialized = true;
    }

    private void reclaimProcessedBuffers() {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            bufferPool[pooledBuffers++] = AL10.alSourceUnqueueBuffers(source);
        }
    }

    /** Returns true when a pooled buffer is available; false if interrupted first. */
    private boolean waitForFreeBuffer() {
        while (true) {
            reclaimProcessedBuffers();
            if (pooledBuffers > 0) {
                return true;
            }
            LockSupport.parkNanos(QUEUE_WAIT_NANOS);
            if (Thread.currentThread().isInterrupted()) {
                reclaimProcessedBuffers();
                return pooledBuffers > 0;
            }
        }
    }

    @Override
    public void close() {
        if (!initialized) {
            return;
        }
        AL10.alSourceStop(source);
        reclaimProcessedBuffers();
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        while (queued-- > 0 && pooledBuffers < MAX_QUEUED_BUFFERS) {
            bufferPool[pooledBuffers++] = AL10.alSourceUnqueueBuffers(source);
        }
        for (int i = 0; i < pooledBuffers; i++) {
            AL10.alDeleteBuffers(bufferPool[i]);
        }
        AL10.alDeleteSources(source);
        ALC10.alcMakeContextCurrent(NULL);
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
        source = 0;
        context = NULL;
        device = NULL;
        pooledBuffers = 0;
        initialized = false;
    }
}
