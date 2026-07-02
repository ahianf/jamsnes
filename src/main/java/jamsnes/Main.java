package jamsnes;

import jamsnes.renderer.NoRenderer;

import java.io.PrintStream;

public final class Main {
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

        try {
            SNES snes = new SNES(args[0], new NoRenderer(0, 0, 0));
            snes.update();
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
}
