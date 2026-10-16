#!/bin/sh
# Builds the Magisk module (zip) to build/liquidy-magisk.zip
cd "$(dirname "$0")/magisk-module" && mkdir -p ../../build && rm -f ../../build/liquidy-magisk.zip && zip -qr ../../build/liquidy-magisk.zip . && echo "build/liquidy-magisk.zip"
