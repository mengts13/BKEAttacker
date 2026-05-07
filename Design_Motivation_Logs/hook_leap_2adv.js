// hook_set_advertising_data.js

// 1. Define base address and offsets
const libbluetoothBase = Module.findBaseAddress("libbluetooth.so");
// const targetOffset = 0x33D7E8; // Replace with actual offset
// const respTargetOffset = 0x33DA08;

// Pixel 4
const targetOffset = 0x33A34C;
const respTargetOffset = 0x33A50C;
const targetAddr = libbluetoothBase.add(targetOffset);
const respTargetAddr = libbluetoothBase.add(respTargetOffset);

// 2. Validate address validity
if (libbluetoothBase.isNull() || targetAddr.isNull()) {
    console.error("[-] libbluetooth.so or target function not found!");
    exit();
}

console.log(`[+] libbluetooth.so base: ${libbluetoothBase}`);
console.log(`[+] Target function address: ${targetAddr}`);
console.log(`[+] Resp Target function address: ${respTargetAddr}`);

// 3. Hook the target function
Interceptor.attach(targetAddr, {
    onEnter: function(args) {
        // Parse arguments (based on reversed function signature)
        const handle = args[1].toUInt32(); // uint8_t handle
        const operation = args[2].toUInt32(); // uint64_t operation (might actually be uint8_t; verify)
        const fragment_preference = args[3].toUInt32(); // uint8_t fragment_preference
        const data_length = args[4].toUInt32(); // uint8_t data_length
        const data_ptr = args[5]; // uint8_t* data
        console.log(`[+][+][+]  data_ptr: ${data_ptr}`);
        console.log(`[+][+][+][+]  Current handle: ${handle}, data length: ${data_length}`);

        // Read advertising data
        if (data_ptr && data_length > 0) {
            try {
                const data = Memory.readByteArray(data_ptr, data_length);
                const bytes1 = Array.from(new Uint8Array(data));
                console.log(`[+] data: ${bytes1.map(b => b.toString(16).padStart(2, '0')).join('')}`);
                console.log(`[+] data_ptr type: ${typeof data_ptr}`);
                console.log(`[+] data_ptr: ${data_ptr}`);
                console.log(`[+] handle: ${handle}`);
                console.log(`[+] operation: ${operation}`);
                console.log(`[+] fragment_preference: ${fragment_preference}`);
                console.log(`[+] data_length type: ${typeof data_length}`);
                console.log(`[+] args[4] type: ${typeof args[4]}`);
                console.log(`[+] data_length: ${data_length}`);

                let bytes = [];
                for (let i = 0; i < data_length; i++) {
                    bytes.push(Memory.readU8(data_ptr.add(i)));
                }
                console.log(`[+] data: ${bytes.map(b => b.toString(16).padStart(2, '0')).join('')} (Hex)`);
            } catch (e) {
                console.error(`[-] Failed to read data: ${e}`);
            }
        }

        const originaldata = Memory.readByteArray(data_ptr, data_length);
        const originalArray = Array.from(new Uint8Array(originaldata));
        console.log(`[+] Original data: ${originalArray.map(b => b.toString(16).padStart(2, '0')).join('')}`);

        // Modify advertising data based on handle
        let newData = [];

        if (handle === 0x00) { // Advertising set 1
            newData = [
                0x05, 0x02, 0xFE, 0xFF, 0x09, 0x01, 0x05, 0x04,
                0x00, 0x00, 0x00, 0x00, 0x11, 0x07, 0x6E, 0xEF,
                0xE9, 0xB2, 0x3A, 0xBA, 0xF1, 0xFE, 0x0B, 0xE0,
                0x35, 0xFA, 0x2E, 0xE4, 0x29, 0xB6
            ];
        } else if (handle === 0x01) { // Advertising set 2
            newData = [
                0x02, 0x01, 0x06,
                // Second field: Manufacturer Specific Data
                0x1A, 0xFF, 0x4C, 0x00, 0x02, 0x15, 0xFE, 0xFF, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC5
            ];
        } else {
            console.log(`[+] Ignoring unknown handle: ${handle}`);
            return;
        }

        const newDataLength = newData.length;

        Memory.writeByteArray(data_ptr, newData);
        args[4] = ptr(newDataLength);
        console.log(newDataLength);

        let bytes2 = [];
        for (let i = 0; i < newDataLength; i++) {
            bytes2.push(Memory.readU8(data_ptr.add(i)));
        }
        console.log(`[+] Modified data: ${bytes2.map(b => b.toString(16).padStart(2, '0')).join('')}`);
    },
    onLeave: function(retval) {
        // Optional: monitor return value
    }
});

