#!/bin/sh
# Fails if the frozen Bluetooth files differ from the version released as 0.3.0 (git tag v0.3.0).
cd "$(dirname "$0")/../app/src/main/java/com/lifetrenz/qrreader" || exit 2
shasum -a 256 -c ../../../../../../../baseline/bluetooth-frozen.sha256 && echo "Bluetooth code unchanged"
