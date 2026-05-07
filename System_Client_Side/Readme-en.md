# Client Code

This repository contains two parts:

- `kcp-client`: Used to build the native shared library `libkcp_client.so` for Android.
- `BKEAttacker`: An Android App project that uses the `libkcp_client.so` built above.

> In other words: `kcp-client` is responsible for producing the `.so`, and `BKEAttacker` is responsible for consuming the `.so`.

## 1. How to Build kcp-client (Generate libkcp_client.so)

Linux environment.

`kcp-client` is a mixed **Go + C** project:

- On the Go side, `go build -buildmode=c-shared` is used to generate `libgo_kcp_client.so` (and the corresponding header file).
- On the C side, several “glue-layer” source files are compiled into the final `libkcp_client.so` for the Android App, and linked against `libgo_kcp_client.so`.

### 1.1 Prerequisites

- Go (using Go modules; dependencies are defined in `kcp-client/go.mod`)
- Android NDK (must be installed separately, and the path must be configured in the script)

### 1.2 Configure the NDK Path (Required)

The build script is located at: `kcp-client/ndk_build_client.sh`.

At the top of the script there is a field:

- `NDK_ROOT="..."`

You must change it to the NDK installation path on your system.

### 1.3 Build

Run the following in the `kcp-client` directory:

```bash
bash ndk_build_client.sh
```

Default script parameters:

- `API_LEVEL=21`
- Build ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Output directory: `kcp-client/src/android-libs/<ABI>/`

### 1.4 Build Artifacts

After a successful build, the following files will be generated (per ABI):

- `kcp-client/src/android-libs/arm64-v8a/libkcp_client.so`
- `kcp-client/src/android-libs/armeabi-v7a/libkcp_client.so`
- `kcp-client/src/android-libs/x86_64/libkcp_client.so`

## 2. Directory Structure

```
.
├── BKEAttacker/                 # Android project (Gradle)
│   ├── app/
│   │   ├── build.gradle
│   │   └── src/
│   │       └── main/
│   │           ├── AndroidManifest.xml
│   │           ├── assets/      # Resources and configuration (e.g., config.yaml, HTML pages)
│   │           ├── java/        # App code
│   │           ├── jniLibs/     # Native libraries (place .so per ABI)
│   │           └── res/
│   ├── build.gradle
│   ├── gradle/
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── local.properties
│   └── settings.gradle
├── kcp-client/                  # Go + C native library project (Go modules)
│   ├── go.mod
│   ├── go.sum
│   ├── ndk_build_client.sh      # One-click build script (multi-ABI)
│   └── src/
│       ├── android-libs/        # Build output directory (outputs .so per ABI)
│       ├── c/                   # C glue-layer source code
│       └── go/                  # Go source code (buildmode=c-shared)
├── Readme-cn.md
└── Readme-en.md
```

## 3. Integrate libkcp_client.so into the Android Project (BKEAttacker)

Copy the `.so` files built by `kcp-client` into the Android project’s `jniLibs` directory (placed per ABI):

```
BKEAttacker/app/src/main/jniLibs/
  arm64-v8a/libkcp_client.so
  armeabi-v7a/libkcp_client.so
  x86_64/libkcp_client.so
```

Then open `BKEAttacker/` in Android Studio and Build/Run directly.

BKEAttacker Usage Guide

BKEAttacker App Usage Guide (UI and Operation Flow)

## 1. What the App Is

BKEAttacker is an Android app serving as the client for executing the actual attack.

## 2. Initial Page Logic (What You See When You Open the App)

Normal case:

- After launch, the app navigates to the login page.

Special case:

- If a long-token already exists locally and auto-reconnection succeeds,
  the app will skip the login page and go directly to the identity selection and pairing page.

## 3. Preparation Before Use

1) The phone must support BLE (Bluetooth Low Energy).

2) During the process, the app will request permissions:

- Camera permission: used for QR-code pairing.
- Bluetooth-related permissions: used for scanning/connecting/advertising/attack workflow.

3) Server configuration is loaded from `assets/config.yaml` (the login page reads it and pre-fills the server list).

