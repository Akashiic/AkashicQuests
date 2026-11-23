#!/usr/bin/env bash
# Builds build/AkashaQuestStats-<version>.jar: fetch pinned libs, compile (Java 8 bytecode), run tests, package.
# Needs a JDK 17+ (javac --release 8, jar --date) and curl; CI uses JDK 21, so the committed jar is built with 21.
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(sed -n 's/^version: *//p' src/main/resources/plugin.yml)
CENTRAL=https://repo.maven.apache.org/maven2
LIBS=(
  # spigot-api 1.8.8 is the oldest Bukkit API still published; the code uses only API that Bukkit 1.7.10 had
  "spigot-api-1.8.8-R0.1-20160221.082514-43.jar|https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/1.8.8-R0.1-SNAPSHOT/spigot-api-1.8.8-R0.1-20160221.082514-43.jar|bc50bbc4d8226d75d0d75460828567b4580841d5987cae5c1d89b321f8ffc071"
  "BetterQuesting-3.8.79-GTNH-dev.jar|https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/BetterQuesting/3.8.79-GTNH/BetterQuesting-3.8.79-GTNH-dev.jar|29af0c65679b006b1306d4e9b8d537bcbd5ca3d9253fee34bbbab5fecf36fc8a"
  "guava-17.0.jar|$CENTRAL/com/google/guava/guava/17.0/guava-17.0.jar|8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc"
)

mkdir -p libs
CP=""
for entry in "${LIBS[@]}"; do
  IFS='|' read -r name url sha <<<"$entry"
  if [ ! -f "libs/$name" ] || ! echo "$sha  libs/$name" | sha256sum -c --quiet - 2>/dev/null; then
    curl -sSfL -o "libs/$name.part" "$url"
    mv "libs/$name.part" "libs/$name"
  fi
  echo "$sha  libs/$name" | sha256sum -c --quiet -
  CP="$CP${CP:+:}libs/$name"
done

rm -rf build
mkdir -p build/classes build/test-classes
javac --release 8 -Xlint:all,-options,-classfile -Werror -d build/classes -cp "$CP" $(find src/main/java -name '*.java')
javac --release 8 -Xlint:-options -d build/test-classes -cp "$CP:build/classes" $(find src/test/java -name '*.java')
java -cp "$CP:build/classes:build/test-classes" akasha.queststats.PluginTests

cp src/main/resources/* build/classes/
JAR="build/AkashaQuestStats-$VERSION.jar"
# fixed date: the same sources always give the same jar
TZ=UTC jar --create --file "$JAR" --date=2026-01-01T00:00:00Z -C build/classes .
sha256sum "$JAR"
