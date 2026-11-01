#!/usr/bin/env python3
"""Read-only structure checks. Not a replacement for compilation against Paper."""
from pathlib import Path
from zipfile import ZipFile
from xml.etree import ElementTree
import hashlib
import json
import sys
root=Path(__file__).resolve().parents[1]
assert ElementTree.parse(root/'pom.xml').getroot() is not None
source=(root/'src/main/java/online/theaxiom/wardrobe/probe/AxiomRigProbePlugin.java').read_text()
assert 'player.addPassenger(display)' in source
assert 'getBodyYaw()' in source
assert 'setItemModel(itemModelKey)' in source
assert 'setItemMeta(meta)' in source
assert 'PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED' in source
assert 'requiredPackId.equals(pack)' in source
assert 'RigCalibration.parse(args,1)' in source
assert 'case "inspect"' in source
assert 'display.getVehicle()==player' in source
assert 'display.getVehicle() == player' in source
assert 'boolean isMounted(ItemDisplay display,Frame frame)' in source
assert 'rig.attached(id).isPresent()' in source
assert 'requiredPackStatus.get(id)' in source
# Static integration checks: these prevent regressions in the Paper adapter,
# but do not substitute for a real Paper API build or live client test.
assert 'recordPackStatus(sourcePlayer, id, pack, status)' in source
assert 'Bukkit.getPlayer(id) != sourcePlayer' in source
import re
pack_handler = source.split('private void recordPackStatus(',1)[1].split('private void tick()',1)[0]
assert re.search(r'else\s*\{[^{}]*rig\.disconnect\(id\);[^{}]*requested\.remove\(id\);', pack_handler, flags=re.S)
assert 'probe.x' in source and 'saveConfig()' in source
assert 'getScoreboardTags().contains(TAG)' in source
assert 'display.teleport(' not in source
assert 'net.milkbowl.vault' not in source and 'EconomyResponse' not in source
assert 'sr_players' not in source
assert 'BridgeClient' not in source
assert 'api/wardrobe' not in source
assert (root/'src/main/resources/plugin.yml').read_text().count('axiomrig:')==1
assert 'enabled: false' in (root/'src/main/resources/config.yml').read_text()
assert 'paper-api' in (root/'pom.xml').read_text()
model_zip=root/'test-assets/AxiomWardrobe-Wings-Calibration-0.9.0-probe.zip'
with ZipFile(model_zip) as z:
    assert z.testzip() is None
    files=set(z.namelist())
    assert 'assets/theaxiom/items/wings_nether_probe.json' in files
    assert 'assets/theaxiom/models/item/wings_nether_probe.json' in files
    assert 'assets/theaxiom/textures/item/wings_nether_probe.png' in files
    meta=json.loads(z.read('assets/theaxiom/items/wings_nether_probe.json'))
    assert meta['model']['model']=='theaxiom:item/wings_nether_probe'
# Once built with real API, verify actual jar includes no bundled Bukkit classes.
jar=root/'target/AxiomRigProbe-0.9.6-qa5.jar'
if jar.exists():
    with ZipFile(jar) as z:
        assert z.testzip() is None
        assert 'plugin.yml' in z.namelist()
        assert 'online/theaxiom/wardrobe/probe/AxiomRigProbePlugin.class' in z.namelist()
        assert not any(n.startswith(('org/bukkit/','io/papermc/','org/joml/')) for n in z.namelist())
    print('BUILT_JAR_CONTENT_CHECK=PASS')
else: print('BUILT_JAR_CONTENT_CHECK=NOT_RUN (no true Paper API compilation)')
print('PROJECT_STRUCTURE_CHECK=PASS')
print('PAPER_API_COMPILATION=NOT_RUN_IN_THIS_ENVIRONMENT')
