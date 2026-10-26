# Custom Crosshair (Fabric, Minecraft 1.21.11)

Standalone version of the **Custom Crosshair** module from Anubis Client. Nothing else from Anubis is included.

* Press **`** (grave accent / backtick) in-game to open the settings panel. Press it again or Esc to close.
  The key can be changed in Options > Controls > Custom Crosshair.
* The panel matches the Anubis module page: master toggle, Show in HUD, Toggle Message, Style
  (Cross / T / X / Dot / Circle), Size, Gap, Thickness, Center Dot, Outline, Color (Custom / Accent),
  Custom Color, Target Tint (+ Target Color), Dynamic Gap (+ Spread), "Reset all".
  A blue dot next to a setting means it differs from the default.
* Settings are saved to `config/customcrosshair.json`.

## Building

Requires JDK 21 and Gradle 9.2+ (or run `gradle wrapper` once, then use `./gradlew`).

    gradle build

The jar is created in `build/libs/customcrosshair-1.0.0.jar`. Put it in your `mods` folder together with
Fabric Loader and Fabric API for 1.21.11.

If a dependency version in `gradle.properties` does not resolve, take the current ones for 1.21.11 from
https://fabricmc.net/develop.

## License

GPL-3.0-only, same as Anubis Client. See `LICENSE` and `NOTICE.md`.
