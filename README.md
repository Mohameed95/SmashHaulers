# Smash Haulers

An original physics-launcher game for Android, written in Kotlin with no external
game engine — just a `SurfaceView` game loop, Canvas rendering, and simple custom
2D physics.

**Gameplay:** drag back from the launch pad on the left, release to fling a truck
at the crate stacks on the right, and squash every alien to clear the level.
3 levels, limited trucks per level, score for crates smashed (100 / 300 for steel)
and aliens squashed (500).

## How to build and install

1. Install [Android Studio](https://developer.android.com/studio) (any recent version).
2. **File → Open** and select this `SmashHaulers` folder. Let Gradle sync finish
   (first sync downloads dependencies; it may take a few minutes).
3. Connect your Android phone with USB debugging enabled
   (Settings → About phone → tap "Build number" 7 times → Developer options → USB debugging),
   or create an emulator via **Device Manager**.
4. Press **Run ▶**. The game installs and launches in landscape.

To produce a shareable APK: **Build → Build Bundle(s)/APK(s) → Build APK(s)**;
the file lands in `app/build/outputs/apk/debug/`.

## Project layout

- `app/src/main/java/com/smashhaulers/game/MainActivity.kt` — fullscreen activity, hosts the game view
- `.../GameView.kt` — surface + game-loop thread, screen→world coordinate mapping
- `.../GameWorld.kt` — physics, entities, levels, input, scoring, rendering

## Extending it

- **Add levels:** edit `loadLevel()` in `GameWorld.kt` — `crate(x, stackHeight)` and
  `alienOn(x, stackHeight)` build the layouts; bump `levelCount`.
- **Tune feel:** the constants at the top of `GameWorld` (`GRAVITY`, `LAUNCH_POWER`,
  `MAX_DRAG`, restitution/friction factors in the collision code).
- **Art/sound:** all visuals are drawn in `draw*()` functions with Canvas; swap them
  for bitmaps when you have artwork, and add `SoundPool` for impact sounds.

This is an original work: no code or assets from any existing game.
