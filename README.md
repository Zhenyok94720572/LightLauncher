# Light Launcher 1.0

An unofficial Minecraft: Java Edition launcher powered by Java Swing. Launches the game automatically, installs mods, presets, textures, and shaders from Modrinth,
supports Fabric / Quilt / Forge / NeoForge, isolated installations, offline nicknames, Microsoft and Ely.by logins, local skins, and a Russian and English interface.

## Assembly
Requires JDK 17+ (and `python3 -m pip install ziglang` only if you need Windows executables).

./build.sh # dist/LightLauncher.jar, LightLauncher-Setup.jar (+ .exe if ziglang is present) 
./build.sh nojar # compilation only 

## Structure
- `src/lightmods/` — launcher (`App` is the entry point)
- `setup-src/lightmods/setup/` — installer (updating an existing installation)
- `res/` — resources: `avatar.png`, `i18n/en.json` (English dictionary; Russian texts are source)
- `winstub/` — C wrapper for .exe (JNI, Java 17+ search); exe = shell + jar
- `test/` — mock server `mock.py` and tests (`LaunchTest`, `SkinTest`, `MpFixTest`, `ElyTest`, …)

## Language
The texts in the code are Russian; `I18n.t()` translates them using the dictionary `res/i18n/en.json` (exact match, then fragment replacement).
New line in interface = add "Russian → English" pair to `en.json`.
