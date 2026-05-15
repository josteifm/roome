"""
BlitzWolf / Roome BLE Lamp Controller
======================================
Reverse-engineered from the BlitzWolf Android APK.

Protocol: Nordic UART Service (NUS) over BLE
  - Service UUID:  6e400001-b5a3-f393-e0a9-e50e24dcca9e
  - RX (write):    6e400002-b5a3-f393-e0a9-e50e24dcca9e  (app → device)
  - TX (notify):   6e400003-b5a3-f393-e0a9-e50e24dcca9e  (device → app)

Command frame: 01 <cmd_id> <01=write|00=read> [params as hex bytes]

Requirements: pip install bleak
"""

import asyncio
import argparse
import sys
from bleak import BleakClient, BleakScanner

# Nordic UART Service UUIDs
NUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
NUS_RX_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  # Write (app → device)
NUS_TX_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  # Notify (device → app)

# Device name prefixes used by the app
DEVICE_NAME_PREFIXES = ("RoomeLightMini", "RoomeLight", "RoomeSwitch", "RoomeSwitchAce")

# Known devices — use alias instead of MAC address: -a lamp1
KNOWN_DEVICES = {
    "lamp1": "C5:72:80:E2:F2:16",  # RoomeLightMini-f216
    "lamp2": "FC:FB:D9:CA:76:F5",  # RoomeLightMini-76f5
    "lamp3": "EB:53:F2:ED:59:3F",  # RoomeLightMini-593f
}

# Command IDs
CMD_SWITCH_CONTROL = 0x43
CMD_BRIGHTNESS = 0x4F

# Device clips brightness=0 to this minimum; treat as OFF
BRIGHTNESS_OFF_THRESHOLD = 3

# Seconds to wait for device responses after sending commands
RESPONSE_WAIT_TIME = 0.0
CMD_LIGHT_FULL = 0x49
CMD_LIGHT_EXTENDED = 0x48
CMD_LIGHT_SAVE = 0x61
CMD_AUTO_CONTROL = 0x4A
CMD_DELAY = 0x4B
CMD_SLEEP_ON = 0x47
CMD_NO_PEOPLE_OFF = 0x46
CMD_REST_LIGHT = 0x4C
CMD_RECOVERY = 0x4D
CMD_SET_DATETIME = 0x06
CMD_QUERY_LIGHT = 0x82
CMD_QUERY_LIGHT_NEW = 0x8A
CMD_QUERY_ENV_LIGHT = 0x8B
CMD_QUERY_FIRMWARE = 0x83
CMD_QUERY_BATTERY = 0x7E
CMD_QUERY_MAC = 0x03
CMD_QUERY_DEVICE = None  # special: raw "0102"


def _hex(value: int) -> str:
    """Encode an int (0-255) as a 2-char hex string, as the app does."""
    return f"{value & 0xFF:02x}"


def _build_cmd(cmd_id: int, write: bool, *params: int) -> bytes:
    """Build a command frame and return as bytes."""
    hex_str = "01" + _hex(cmd_id) + ("01" if write else "00")
    for p in params:
        hex_str += _hex(p)
    return bytes.fromhex(hex_str)


# ── Command builders ─────────────────────────────────────────────────────

def cmd_set_brightness(brightness: int) -> bytes:
    """Set brightness (0-100). Frame: 01 4f 01 XX 00"""
    brightness = max(0, min(100, brightness))
    return _build_cmd(CMD_BRIGHTNESS, True, brightness, 0)


def cmd_light_full(on: int, brightness: int, warm: int, cool: int,
                   r: int, g: int, b: int) -> bytes:
    """Full light control. Frame: 01 49 01 [on] [bright] [warm] [cool] [R] [G] [B]"""
    return _build_cmd(CMD_LIGHT_FULL, True, on, brightness, warm, cool, r, g, b)


def cmd_power_on(brightness: int = 100, warm: int = 100, cool: int = 0) -> bytes:
    """Turn lamp on by setting brightness (0-100). Uses cmd 0x4F."""
    brightness = max(1, min(100, brightness))
    return cmd_set_brightness(brightness)


def cmd_power_off() -> bytes:
    """Turn lamp off by setting brightness to 0."""
    return cmd_set_brightness(0)


