package jamsnes.renderer.lwjgl;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.system.MemoryUtil.NULL;

final class LwjglAudioDevice implements AutoCloseable {
    static final int SAMPLE_RATE = 32_040;
    static final int MAX_QUEUED_BUFFERS = 3;
    private static final long QUEUE_WAIT_NANOS = 1_000_000;

    private long device;
    private long context;
    private int source;
    private boolean initialized;

    void queueSamples(short[] samples) {
        if (samples.length == 0) {
            return;
        }
        ensureInitialized();
        waitForQueueSlot();

        int buffer = AL10.alGenBuffers();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, pcm16StereoLittleEndian(samples), SAMPLE_RATE);
        AL10.alSourceQueueBuffers(source, buffer);
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source);
        }
    }

    static boolean queueIsFull(int queuedBuffers) {
        return queuedBuffers >= MAX_QUEUED_BUFFERS;
    }

    static ByteBuffer pcm16StereoLittleEndian(short[] samples) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(samples.length * Short.BYTES);
        for (short sample : samples) {
            buffer.put((byte) (sample & 0xff));
            buffer.put((byte) ((sample >>> 8) & 0xff));
        }
        buffer.flip();
        return buffer;
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
        initialized = true;
    }

    private void deleteProcessedBuffers() {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(source));
        }
    }

    private void waitForQueueSlot() {
        while (true) {
            deleteProcessedBuffers();
            if (!queueIsFull(AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED))) {
                return;
            }
            LockSupport.parkNanos(QUEUE_WAIT_NANOS);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
        }
    }

    @Override
    public void close() {
        if (!initialized) {
            return;
        }
        AL10.alSourceStop(source);
        deleteProcessedBuffers();
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        while (queued-- > 0) {
            AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(source));
        }
        AL10.alDeleteSources(source);
        ALC10.alcMakeContextCurrent(NULL);
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
        source = 0;
        context = NULL;
        device = NULL;
        initialized = false;
    }
}
