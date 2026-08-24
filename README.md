# JamSNES

JamSNES is a Java SNES emulator.

## Requirements

- Install Java Development Kit (JDK) 17 or a later version.
- Install Apache Maven.
- Use a supported desktop system.
- Prepare a valid SNES ROM file. Use a ROM that you can legally use.

## Build the application

1. Open a terminal.
2. Go to the JamSNES directory.
3. Run this command:

   ```sh
   mvn clean package
   ```

Maven creates the application file at `target/jamsnes.jar`.

## Launch the application

1. Keep the terminal in the JamSNES directory.
2. Run this command. Replace `path/to/game.sfc` with the path to your ROM file:

   ```sh
   java -jar target/jamsnes.jar path/to/game.sfc
   ```

The application opens a window and starts the ROM.

## Show help

Run this command:

```sh
java -jar target/jamsnes.jar --help
```

The command prints the available command format.

## Default keyboard controls

- Use the arrow keys for the directional buttons.
- Press `Z` for the B button.
- Press `X` for the A button.
- Press `A` for the Y button.
- Press `S` for the X button.
- Press `Enter` or `Space` for Start.
- Press `Right Shift` for Select.
- Press `Q` for L.
- Press `E` for R.

## Stop the application

Close the application window.