// 4. Hook response target function
Interceptor.attach(respTargetAddr, {
    onEnter: function(args) {
        // Parse arguments (based on reversed function signature)
        const handle = args[1].toUInt32(); // uint8_t handle
        const operation = args[2].toUInt32(); // uint64_t operation (might actually be uint8_t; verify)
        const fragment_preference = args[3].toUInt32(); // uint8_t fragment_preference
        const data_length = args[4].toUInt32(); // uint8_t data_length
        const data_ptr = args[5]; // uint8_t* data
        console.log(`[+][+][+]  data_ptr: ${data_ptr}`);
        console.log(`[+][+][+]   data_length: ${data_length}`);

        // Read advertising data
        if (data_ptr && data_length > 0) {
            try {
                const data = Memory.readByteArray(data_ptr, data_length);
                const bytes1 = Array.from(new Uint8Array(data));
                console.log(`[+] data: ${bytes1.map(b => b.toString(16).padStart(2, '0')).join('')}`);
                console.log(`[+] data_ptr type: ${typeof data_ptr}`);
                console.log(`[+] data_ptr: ${data_ptr}`);
                console.log(`[+] handle: ${handle}`);
                console.log(`[+] operation: ${operation}`);
                console.log(`[+] fragment_preference: ${fragment_preference}`);
                console.log(`[+] data_length type: ${typeof data_length}`);
                console.log(`[+] args[4] type: ${typeof args[4]}`);
                console.log(`[+] data_length: ${data_length}`);

                let bytes = [];
                for (let i = 0; i < data_length; i++) {
                    bytes.push(Memory.readU8(data_ptr.add(i)));
                }
                console.log(`[+] data: ${bytes.map(b => b.toString(16).padStart(2, '0')).join('')} (Hex)`);
            } catch (e) {
                console.error(`[-] Failed to read data: ${e}`);
            }
        }

        const originaldata = Memory.readByteArray(data_ptr, data_length);
        const originalArray = Array.from(new Uint8Array(originaldata));
        console.log(`[+] Original data: ${originalArray.map(b => b.toString(16).padStart(2, '0')).join('')}`);

        // Modify advertising data based on handle
        let newData = [];

        if (handle === 0x00) { // Advertising set 1
            newData = [
                0x05, 0x08,
                0x30, 0x30, 0x30, 0x30, 0x0B, 0xFF, 0xAA, 0xAA,
                0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11
            ];
        } else if (handle === 0x01) { // Advertising set 2
            newData = [
                0x05, 0x08, 0x30, 0x30, 0x30, 0x30,
                // Fourth field: Manufacturer Specific Data
                0x0B, 0xFF, 0xAA, 0xAA, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11
                // Fifth field: Invalid type (possibly padding)
            ];
        } else {
            console.log(`[+] Ignoring unknown handle: ${handle}`);
            return;
        }

        const newDataLength = newData.length;

        Memory.writeByteArray(data_ptr, newData);
        args[4] = ptr(newDataLength);
        console.log(newDataLength);

        let bytes2 = [];
        for (let i = 0; i < newDataLength; i++) {
            bytes2.push(Memory.readU8(data_ptr.add(i)));
        }
        console.log(`[+] Modified data: ${bytes2.map(b => b.toString(16).padStart(2, '0')).join('')}`);
    },
    onLeave: function(retval) {
        // Optional: monitor return value
    }
});

// Offsets for SetRandomAddress functions in different advertiser implementations
const OFFSETS = [
    0x33A6CC, // BleAdvertiserHciExtendedImpl::SetRandomAddress
    0x33B94C, // BleAdvertiserVscHciInterfaceImpl::SetRandomAddress
    0x33C988  // BleAdvertiserLegacyHciInterfaceImpl::SetRandomAddress
];

const libBluetooth = Module.findBaseAddress("libbluetooth.so");

if (libBluetooth.isNull()) {
    console.log("[!] libbluetooth.so not found!");
    exit();
}

console.log(`[+] libbluetooth.so base address: ${libBluetooth}`);

// New MAC address (30:AF:7E:A9:55:D0)
const newMacBytes = [0x30, 0xAF, 0x7E, 0xA9, 0x55, 0xD0];

OFFSETS.forEach((offset, index) => {
    const address = libBluetooth.add(offset);
    console.log(`[+] Hooking function at offset 0x${offset.toString(16)} (addr: ${address})`);

    Interceptor.attach(address, {
        onEnter: function (args) {
            // Get raw_address pointer (in x2 register on ARM64)
            const rawAddressPtr = this.context.x2;

            // Read original MAC address
            const originalMacBytes = Memory.readByteArray(rawAddressPtr, 6);
            const originalUint8 = new Uint8Array(originalMacBytes);
            const originalMacStr = Array.from(originalUint8)
                .map(b => b.toString(16).padStart(2, '0'))
                .join(':')
                .toUpperCase();

            console.log(`\n[+] Function ${index + 1} called!`);
            console.log(`    Base: ${libBluetooth}`);
            console.log(`    Address: ${address}`);
            console.log(`    Original MAC: ${originalMacStr}`);

            // Allocate temporary memory and write new MAC address
            const newMacBuffer = Memory.alloc(newMacBytes.length);
            for (let i = 0; i < newMacBytes.length; i++) {
                newMacBuffer.add(i).writeU8(newMacBytes[i]);
            }

            // Write new MAC address to target memory
            Memory.copy(rawAddressPtr, newMacBuffer, newMacBytes.length);

            // Verify written result
            const writtenMacBytes = Memory.readByteArray(rawAddressPtr, 6);
            const writtenUint8 = new Uint8Array(writtenMacBytes);
            const writtenMacStr = Array.from(writtenUint8)
                .map(b => b.toString(16).padStart(2, '0'))
                .join(':')
                .toUpperCase();

            console.log(`    New MAC: ${writtenMacStr}`);
            console.log(`    Stack trace:\n${Thread.backtrace(this.context, Backtracer.ACCURATE).map(DebugSymbol.fromAddress).join("\n")}`);
        }
    });
});