def cmd_set_color(r: int, g: int, b: int, brightness: int = 100) -> bytes:
    """Set RGB color (each 0-255), brightness 0-100."""
    return cmd_light_full(1, brightness, 0, 0, r, g, b)


def cmd_set_warm_white(brightness: int = 100, warm: int = 100, cool: int = 0) -> bytes:
    """Set warm/cool white balance (each 0-100)."""
    return cmd_light_full(1, brightness, warm, cool, 0, 0, 0)


def cmd_auto_control(enable: bool, sensitivity: int = 128) -> bytes:
    """Enable/disable auto brightness with optional sensitivity."""
    return _build_cmd(CMD_AUTO_CONTROL, True, 1 if enable else 0, sensitivity)


def cmd_sleep_timer(minutes: int) -> bytes:
    """Set sleep timer in minutes (auto-off after delay)."""
    high = (minutes >> 8) & 0xFF
    low = minutes & 0xFF
    return _build_cmd(CMD_SLEEP_ON, True, high, low, 1)


def cmd_query_state() -> bytes:
    """Query current brightness state (cmd 0x4F read mode)."""
    return _build_cmd(CMD_BRIGHTNESS, False)


def cmd_query_state_new() -> bytes:
    """Query ambient light sensor thresholds."""
    return _build_cmd(CMD_QUERY_LIGHT_NEW, False)


def cmd_query_battery() -> bytes:
    """Query battery level. Only supported on switch devices; lights ignore this command."""
    return _build_cmd(CMD_QUERY_BATTERY, False)


def cmd_query_firmware() -> bytes:
    """Query firmware version."""
    return _build_cmd(CMD_QUERY_FIRMWARE, False, 0, 0, 0)


def cmd_factory_reset() -> bytes:
    """Factory reset the device."""
    return _build_cmd(CMD_RECOVERY, True)


def cmd_query_device() -> bytes:
    """Ping / query device presence."""
    return bytes.fromhex("0102")


def cmd_handshake_ack() -> bytes:
    """Acknowledge the device's 0104 response. Required before commands work."""
    return bytes.fromhex("010001040000")


# ── BLE Communication ────────────────────────────────────────────────────

# Event used to signal handshake completion
_handshake_event = None  # Created lazily per event loop
_active_client = None  # Set when connected, for sending receipts


def _ensure_event():
    global _handshake_event
    _handshake_event = asyncio.Event()


def _receipt_ack(cmd_id_hex: str) -> bytes:
    """Build a receipt ACK: 01 00 01 <cmd_id> 00 00"""
    return bytes.fromhex(f"0100{cmd_id_hex}0000")


def notification_handler(sender, data: bytearray):
    """Handle incoming notifications from the lamp."""
    hex_data = data.hex()
    print(f"  ← RX [{len(data)} bytes]: {hex_data}")

    # Detect handshake response "0104"
    if hex_data == "0104":
        print("     [Handshake: device ready, sending ACK]")
        _handshake_event.set()
        return

    # Parse response format
    # ACK format (len>=6, byte[1]==0x00): 01 00 [xx] [cmd_id] [success] [hasExtra] [data...]
    # Push format (byte[1]!=0x00):        01 [cmd_id] [data...]
    if len(data) >= 2:
        if data[1] == 0x00 and len(data) >= 6:
            # ACK response
            cmd_id = data[3]
            success = data[4]
            has_extra = data[5]
            extra = data[6:]
            print(f"     ACK: cmd=0x{cmd_id:02x} success={success==0} hasExtra={has_extra==1}")
            _decode_ack(cmd_id, extra)
        else:
            # Device push / upload
            cmd_id = data[1]
            payload = data[2:]
            print(f"     Push: cmd=0x{cmd_id:02x} payload={payload.hex()}")
            _decode_push(cmd_id, payload)
            # Send receipt ACK for 0x81 pushes (as the app does)
            if cmd_id == 0x81 and _active_client:
                asyncio.ensure_future(
                    _active_client.write_gatt_char(
                        NUS_RX_UUID, _receipt_ack("81"), response=False
                    )
                )


