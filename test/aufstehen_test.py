"""Roborock wartet nach dem Aufstehen. Start: python3 test/aufstehen_test.py"""
import os, sys
from datetime import datetime
from zoneinfo import ZoneInfo
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import types
for name in ["roborock", "roborock.data", "roborock.devices", "roborock.devices.device_manager", "roborock.web_api", "kalender"]:
    sys.modules.setdefault(name, types.ModuleType(name))
sys.modules["roborock"].RoborockCommand = None
sys.modules["roborock.data"].UserData = None
sys.modules["roborock.devices.device_manager"].UserParams = None
sys.modules["roborock.devices.device_manager"].create_device_manager = None
sys.modules["roborock.web_api"].RoborockApiClient = None
from aufgaben.roborock import aufstehen_warten
import aufstehen

B = ZoneInfo("Europe/Berlin")
a = {"nach_aufstehen_minuten": 60, "ohne_aufstehen_ab": "12:30"}
t = lambda h, m=0: datetime(2026, 11, 27, h, m, tzinfo=B)
assert aufstehen_warten(a, t(10, 30), aufgestanden=t(10)) == "wartet bis 11:00 (eine Stunde nach dem Aufstehen)"
assert aufstehen_warten(a, t(11, 0), aufgestanden=t(10)) is None
assert "ohne Meldung" in aufstehen_warten(a, t(11), aufgestanden=False)
assert aufstehen_warten(a, t(12, 30), aufgestanden=False) is None
assert aufstehen_warten({}, t(10)) is None
# Kanal wie in der App (Signal.kt)
assert aufstehen.kanal("abc").startswith("raumklima-") and len(aufstehen.kanal("abc")) == 34
print("aufstehen ok")
