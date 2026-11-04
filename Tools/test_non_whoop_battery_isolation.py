#!/usr/bin/env python3
"""Guard: a non-WHOOP source's battery never lands in the WHOOP strap's battery field.

The live `batteryPct` on both platforms is the WHOOP strap's charge. It survives a source switch,
banks into the strap's runtime estimate and drives the strap's low/full battery alerts. Both
coordinators used to wire every non-WHOOP source's `onBattery` (a Wahoo or Polar 0x2A19, an FTMS
machine, a Huami band, an Oura ring) straight into it, so a ride on a Wahoo left the Wahoo's charge
under "Strap battery" and in the strap's discharge fit. The non-WHOOP charge now has its own field
(`sourceBatteryPct`, beside the ring's `ouraBatteryPct`), mirrored from the live source.

This reads product source, so it runs on the unfiltered tools-python leg. Run with::

    python3 Tools/test_non_whoop_battery_isolation.py
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SWIFT_COORDINATOR = ROOT / "Strand/BLE/SourceCoordinator.swift"
KOTLIN_COORDINATOR = ROOT / "android/app/src/main/java/com/noop/ble/SourceCoordinator.kt"
KOTLIN_APPLICATION = ROOT / "android/app/src/main/java/com/noop/NoopApplication.kt"
KOTLIN_BLE_CLIENT = ROOT / "android/app/src/main/java/com/noop/ble/WhoopBleClient.kt"


def code_only(text: str) -> str:
    """Drop // line comments and /* */ blocks so prose that names the old wiring does not count."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return "\n".join(line.split("//", 1)[0] for line in text.splitlines())


class NonWhoopBatteryIsolationTest(unittest.TestCase):
    def test_swift_coordinator_never_writes_the_strap_battery(self) -> None:
        code = code_only(SWIFT_COORDINATOR.read_text(encoding="utf-8"))
        self.assertNotIn("setBattery", code,
                         "SourceCoordinator.swift builds only non-WHOOP sources; their charge must not "
                         "reach LiveState.setBattery (the WHOOP strap's battery).")
        self.assertNotRegex(code, r"onBattery\s*:",
                            "a non-WHOOP source's onBattery must not be wired to the live strap battery")
        self.assertNotRegex(code, r"live\.batteryPct\s*=",
                            "a non-WHOOP source's charge belongs in live.sourceBatteryPct")
        self.assertIn("live.sourceBatteryPct = pct", code,
                      "the active generic strap's own charge must reach live.sourceBatteryPct")

    def test_kotlin_coordinator_has_no_battery_sink(self) -> None:
        code = code_only(KOTLIN_COORDINATOR.read_text(encoding="utf-8"))
        self.assertNotIn("batterySink", code)
        self.assertNotRegex(code, r"onBattery\s*=",
                            "a non-WHOOP source's onBattery must not be wired to the live strap battery")
        self.assertIn("_sourceBatteryPct.value = it", code,
                      "the active generic strap's own charge must reach sourceBatteryPct")

    def test_kotlin_ble_client_offers_no_external_battery_entry(self) -> None:
        for path in (KOTLIN_APPLICATION, KOTLIN_BLE_CLIENT):
            with self.subTest(path=path.name):
                self.assertNotIn("publishExternalBattery", code_only(path.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
