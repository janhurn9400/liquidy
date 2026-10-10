#!/bin/sh
# Baut das Magisk-Modul (Zip) nach build/ioshome-magisk.zip
cd "$(dirname "$0")/magisk-module" && mkdir -p ../../build && rm -f ../../build/ioshome-magisk.zip && zip -qr ../../build/ioshome-magisk.zip . && echo "build/ioshome-magisk.zip"
