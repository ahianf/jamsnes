package jamsnes;

import jamsnes.ppu.Background;
import jamsnes.renderer.IRenderer;
import jamsnes.renderer.NoRenderer;
import jamsnes.renderer.lwjgl.LwjglRenderer;

import java.io.PrintStream;

public final class Main {
    private static final String RENDERER_PROPERTY = "jamsnes.renderer";

    private Main() {
    }

    public static void main(String[] args) {
        run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]))) {
            usage(out);
            return 0;
        }
        if (args.length != 1) {
            usage(err);
            return 1;
        }
        return run(args, out, err, defaultRenderer());
    }

    static int run(String[] args, PrintStream out, PrintStream err, IRenderer renderer) {
        if (args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]))) {
            usage(out);
            return 0;
        }
        if (args.length != 1) {
            usage(err);
            return 1;
        }

        try {
            SNES snes = new SNES(args[0], renderer);
            renderer.createWindow(snes, 60);
            return 0;
        } catch (RuntimeException exception) {
            err.println(exception.getMessage());
            return 1;
        }
    }

    private static void usage(PrintStream stream) {
        stream.println("JamSNES:");
        stream.println("\tUsage: jamsnes rom_path");
        stream.println("Options:");
        stream.println("\t-h, --help:\tDisplay this help message and exit.");
    }

    static IRenderer defaultRenderer() {
        String renderer = System.getProperty(RENDERER_PROPERTY, "headless");
        if ("lwjgl".equalsIgnoreCase(renderer)) {
            return new LwjglRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        }
        return new NoRenderer(0, 0, 0);
    }
}
