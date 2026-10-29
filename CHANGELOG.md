# Changelog

## 0.1.0-alpha.4 (2026-10-29)

- Add sky-transition support for the exact Iris `1.11.7+mc26.3` and Sodium `0.9.2+mc26.3` setup on OpenGL. Other Iris/Sodium builds remain unsupported.
- Add the original long-exposure star trails for that Iris setup with the exact Complementary Unbound r5.9.3 pack. Other packs and Iris with shaders off keep sky transitions without added trails.
- Reduce Iris star-trail shader work by skipping noise calculations that cannot contribute a visible star.

## 0.1.0-alpha.3 (2026-10-25)

- Smooth server time jumps between day and night over five seconds while awake, including another player skipping the night. Smooth Time Jumps is enabled by default and can be disabled separately from in-bed transitions.
- Retarget an awake transition from the visible sky when another qualifying time jump arrives. Camera and movement remain under your control.
- Default the Vanilla camera mode's tilt to 0°, labeled "0° (Vanilla)". Existing saved Auto and custom angles are preserved.

## 0.1.0-alpha.2 (2026-10-24)

- Vanilla clouds move with the sky during the sleep transition, then resume their normal speed without jumping back to their previous position.
- Star trails are hidden behind vanilla cloud bodies and remain visible through gaps in the clouds.

## 0.1.0-alpha.1 (2026-10-24)

First public alpha for Minecraft 26.3 on Fabric.

- Smooths the sky from night to morning while sleeping, continuing after waking when needed.
- Adds star trails to vanilla rendering and the supported Vitrail and Complementary Unbound setup.
- Eases back to the current sky when sleep is interrupted, and handles delayed time updates from servers.
- Includes Vanilla and Cinematic camera modes, adjustable tilt and eye position, and settings in Mod Menu and Sodium.
- Runs on the client. Other players and the server do not need the mod.

Requires Java 25 or newer and Fabric Loader 0.19.5 or newer. Fabric API is not required.

Shader trails require the exact Vitrail `0.13.0-beta.vultrum.4` build and Complementary Unbound r5.9.3. With that Vitrail build and another pack, the sky transition works without shader star trails. Other Vitrail builds and Iris disable the effect.

The effect runs in the Overworld with its normal clock, for sleeps entered from sunset onward. Delayed server time updates can make the sky briefly turn back toward night; updates received more than 1.5 seconds after waking can still cause a jump.