## 4. Typical Workflow

This app supports two roles:

- V: impersonate the vehicle (stay close to the victim’s person).
- U: impersonate the owner (stay close to the victim’s vehicle).

### Step 1: Login (Login Page)

1. Open the app to enter the login page.
2. Select a server (from `config.yaml`).
3. Tap “Login”.
4. After a successful login, you will enter the identity selection and pairing page.

### Step 2: Select a Role and Enter the Pairing Flow

1. On the “Identity Selection & Pairing” page, choose a role:

   - Impersonate the vehicle (V): stay close to the victim’s person.
   - Impersonate the owner (U): stay close to the victim’s vehicle.

2. After selecting a role successfully:

   - The page will generate/refresh a local pairing QR code.
   - The “QR Scan Pairing” and “PIN Pairing” buttons will become available.

### Step 3: Pairing (Choose One of Two Methods)

#### A) QR-Code Pairing

1. Tap “QR Scan Pairing”.
2. Grant camera permission.
3. Scan the QR code displayed by the other device.
4. After a successful scan, the process continues (the server system performs pairing/registration).

#### B) PIN Pairing

1. Tap “PIN Pairing” to enter the PIN pairing page.
2. The PIN pairing page shows “My Pairing PIN” (generated/cached during the Identity Selection & Pairing stage).
3. Enter the other party’s 6-digit PIN in the input box and confirm.
4. After a successful pairing, the app automatically navigates:

   - If your local role is V, you will enter the vehicle-side attack panel.
   - If your local role is U, you will enter the owner-side scanning and synchronization flow.

### Step 4: U Side (Impersonate Owner) — Scan and Sync

Prerequisite: you selected U and completed pairing, then entered the owner-side “Broadcast Scan & Sync” page.

1. On the owner-side “Broadcast Scan & Sync” page, choose a scan mode:

   - Manual scan (no input required)
   - Scan by MAC (enter the target MAC)
   - Scan by name (enter the target name)

2. Tap “Start Scan”.
3. After scanning completes, the app automatically navigates to the broadcast selection page.
4. On the broadcast selection page:

   - Tap a device to select it (enables the “Details/Confirm Selection” buttons).
   - Tap “Details” to open the device details page to view:
     - Parsing and display of `adv_data` / `adv_resp`
     - Display of the GATT service tree
   - Tap “Confirm Selection” to return to the owner-side “Broadcast Scan & Sync” page for the synchronization step.

5. Back on the owner-side “Broadcast Scan & Sync” page, tap “Broadcast Sync”.

   - This action syncs the selected broadcast data (and possibly GATT data) to the other side.
   - After a successful sync, you will enter the owner-side attack operation page.

### Step 5: V Side (Impersonate Vehicle) — Receive Data and Start the Attack (Vehicle Attack Panel)

Prerequisite: you selected V and completed pairing, then entered the vehicle-side attack panel.

1. After entering the vehicle-side attack panel, the V side waits to receive data synchronized from the U side:

   - `A-` prefix: broadcast data
   - `S-` prefix: service (GATT) data

2. Once all data has been received, the “Start Attack” button becomes enabled.
3. Tap “Start Attack” to start the workflow (the button changes to “Attacking”).
4. The log area will display send/receive and status information.

### Step 6: U Side Starts the Attack (Owner Attack Operation Page)

Prerequisite: after data synchronization completes, the U side automatically enters the owner-side attack operation page.

1. Tap “Start Attack”; the button switches to “Attacking”.
2. The log area displays send/receive/status information.
3. Tap “Attacking” to stop (if stop logic is implemented).

## 5. Quick Page Reference

- Login page: choose server + login
- Identity selection & pairing page: choose role + choose QR or PIN pairing
- PIN pairing page: enter the other party’s PIN
- Owner: broadcast scan & sync page: start scan + start sync
- Broadcast selection page: scan list (select device / view details / confirm selection)
- Device details page: device details (broadcast parsing + GATT tree)
- Vehicle attack panel: wait for data, then start attack when enabled
- Owner attack operation page: start attack + view logs
- Account settings page: account settings / logout

```
