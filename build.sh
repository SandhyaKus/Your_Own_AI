#!/usr/bin/env bash
# Compile Your_Own_AI -> out/
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out -sourcepath src/main/java src/main/java/yourownai/Main.java
echo "Build OK"
