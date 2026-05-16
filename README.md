# BlitzWolf / Roome BLE Lamp Controller

Python tool to control BlitzWolf (Roome) BLE lamps over Bluetooth Low Energy, reverse-engineered from the BlitzWolf Android APK.

Tested with **RoomeLightMini** devices.

## Setup

```bash
cd test_app
python -m venv venv
venv\Scripts\activate    # Windows
# source venv/bin/activate  # Linux/macOS
pip install -r requirements.txt
```

## Usage

### Scan for devices

```bash
python blitzwolf_lamp.py scan
python blitzwolf_lamp.py scan -t 20   # 20 second scan
```

### Control a lamp

Use a MAC address or a configured alias (see `KNOWN_DEVICES` in the script):

```bash
python blitzwolf_lamp.py -a lamp1 on           # Turn on (brightness 100)
python blitzwolf_lamp.py -a lamp1 on 50        # Turn on at 50% brightness
python blitzwolf_lamp.py -a lamp1 off          # Turn off
python blitzwolf_lamp.py -a lamp1 brightness 75
python blitzwolf_lamp.py -a lamp1 color 255 0 0       # Red (RGB 0-255)
python blitzwolf_lamp.py -a lamp1 warm 100 0          # Warm white
python blitzwolf_lamp.py -a lamp1 status               # Query brightness
python blitzwolf_lamp.py -a lamp1 firmware             # Query firmware version
python blitzwolf_lamp.py -a lamp1 interactive          # Interactive REPL
```

MAC addresses work too:

```bash
python blitzwolf_lamp.py -a C5:72:80:E2:F2:16 on
```

### Control all known lamps

```bash
python blitzwolf_lamp.py --all on
python blitzwolf_lamp.py --all off
python blitzwolf_lamp.py --all brightness 50
python blitzwolf_lamp.py --all status
```

### Interactive mode

```bash
python blitzwolf_lamp.py -a lamp1 interactive
```

Available commands in the REPL:

| Command | Description |
|---------|-------------|
| `on [brightness]` | Turn on (0-100, default 100) |
| `off` | Turn off |
| `brightness <0-100>` | Set brightness |
| `color <R> <G> <B>` | Set RGB color (0-255 each) |
| `warm <warm> <cool>` | Set warm/cool white (0-100 each) |
| `auto <on\|off>` | Auto brightness control |
| `sleep <minutes>` | Sleep timer |
| `status` | Query current brightness |
| `firmware` | Query firmware version |
| `raw <hex>` | Send raw hex command |
| `help` | Show help |
| `quit` | Disconnect |

## Configuration

Edit the constants at the top of `blitzwolf_lamp.py`:

```python
# Known devices — use alias instead of MAC address: -a lamp1
KNOWN_DEVICES = {
    "lamp1": "C5:72:80:E2:F2:16",  # RoomeLightMini-f216
    "lamp2": "FC:FB:D9:CA:76:F5",  # RoomeLightMini-76f5
    "lamp3": "EB:53:F2:ED:59:3F",  # RoomeLightMini-593f
}

# Device clips brightness=0 to this minimum; treat as OFF
BRIGHTNESS_OFF_THRESHOLD = 3

# Seconds to wait for device responses after sending commands
RESPONSE_WAIT_TIME = 1.0
```

## BLE Protocol

Communication uses the **Nordic UART Service (NUS)** over BLE:

| Characteristic | UUID | Direction |
|---------------|------|-----------|
| Service | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | — |
| RX (write) | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | App → Device |
| TX (notify) | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | Device → App |

### Connection handshake

A handshake is required before commands are accepted:

1. Send `01 02` → device replies `01 04`
2. Send ACK `01 00 01 04 00 00`
3. Device is now ready for commands

### Command frame format

```
01 <cmd_id> <01=write | 00=read> [params...]
```

### Key commands

| Command | Hex | Description |
|---------|-----|-------------|
| Set brightness | `01 4F 01 <bright> 00` | brightness 0-100 |
| Full light control | `01 49 01 <on> <bright> <warm> <cool> <R> <G> <B>` | All params 0-100/255 |
| Query brightness | `01 4F 00` | Returns current brightness |
| Query firmware | `01 83 00 00 00 00` | Returns version |
| Auto control | `01 4A 01 <enable> <sensitivity>` | 0/1 enable, 0-255 sensitivity |
| Sleep timer | `01 4B 01 <high> <low>` | Seconds as 16-bit value |

### Response format

**ACK** (byte[1] == 0x00): `01 00 XX <cmd_id> <success> <hasExtra> [data...]`

**Push** (byte[1] != 0x00): `01 <cmd_id> [data...]`

Status pushes (`0x81`) contain on/off state and brightness. The app sends a receipt ACK `01 00 01 81 00 00` after each push.

## Notes

- The device clips brightness=0 to a minimum of ~3. There is no separate "off" command; the script treats brightness ≤ 3 as OFF.
- Battery query (`0x7E`) is only supported on Roome Switch devices, not lights.
- The `0x49` read command returns stored defaults (all zeros), not live state. Use `0x4F` read for actual brightness.

## Android App

A native Android app with the same BLE control functionality, built with Kotlin and Jetpack Compose.

### Features

- **Scan** for nearby BLE devices
- **Save** discovered devices with custom names (persisted across app restarts)
- **Rename** or **delete** saved devices
- On/off, brightness, warm/cool white, RGB color control
- Color presets (red, green, blue, yellow, purple, cyan)
- Auto brightness, sleep timer
- Status, battery, and firmware queries
- Raw hex command input
- BLE log viewer

### Building

Open the `android/` folder in Android Studio, or build from the command line:

```bash
cd android
./gradlew assembleDebug
```

The debug APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.

For a signed release build: **Build > Generate Signed App Bundle / APK** in Android Studio.

### Requirements

- Android 8.0+ (API 26)
- Bluetooth LE support
- Location permission (required by Android for BLE scanning)