def _decode_ack(cmd_id: int, extra: bytes):
    """Decode ACK response data."""
    if cmd_id == 0x49 and len(extra) >= 7:
        # Light state: on/off, brightness, warm, cool, R, G, B
        on_off, bright, warm, cool, r, g, b = extra[0], extra[1], extra[2], extra[3], extra[4], extra[5], extra[6]
        state = "ON" if on_off else "OFF"
        print(f"     Light state: {state} brightness={bright} warm={warm} cool={cool} RGB=({r},{g},{b})")
    elif cmd_id == 0x8a and len(extra) >= 2:
        # Ambient light sensor thresholds
        open_env = extra[0]
        close_env = extra[1]
        print(f"     Ambient light: open_threshold={open_env} close_threshold={close_env}")
    elif cmd_id == 0x7e and len(extra) >= 1:
        print(f"     Battery: {extra[0]}%")
    elif cmd_id == 0x83 and len(extra) >= 3:
        print(f"     Firmware: v{extra[0]}.{extra[1]}.{extra[2]}")
    elif cmd_id == 0x4f and len(extra) >= 1:
        bright = extra[0]
        state = "OFF" if bright <= BRIGHTNESS_OFF_THRESHOLD else "ON"
        print(f"     Brightness: {state} brightness={bright}")
    elif extra:
        print(f"     Data: {extra.hex()}")


def _decode_push(cmd_id: int, payload: bytes):
    """Decode device-initiated push notifications."""
    if cmd_id == 0x81 and len(payload) >= 5:
        on_off = payload[1]
        brightness = payload[3]
        state = "OFF" if brightness <= BRIGHTNESS_OFF_THRESHOLD else "ON"
        extra_vals = [payload[i] for i in range(4, len(payload))]
        print(f"     Status: {state} brightness={brightness} extra={extra_vals}")
    elif cmd_id in (0xe1, 0xe2) and len(payload) >= 6:
        print(f"     DateTime: 20{payload[0]:02d}-{payload[1]:02d}-{payload[2]:02d} "
              f"{payload[3]:02d}:{payload[4]:02d}:{payload[5]:02d}")
    elif cmd_id == 0x4f and len(payload) >= 1:
        print(f"     Brightness: {payload[0]}")


async def scan_devices(timeout: float = 10.0):
    """Scan for BlitzWolf/Roome BLE devices."""
    print(f"Scanning for BLE devices ({timeout}s)...")
    discovered = []

    def _callback(device, adv_data):
        discovered.append((device, adv_data))

    scanner = BleakScanner(detection_callback=_callback)
    await scanner.start()
    await asyncio.sleep(timeout)
    await scanner.stop()

    found = []
    for d, adv in discovered:
        name = adv.local_name or d.name or ""
        rssi = adv.rssi
        is_target = any(name.startswith(p) for p in DEVICE_NAME_PREFIXES)
        if is_target:
            found.append((d, adv))
            print(f"  ★ {d.address}  {name}  RSSI={rssi}")

    if not found:
        # Deduplicate by address, keep strongest RSSI
        seen = {}
        for d, adv in discovered:
            addr = d.address
            if addr not in seen or (adv.rssi or -999) > (seen[addr][1].rssi or -999):
                seen[addr] = (d, adv)
        print("\nNo Roome/BlitzWolf devices found. All visible devices:")
        for d, adv in sorted(seen.values(), key=lambda x: x[1].rssi or -999, reverse=True):
            name = adv.local_name or d.name or "(unknown)"
            print(f"    {d.address}  {name}  RSSI={adv.rssi}")
    return found


async def do_handshake(client: BleakClient) -> bool:
    """Perform the required handshake: send 0102, wait for 0104, send ACK."""
    _ensure_event()
    _handshake_event.clear()

    print("  → TX (query): 0102")
    await client.write_gatt_char(NUS_RX_UUID, cmd_query_device(), response=False)

    try:
        await asyncio.wait_for(_handshake_event.wait(), timeout=5.0)
    except asyncio.TimeoutError:
        print("  ⚠ Handshake timeout — device did not respond with 0104")
        return False

    ack = cmd_handshake_ack()
    print(f"  → TX (ack): {ack.hex()}")
    await client.write_gatt_char(NUS_RX_UUID, ack, response=False)
    await asyncio.sleep(0.3)
    print("  Handshake complete.")
    return True


