#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .qa-classes
javac -d .qa-classes src/main/java/online/theaxiom/wardrobe/rig/*.java src/test/java/online/theaxiom/wardrobe/rig/*.java
for t in TestRigCore TestWingsRigPlanner TestWingsAttachmentCoordinator TestResourcePackAcceptanceGate TestRigCalibration; do
 java -cp .qa-classes "online.theaxiom.wardrobe.rig.$t"
done
