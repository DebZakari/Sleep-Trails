# Sleep Trails

Sleep Trails is a client-side Fabric mod for Minecraft 26.3. It smooths the sky's transition from night to morning while you sleep and adds long-exposure star trails.

![Star trails during sleep with the vanilla camera at zero tilt](docs/media/showcase.gif)

*Vanilla camera at 0° tilt, with Vitrail and Complementary Unbound r5.9.3.*

The transition starts in bed and finishes after waking. Server time and gameplay stay unchanged. Other players and the server do not need the mod.

**Development version:** `0.1.0-dev.10`. No public release is available yet.

## Features

- A five-second sky transition while you sleep, followed by a two-second finish when the server skips the night.
- Star trails using vanilla's stars, or the supported shader pack's stars.
- A smooth return to the current sky if you leave bed before the night skips.
- Adjustable camera tilt and eye position, plus a Cinematic camera mode.
- Settings in Mod Menu and Sodium's Video Settings when those mods are installed.

## Requirements and compatibility

Requires Minecraft 26.3, Fabric Loader 0.19.5 or newer, and Java 25 or newer.

| Renderer | Behavior |
|---|---|
| Vanilla, without Iris or Vitrail | Sky transition and star trails |
| Vitrail `0.13.0-beta.vultrum.4` with Complementary Unbound r5.9.3 | Sky transition and star trails |
| That Vitrail build with shaders off or no pack loaded | Sky transition and vanilla star trails |
| That Vitrail build with another pack | Sky transition only |
| Other Vitrail builds, or Iris | Effect disabled |

The Vitrail adapter checks the renderer JAR and shader pack against the supported file hashes. A matching version label alone is insufficient.

Fabric API is not required. Sodium and Mod Menu are optional at runtime.

## Settings

Open Sleep Trails from Mod Menu or Sodium's Video Settings. Changes made during a transition apply from the next sleep.

| Setting | Default | Options |
|---|---|---|
| Sky Transition | On | Enable or disable the effect |
| Star Trails | On | Enable or disable trails while keeping the sky transition |
| Camera Mode | Vanilla | Vanilla or Cinematic |
| Tilt | Auto | Auto, or 0 to 60 degrees in Vanilla mode |
| Eye Position | Toward the Feet | Toward the Feet, Vanilla, Raised, or Vanilla Body Pose when Entity Model Features is installed |

Auto tilt follows your vertical field of view. Cinematic mode raises the view while sleeping and orbits the sleeper in third person. Camera changes affect rendering only.

Settings are saved in `config/sleep_trails.json`. You can also use these JVM arguments:

- `-Dsleeptrails.disabled=true` disables the effect.
- `-Dsleeptrails.trails.disabled=true` disables trails while keeping the transition.

## Current limitations

- The effect runs in the Overworld with its normal clock, for sleeps entered from sunset onward.
- Shader support is limited to the exact files listed above.
- On servers that deliver the time update after waking, the sky may start returning toward night before continuing to morning. Updates arriving more than 1.5 seconds after waking can still cause a jump.

## Build

Use JDK 25 and the included Gradle wrapper. The build requires three local compile dependencies and the shader pack for one test. See [Building from source](docs/BUILDING.md) for file names, configuration and commands.

The mod JAR is written to `build/libs/`. Copy the JAR without the `-sources` suffix into your client's `mods` folder.

## License

Sleep Trails is licensed under the [MIT License](LICENSE). Third-party mods and shader packs retain their own licenses and are supplied separately.
