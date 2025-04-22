# Smart Login Conditions 

An Android app that lets you log in only when **specific smart conditions** are met. Ideal for showcasing creative access control using hardware sensors and smart device integration.

---

## Features

This project includes a login screen protected by **five unique smart login conditions**:

1. **Bluetooth Device Match**
   - Checks for nearby Bluetooth device (e.g. AirPods)
2. **Voice Command**
   - Login only after speaking a specific password ("Password")
3. **Call Match**
   - Type the name of your last incoming caller
4. **Device Spin Detection**
   - Detects two physical spins using the gyroscope
5. **Room Brightness Check**
   - Ensures room is bright enough using the light sensor

All conditions must be fulfilled for the login to succeed.

---

## Demo

[![Watch the demo](https://i.imgur.com/ZlJEZdF.gif)](https://i.imgur.com/ZlJEZdF.gif)

---

## Technologies Used

- Android SDK (API 26+)
- Firebase for call detection
- Sensor APIs
- Bluetooth APIs
- Runtime Permission Handling
- RecyclerView with custom adapter

---

## Getting Started

1. Clone this repo:
```bash
git clone https://github.com/YOUR_USERNAME/SmartLoginConditions.git
```

2. Open in Android Studio

3. Run on a real Android device (emulator won't work for sensors/Bluetooth)

---

## Permissions Required

Make sure to grant all necessary **runtime permissions**:

- `RECORD_AUDIO`
- `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
- `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`
- `ACCESS_FINE_LOCATION` (for Bluetooth scanning)

---

## Project Structure

```text
.
├── MainActivity.kt          # Handles UI and login flow
├── managers/
│   ├── BluetoothManager.kt     # Bluetooth scan + detection
│   └── SensorHandler.kt        # Gyroscope + Light Sensor
├── adapters/                  # RecyclerView adapter
├── models/                    # Condition model
├── utils/                     # PermissionUtils
└── CallReceiver.kt            # Handles incoming call detection
```


