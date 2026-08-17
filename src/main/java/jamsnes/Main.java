package jamsnes;

import jamsnes.desktop.DesktopApplication;

import java.io.PrintStream;

public final class Main {
    private Main() {
    }

    @FunctionalInterface
    interface RomLauncher {
        void launch(String romPath);
    }

    public static void main(String[] args) {
        run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, DesktopApplication::run);
    }

    static int run(String[] args, PrintStream out, PrintStream err, RomLauncher launcher) {
        if (isHelp(args)) {
            usage(out);
            return 0;
        }

        if (args.length != 1) {
            if (args.length == 0) {
                err.println("Missing ROM path");
            }
            usage(err);
            return 1;
        }
        if (args[0].startsWith("-")) {
            err.println("Unknown option: " + args[0]);
            usage(err);
            return 1;
        }

        try {
            launcher.launch(args[0]);
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

    private static boolean isHelp(String[] args) {
        return args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]));
    }
}
