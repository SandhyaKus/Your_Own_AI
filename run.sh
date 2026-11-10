#!/usr/bin/env bash
# Build and start the server. Run from the project root.
set -e
cd "$(dirname "$0")"
./build.sh
java -cp out yourownai.Main
