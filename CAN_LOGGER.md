# CAN Logger

Open Launcher includes a read-only CAN Logger screen for headunit diagnostics.

## What It Captures

- Known Android broadcasts used by common MCU/CAN headunit stacks, including szchoiceway and microntek-style actions.
- Byte-array, frame-like, and `123#11223344` style payloads found inside broadcast extras.
- Changes in Android `Settings.Global` keys that look related to CAN, MCU, vehicle, doors, lights, radio, or steering controls.
- Visible SocketCAN interface names such as `can0` or `vcan0`, when the Android system exposes them under `/sys/class/net`.

The logger does not transmit CAN frames.

## How To Use It

1. Open the new bug-report/CAN icon in the sidebar.
2. Press `START`.
3. Add a marker before each test, for example `driver door open`, `lights on`, or `reverse`.
4. Perform one vehicle action at a time.
5. Press `SAVE` or `STOP`.
6. Pull the CSV from the path shown on screen, usually under:

```text
/sdcard/Android/data/com.openlauncher.app/files/can-logs/
```

## If No Traffic Appears

Many Android headunits do not expose raw vehicle CAN frames to apps. The CAN adapter may decode frames inside the MCU and only pass selected events to Android.

If this screen stays quiet while vehicle data is clearly working elsewhere, use an external CAN interface for raw decoding, such as a CANable/candleLight adapter, OBDLink/STN adapter, or SocketCAN-capable hardware connected to the correct vehicle bus.