async def connect_and_run(address: str, commands: list[bytes]):
    """Connect to a device, subscribe to notifications, and send commands."""
    print(f"Connecting to {address}...")
    async with BleakClient(address) as client:
        print(f"Connected: {client.is_connected}")

        # Subscribe to TX notifications
        await client.start_notify(NUS_TX_UUID, notification_handler)
        print("Subscribed to notifications")

        # Perform required handshake before sending commands
        global _active_client
        _active_client = client
        if not await do_handshake(client):
            print("Handshake failed. Trying commands anyway...")

        for cmd in commands:
            hex_str = cmd.hex()
            print(f"  → TX: {hex_str}")
            await client.write_gatt_char(NUS_RX_UUID, cmd, response=False)
            await asyncio.sleep(0.3)  # Brief pause between commands

        # Wait for responses
        print(f"Waiting for responses ({RESPONSE_WAIT_TIME}s)...")
        await asyncio.sleep(RESPONSE_WAIT_TIME)

        await client.stop_notify(NUS_TX_UUID)
        _active_client = None
        print("Done.")


async def interactive_mode(address: str):
    """Interactive control session."""
    print(f"Connecting to {address}...")
    async with BleakClient(address) as client:
        print(f"Connected: {client.is_connected}")
        await client.start_notify(NUS_TX_UUID, notification_handler)
        print("Subscribed to notifications")

        # Perform required handshake
        global _active_client
        _active_client = client
        if not await do_handshake(client):
            print("Handshake failed. Commands may not work.")
        print()

        help_text = """
Commands:
  on [brightness]       - Turn on (brightness 0-100, default 100)
  off                   - Turn off
  brightness <0-100>    - Set brightness
  color <R> <G> <B>     - Set RGB color (0-255 each)
  warm <warm> <cool>    - Set warm/cool white (0-100 each)
  auto <on|off>         - Auto brightness control
  sleep <minutes>       - Sleep timer
  status                - Query current state
  battery               - Query battery level
  firmware              - Query firmware version
  raw <hex>             - Send raw hex command
  help                  - Show this help
  quit                  - Disconnect and exit
"""
        print(help_text)

        while True:
            try:
                line = await asyncio.get_event_loop().run_in_executor(
                    None, lambda: input("lamp> ")
                )
            except (EOFError, KeyboardInterrupt):
                break

            parts = line.strip().split()
            if not parts:
                continue
            cmd_name = parts[0].lower()

            try:
                data = None
                if cmd_name == "quit" or cmd_name == "exit":
                    break
                elif cmd_name == "help":
                    print(help_text)
                    continue
                elif cmd_name == "on":
                    bright = int(parts[1]) if len(parts) > 1 else 100
                    data = cmd_power_on(brightness=bright)
                elif cmd_name == "off":
                    data = cmd_power_off()
                elif cmd_name == "brightness":
                    data = cmd_set_brightness(int(parts[1]))
                elif cmd_name == "color":
                    r, g, b = int(parts[1]), int(parts[2]), int(parts[3])
                    data = cmd_set_color(r, g, b)
                elif cmd_name == "warm":
                    w = int(parts[1]) if len(parts) > 1 else 100
                    c = int(parts[2]) if len(parts) > 2 else 0
                    data = cmd_set_warm_white(warm=w, cool=c)
                elif cmd_name == "auto":
                    enabled = parts[1].lower() in ("on", "1", "true")
                    data = cmd_auto_control(enabled)
                elif cmd_name == "sleep":
                    data = cmd_sleep_timer(int(parts[1]))
                elif cmd_name == "status":
                    data = cmd_query_state()
                elif cmd_name == "battery":
                    data = cmd_query_battery()
                elif cmd_name == "firmware":
                    data = cmd_query_firmware()
                elif cmd_name == "raw":
                    data = bytes.fromhex(parts[1])
                else:
                    print(f"Unknown command: {cmd_name}")
                    continue

                if data:
                    print(f"  → TX: {data.hex()}")
                    await client.write_gatt_char(NUS_RX_UUID, data, response=False)
                    await asyncio.sleep(0.5)

            except (ValueError, IndexError) as e:
                print(f"Error: {e}")

        await client.stop_notify(NUS_TX_UUID)
        _active_client = None
        print("Disconnected.")


# ── CLI ───────────────────────────────────────────────────────────────────

