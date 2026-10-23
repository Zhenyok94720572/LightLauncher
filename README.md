# Light Launcher 1.0

Неофициальный лаунчер Minecraft: Java Edition на Java Swing. Запускает игру сам, ставит моды, сборки, текстуры и шейдеры из Modrinth,
поддерживает Fabric / Quilt / Forge / NeoForge, изолированные установки, офлайн-ник, вход через Microsoft и Ely.by, локальные скины,
интерфейс на русском и английском.

## Сборка
Нужен JDK 17+ (и `python3 -m pip install ziglang` — только если нужны Windows-exe).

    ./build.sh            # dist/LightLauncher.jar, LightLauncher-Setup.jar (+ .exe, если есть ziglang)
    ./build.sh nojar      # только компиляция

## Структура
- `src/lightmods/` — лаунчер (`App` — точка входа)
- `setup-src/lightmods/setup/` — установщик (обновление существующей установки)
- `res/` — ресурсы: `avatar.png`, `i18n/en.json` (английский словарь; русские тексты — исходные)
- `winstub/` — C-оболочка для .exe (JNI, поиск Java 17+); exe = оболочка + jar
- `test/` — мок-сервер `mock.py` и тесты (`LaunchTest`, `SkinTest`, `MpFixTest`, `ElyTest`, …)

## Язык
Тексты в коде русские; `I18n.t()` переводит их через словарь `res/i18n/en.json` (точное совпадение, затем замена фрагментов).
Новая строка в интерфейсе = добавить пару «русский → английский» в `en.json`.




## Сайт

Файл `index.html` в корне — сайт лаунчера (GitHub Pages: Settings → Pages → Deploy from a branch → main → / (root)).
