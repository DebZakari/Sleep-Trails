package dev.sleeptrails.vitrail;

import dev.vitrail.render.PackChain;

/** Loaded only when the exact Vitrail is present (VanillaTrails.packDrawing). */
public final class PackState {
    private PackState() {}

    /** A pack is loaded and still drawing. With none, Vitrail leaves the sky, and its stars, to the game. */
    public static boolean drawing() { return PackChain.drawingPack(); }
}
