#!/bin/sh
# Сборка Light Launcher. Нужен JDK 17+ (jar, javac). Для Windows-exe дополнительно: python3 -m pip install ziglang
# Использование: ./build.sh            — jar-файлы и (если есть ziglang) exe в папку dist/
#                ./build.sh nojar      — только компиляция
set -e
cd "$(dirname "$0")"
VER="1.2 beta"
OUT=out
rm -rf "$OUT" dist
mkdir -p "$OUT/main" "$OUT/setup" "$OUT/stage" dist

echo "== компиляция"
javac -encoding UTF-8 --release 17 -d "$OUT/main" src/lightmods/*.java
cp -r res/* "$OUT/main/"
javac -encoding UTF-8 --release 17 -cp "$OUT/main" -d "$OUT/setup" setup-src/lightmods/setup/*.java
[ "$1" = "nojar" ] && exit 0

if python3 -m ziglang version >/dev/null 2>&1; then
  echo "== нативное ядро llcore (C++)"
  mkdir -p "$OUT/main/native"
  python3 -m ziglang c++ -target x86_64-windows-gnu -std=c++17 -O2 -s -static -Wl,--subsystem,windows \
    -o "$OUT/main/native/llcore.exe" native/llcore.cpp -lwinhttp
fi

printf 'Main-Class: lightmods.App\nImplementation-Title: Light Launcher\nImplementation-Version: %s\n' "$VER" > "$OUT/app.mf"
printf 'Main-Class: lightmods.setup.Setup\nImplementation-Title: Light Launcher Setup\n' > "$OUT/setup.mf"
jar cfm dist/LightLauncher.jar "$OUT/app.mf" -C "$OUT/main" .

HAVE_ZIG=0
if python3 -m ziglang version >/dev/null 2>&1; then HAVE_ZIG=1; fi

if [ "$HAVE_ZIG" = 1 ]; then
  echo "== exe (zig cc)"
  ( cd winstub
    for T in app:lightmods/App setup:lightmods/setup/Setup; do
      N=${T%%:*}; C=${T#*:}
      python3 -m ziglang cc -target x86_64-windows-gnu -O2 -s -Wl,--subsystem,windows -Ijni \
        -DMAINCLASS="\"$C\"" stub.c app.rc -o "stub-$N.exe" -lshell32 -luser32
    done )
  cat winstub/stub-app.exe dist/LightLauncher.jar > dist/LightLauncher.exe
fi

echo "== установщик"
mkdir -p "$OUT/stage/app"
cp -r "$OUT/main/." "$OUT/stage/"
cp -r "$OUT/setup/." "$OUT/stage/"
cp dist/LightLauncher.jar "$OUT/stage/app/"
[ -f dist/LightLauncher.exe ] && cp dist/LightLauncher.exe "$OUT/stage/app/"
jar cfm dist/LightLauncher-Setup.jar "$OUT/setup.mf" -C "$OUT/stage" .
[ "$HAVE_ZIG" = 1 ] && cat winstub/stub-setup.exe dist/LightLauncher-Setup.jar > dist/LightLauncher-Setup.exe
echo "Готово: dist/"; ls -l dist
