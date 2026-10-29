# Inhabited World

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## Testing

- **In-game:** double-click `test-launch.bat` at the workspace root. It builds this checkout and opens the
  dev client (player `Dev`) in a fresh "Inhabited Test" world: fixed seed, creative, cheats on.
  `test-launch.bat default` makes a "Default Test" world with the same seed, for comparisons.
  Each launch replaces the previous test world of that kind; other worlds are never touched.
  (Gradle: `gradlew runTestClient [-PtestWorld=default]`.)
- **Automated:** `gradlew build` runs the JUnit unit tests (`src/test/`) and the headless Fabric game tests
  (`src/gametest/`, a test-only mod that never ships). The game-test world is a real Inhabited World:
  the test mod swaps the game-test server's `minecraft:flat_all_dimensions` preset for a build-time copy
  of our `rpg_world` preset. Run only the game tests with `gradlew runGameTest`.

## License

All rights reserved. See LICENSE.
