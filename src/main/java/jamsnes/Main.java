package jamsnes;

import jamsnes.ppu.Background;
import jamsnes.renderer.IRenderer;
import jamsnes.renderer.NoRenderer;
import jamsnes.renderer.lwjgl.LwjglRenderer;

import java.io.PrintStream;

public final class Main {
    private static final String RENDERER_PROPERTY = "jamsnes.renderer";
    private static final String RENDERER_HEADLESS = "headless";
    private static final String RENDERER_LWJGL = "lwjgl";

    private Main() {
    }

    public static void main(String[] args) {
        run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (isHelp(args)) {
            usage(out);
            return 0;
        }

        LaunchOptions options;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            usage(err);
            return 1;
        }

        return run(new String[]{options.romPath()}, out, err, rendererFor(options.renderer()));
    }

    static int run(String[] args, PrintStream out, PrintStream err, IRenderer renderer) {
        if (isHelp(args)) {
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
        stream.println("\tUsage: jamsnes rom_path [--renderer=headless|lwjgl]");
        stream.println("Options:");
        stream.println("\t-h, --help:\tDisplay this help message and exit.");
        stream.println("\t--renderer:\tSelect headless or lwjgl renderer.");
        stream.println("\t--headless:\tUse the headless renderer.");
        stream.println("\t--lwjgl:\tUse the LWJGL/OpenGL renderer.");
    }

    static IRenderer defaultRenderer() {
        return rendererFor(System.getProperty(RENDERER_PROPERTY, RENDERER_HEADLESS));
    }

    static IRenderer rendererFor(String renderer) {
        if (RENDERER_LWJGL.equalsIgnoreCase(renderer)) {
            return new LwjglRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        }
        if (!RENDERER_HEADLESS.equalsIgnoreCase(renderer)) {
            throw new IllegalArgumentException("Unknown renderer: " + renderer);
        }
        return new NoRenderer(0, 0, 0);
    }

    static LaunchOptions parseArgs(String[] args) {
        String romPath = null;
        String renderer = System.getProperty(RENDERER_PROPERTY, RENDERER_HEADLESS);
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--lwjgl".equals(arg)) {
                renderer = RENDERER_LWJGL;
            } else if ("--headless".equals(arg)) {
                renderer = RENDERER_HEADLESS;
            } else if (arg.startsWith("--renderer=")) {
                renderer = arg.substring("--renderer=".length());
            } else if ("--renderer".equals(arg)) {
                if (++i >= args.length) {
                    throw new IllegalArgumentException("--renderer requires a value");
                }
                renderer = args[i];
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else if (romPath == null) {
                romPath = arg;
            } else {
                throw new IllegalArgumentException("Only one ROM path is supported");
            }
        }

        if (romPath == null) {
            throw new IllegalArgumentException("Missing ROM path");
        }
        rendererFor(renderer);
        return new LaunchOptions(romPath, renderer);
    }

    private static boolean isHelp(String[] args) {
        return args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]));
    }

    record LaunchOptions(String romPath, String renderer) {
    }
}