def _resolve_address(addr: str) -> str:
    """Resolve an alias or MAC address from KNOWN_DEVICES."""
    if addr.lower() in KNOWN_DEVICES:
        resolved = KNOWN_DEVICES[addr.lower()]
        print(f"  [{addr} → {resolved}]")
        return resolved
    return addr


def main():
    aliases = ", ".join(f"{k}" for k in KNOWN_DEVICES)
    parser = argparse.ArgumentParser(
        description="BlitzWolf / Roome BLE Lamp Controller",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=f"""
Known devices: {aliases}

Examples:
  %(prog)s scan                          # Scan for devices
  %(prog)s -a lamp1 on                   # Turn lamp1 on (using alias)
  %(prog)s -a lamp1 off                  # Turn lamp1 off
  %(prog)s --all on 50                   # Turn ALL known lamps on at 50%%
  %(prog)s -a lamp2 brightness 80
  %(prog)s -a AA:BB:CC:DD:EE:FF on       # Use MAC directly
  %(prog)s -a lamp1 color 255 0 0        # Red
  %(prog)s -a lamp1 status
  %(prog)s -a lamp1 interactive          # Interactive REPL
""")
    parser.add_argument("-a", "--address",
                        help=f"BLE device MAC address or alias ({aliases})")
    parser.add_argument("--all", action="store_true",
                        help="Send command to all known devices")
    parser.add_argument("-t", "--timeout", type=float, default=10.0,
                        help="Scan timeout in seconds (default: 10)")
    parser.add_argument("command", nargs="*", default=["scan"],
                        help="Command: scan, on, off, brightness, color, warm, status, "
                             "battery, firmware, interactive, raw")
    args = parser.parse_args()

    cmd_parts = args.command
    cmd_name = cmd_parts[0].lower() if cmd_parts else "scan"

    if cmd_name == "scan":
        asyncio.run(scan_devices(timeout=args.timeout))
        return

    # Determine target addresses
    if args.all:
        addresses = list(KNOWN_DEVICES.values())
    elif args.address:
        addresses = [_resolve_address(args.address)]
    else:
        print("Error: --address (-a) or --all is required for device commands.", file=sys.stderr)
        sys.exit(1)

    if cmd_name == "interactive":
        if len(addresses) > 1:
            print("Error: interactive mode only supports a single device.", file=sys.stderr)
            sys.exit(1)
        asyncio.run(interactive_mode(addresses[0]))
        return

    # Build command(s)
    commands = []
    if cmd_name == "on":
        bright = int(cmd_parts[1]) if len(cmd_parts) > 1 else 100
        commands.append(cmd_power_on(brightness=bright))
    elif cmd_name == "off":
        commands.append(cmd_power_off())
    elif cmd_name == "brightness":
        commands.append(cmd_set_brightness(int(cmd_parts[1])))
    elif cmd_name == "color":
        r, g, b = int(cmd_parts[1]), int(cmd_parts[2]), int(cmd_parts[3])
        commands.append(cmd_set_color(r, g, b))
    elif cmd_name == "warm":
        w = int(cmd_parts[1]) if len(cmd_parts) > 1 else 255
        c = int(cmd_parts[2]) if len(cmd_parts) > 2 else 0
        commands.append(cmd_set_warm_white(warm=w, cool=c))
    elif cmd_name == "auto":
        enabled = cmd_parts[1].lower() in ("on", "1", "true")
        commands.append(cmd_auto_control(enabled))
    elif cmd_name == "sleep":
        commands.append(cmd_sleep_timer(int(cmd_parts[1])))
    elif cmd_name == "status":
        commands.append(cmd_query_state())
    elif cmd_name == "ambient":
        commands.append(cmd_query_state_new())
    elif cmd_name == "battery":
        commands.append(cmd_query_battery())
    elif cmd_name == "firmware":
        commands.append(cmd_query_firmware())
    elif cmd_name == "reset":
        commands.append(cmd_factory_reset())
    elif cmd_name == "raw":
        commands.append(bytes.fromhex(cmd_parts[1]))
    else:
        print(f"Unknown command: {cmd_name}", file=sys.stderr)
        sys.exit(1)

    for addr in addresses:
        try:
            asyncio.run(connect_and_run(addr, commands))
        except Exception as e:
            print(f"Error with {addr}: {e}", file=sys.stderr)


if __name__ == "__main__":
    main()
