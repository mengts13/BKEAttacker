console.log("[*] Loading Bluetooth protocol stack comprehensive Hook script...");

var EnterDelay = 250;
// Basic utility functions
function bytesToUuid(bytes) {
    const hex = Array.from(bytes, b => ('0' + (b & 0xFF).toString(16)).slice(-2))
                    .join('').toUpperCase();
    return [
        hex.substr(0, 8),
        hex.substr(8, 4),
        hex.substr(12, 4),
        hex.substr(16, 4),
        hex.substr(20, 12)
    ].join('-');
}

function readBluetoothUuid(uuidAddr) {
    if (uuidAddr.isNull()) return "00000000-0000-0000-0000-000000000000";
    var uuidBytes = Memory.readByteArray(uuidAddr, 16);
    var bytes = new Uint8Array(uuidBytes);
    var hex = Array.from(bytes, b => b.toString(16).padStart(2, '0'));
    return [
        hex.slice(0,4).join(''),
        hex.slice(4,6).join(''),
        hex.slice(6,8).join(''),
        hex.slice(8,10).join(''),
        hex.slice(10).join('')
    ].join('-');
}

function readVector(vectorAddr, elementSize, readElementFunc) {
    if (vectorAddr.isNull()) return [];
    var _Myfirst = vectorAddr.readPointer();
    var _Mylast = vectorAddr.add(Process.pointerSize).readPointer();
    var count = _Mylast.sub(_Myfirst).toInt32() / elementSize;
    var elements = [];
    for (var i = 0; i < count; i++) {
        var elemAddr = _Myfirst.add(i * elementSize);
        elements.push(readElementFunc(elemAddr));
    }
    return elements;
}

// Struct offset definitions
const STRUCT_OFFSETS = {
    clcb: {
        p_srcb: 0x18
    },
    serv: {
        gatt_database: 0x10
    }
};

// GATT constant definitions
const GATT_WRITE_TYPE = {
    1: "GATT_WRITE_NO_RSP",
    2: "GATT_WRITE",
    3: "GATT_WRITE_PREPARE"
};
const GATT_HANDLE_MULTI_VALUE_NOTIF = 0x23;

// Get base address
const LIB_NAME = 'libbluetooth.so';
const baseAddr = Module.findBaseAddress(LIB_NAME);
if (!baseAddr) {
    console.error(`[-] Module not found: ${LIB_NAME}`);
    throw new Error(`Unable to locate ${LIB_NAME} base address`);
}
console.log(`[+] Base address: ${baseAddr}`);

// GATT database reading functions
function readDescriptor(descAddr) {
    if (descAddr.isNull()) return null;
    return {
        handle: descAddr.add(0x00).readU16(),
        uuid: readBluetoothUuid(descAddr.add(0x02)),
        extended_props: descAddr.add(0x12).readU16()
    };
}

function readIncludedService(servAddr) {
    if (servAddr.isNull()) return null;
    return {
        handle: servAddr.add(0x00).readU16(),
        uuid: readBluetoothUuid(servAddr.add(0x02)),
        start_handle: servAddr.add(0x12).readU16(),
        end_handle: servAddr.add(0x14).readU16()
    };
}

function readCharacteristic(charAddr) {
    if (charAddr.isNull()) return null;
    return {
        declaration_handle: charAddr.add(0x00).readU16(),
        uuid: readBluetoothUuid(charAddr.add(0x02)),
        value_handle: charAddr.add(0x12).readU16(),
        properties: charAddr.add(0x14).readU8(),
        descriptors: readVector(
            charAddr.add(0x18),
            0x14,
            readDescriptor
        )
    };
}

function readService(servAddr) {
    if (servAddr.isNull()) return null;
    return {
        handle: servAddr.add(0x00).readU16(),
        uuid: readBluetoothUuid(servAddr.add(0x02)),
        is_primary: servAddr.add(0x12).readU8(),
        end_handle: servAddr.add(0x14).readU16(),
        included_services: readVector(
            servAddr.add(0x18),
            0x16,
            readIncludedService
        ),
        characteristics: readVector(
            servAddr.add(0x30),
            0x30,
            readCharacteristic
        )
    };
}

// ================== Main Hook Functions ==================

// 1. GATT Database Service Discovery Hook
(function hookGattDiscovery() {
    const OFFSET = 0x1F3610;
    const targetAddr = baseAddr.add(OFFSET);
    Interceptor.attach(targetAddr, {
        onEnter: function(args) {
            this.p_clcb = args[0];
        },
        onLeave: function(retval) {
            console.log('--- Service discovery complete (bta_gattc_disc_cmpl) ---');
            var p_clcb = this.p_clcb;
            var p_srcb = p_clcb.add(STRUCT_OFFSETS.clcb.p_srcb).readPointer();
            var gattDatabaseAddr = p_srcb.add(STRUCT_OFFSETS.serv.gatt_database);
            var servicesListAddr = gattDatabaseAddr.readPointer();

            var _Myfirst = gattDatabaseAddr.readPointer();
            var _Mylast = gattDatabaseAddr.add(Process.pointerSize).readPointer();
            var _Mysize = gattDatabaseAddr.add(Process.pointerSize * 2).readU64();
            
            if (_Mysize == 0) {
                console.log("[!] No services found in the database");
                return;
            }

            var node = _Myfirst;
            var index = 0;
            while (!node.isNull()) {
                try {
                    var dataAddr = node.add(Process.pointerSize * 2);
                    var service = readService(dataAddr);
                    console.log(`Service [${index}]`);
                    console.log(`  Handle: 0x${service.handle.toString(16)} | UUID: ${service.uuid}`);
                    console.log(`  Type: ${service.is_primary ? "Primary Service" : "Secondary Service"} | End Handle: 0x${service.end_handle.toString(16)}`);
                    
                    // Characteristic output
                    service.characteristics.forEach((char, j) => {
                        console.log(`  Characteristic [${j}]`);
                        console.log(`    Declaration Handle: 0x${char.declaration_handle.toString(16)} | UUID: ${char.uuid}`);
                        console.log(`    Value Handle: 0x${char.value_handle.toString(16)} | Properties: 0x${char.properties.toString(16)}`);
                        
                        // Descriptor output
                        if (char.descriptors.length > 0) {
                            char.descriptors.forEach((desc, k) => {
                                console.log(`    Descriptor [${k}]`);
                                console.log(`      Handle: 0x${desc.handle.toString(16)} | UUID: ${desc.uuid}`);
                            });
                        }
                    });
                    index++;
                } catch (e) {
                    console.log(`Failed to read service: ${e}`);
                    break;
                }
                node = node.readPointer();
                if (index == _Mysize) {
                    break;
                }
            }
            console.log(`Total services found: ${index}`);
        }
    });
})();

// 2. GATTC Write Operation Hook
(function hookGattWrite() {
    const OFFSET = 0x38AD58;
    const funcAddr = baseAddr.add(OFFSET);
    Interceptor.attach(funcAddr, {
        onEnter: function(args) {
            Java.use("java.lang.Thread").sleep(EnterDelay);
            const conn_id = args[0].toUInt32();
            const type = args[1].toUInt32();
            const p_write = args[2];
            
            const structFields = {
                conn_id: p_write.readU16(),
                handle: p_write.add(2).readU16(),
                offset: p_write.add(4).readU16(),
                len: p_write.add(6).readU16(),
                auth_req: p_write.add(8).readU8()
            };
            
            const valueBytes = [];
            try {
                if (structFields.len > 0) {
                    const buffer = p_write.add(9).readByteArray(structFields.len);
                    valueBytes.push(...Array.from(new Uint8Array(buffer)));
                }
            } catch (e) {
                console.warn("Failed to read data:", e.message);
            }
            
            console.log(`
[GATTC Write Operation]
Connection ID: 0x${conn_id.toString(16)} (${conn_id})
Type: ${GATT_WRITE_TYPE[type] || "UNKNOWN"} (${type})
Data Structure:
{
    Connection ID: 0x${structFields.conn_id.toString(16)},
    Handle: 0x${structFields.handle.toString(16)},
    Offset: 0x${structFields.offset.toString(16)},
    Length: ${structFields.len},
    Auth Request: 0x${structFields.auth_req.toString(16)},
    Data: [${valueBytes.map(b => b.toString(16).padStart(2, '0')).join(' ')}]
}`);
        },
        onLeave: function(retval) {
            console.log(`[GATTC Write Operation] Return Value: 0x${retval.toInt32().toString(16)}`);
        }
    });
})();

// 3. GATT Notification Handling Hook
(function hookGattNotification() {
    const OFFSET = 0x3925FC;
    const targetAddr = baseAddr.add(OFFSET);
    Interceptor.attach(targetAddr, {
        onEnter: function(args) {
            Java.use("java.lang.Thread").sleep(EnterDelay);
            this.tcb = new NativePointer(args[0]);
            this.cid = args[1] & 0xFFFF;
            this.op_code = args[2] & 0xFF;
            this.len = args[3] & 0xFFFF;
            this.p_data = new NativePointer(args[4]);
        },
        onLeave: function(retval) {
            console.log('\n[Bluetooth GATT Notification]');
            console.log(`Channel ID: 0x${this.cid.toString(16)}, Op Code: 0x${this.op_code.toString(16)}, Total Length: ${this.len} bytes`);
            if (!this.p_data || this.p_data.isNull()) {
                console.warn("Data pointer is invalid");
                return;
            }
            try {
                let p = this.p_data;
                let remaining = this.len;
                const values = [];
                while (remaining > 0) {
                    if (remaining < 2) break;
                    const handle = Memory.readU16(p);
                    p = p.add(2);
                    remaining -= 2;
                    
                    let value_len = 0;
                    if (this.op_code === GATT_HANDLE_MULTI_VALUE_NOTIF) {
                        if (remaining < 2) break;
                        value_len = Memory.readU16(p);
                        p = p.add(2);
                        remaining -= 2;
                    } else {
                        value_len = this.len - 2;
                    }
                    
                    if (value_len > remaining || value_len > 0xFFFF) break;
                    const data = Memory.readByteArray(p, value_len);
                    values.push({ handle, length: value_len, data });
                    p = p.add(value_len);
                    remaining -= value_len;
                    if (this.op_code !== GATT_HANDLE_MULTI_VALUE_NOTIF) break;
                }
                
                values.forEach((value, index) => {
                    console.log(`Data Block #${index + 1}`);
                    console.log(`Handle: 0x${value.handle.toString(16).padStart(4, '0')}`);
                    console.log(`Data Length: ${value.length} bytes`);
                    console.log(`Data Content: ${Array.from(new Uint8Array(value.data))
                        .map(b => b.toString(16).padStart(2, '0'))
                        .join(' ').toUpperCase()}`);
                });
            } catch (e) {
                console.error(`Parse failed: ${e.message}`);
            }
        }
    });
})();

// 4. GATTC Read Operation Hook
(function hookGattRead() {
    const OFFSET = 0x38A8D8;
    const funcAddr = baseAddr.add(OFFSET);
    Interceptor.attach(funcAddr, {
        parameters: ['uint16', 'uint8', 'pointer'],
        onEnter: function(args) {
            const conn_id = args[0].toInt32();
            const type = args[1].toInt32();
            const p_read = args[2];
            console.log(`[GATTC Read Operation] Connection ID: ${conn_id}, Type: ${type}, Address: ${p_read}`);
            try {
                const auth_req = Memory.readU8(p_read);
                const s_handle = Memory.readU16(p_read.add(2));
                const e_handle = Memory.readU16(p_read.add(4));
                const arrayBuffer = Memory.readByteArray(p_read.add(6), 16);
                const uuidBytes = Array.from(new Uint8Array(arrayBuffer));
                const uuidStr = bytesToUuid(uuidBytes);
                console.log(`Struct Content:
  Auth Request: ${auth_req}
  Start Handle: 0x${s_handle.toString(16)}
  End Handle: 0x${e_handle.toString(16)}
  UUID:     ${uuidStr}`);
            } catch (e) {
                console.log("Failed to read struct:", e.message);
            }
        },
        onLeave: function(retval) {
            console.log(`[GATTC Read Operation] Return Value: ${retval.toInt32()}`);
        }
    });
})();

// 5. MTU Config Hook
(function hookMtuConfig() {
    const OFFSET = 0x2C0078;
    const funcAddr = baseAddr.add(OFFSET);
    Interceptor.attach(funcAddr, {
        onEnter: function(args) {
            this.conn_id = args[0];
            this.mtu = args[1];
            console.log(`[MTU Config] Connection ID: ${this.conn_id}, Current MTU: ${this.mtu.toInt32()}`);
        },
        onLeave: function(retval) {
            console.log(`[MTU Config] Return Value: ${retval.toInt32()}`);
        }
    });
})();

// 6. Security Check Hook
(function hookSecurityCheck() {
    const OFFSET = 0x38FD5C;
    const targetAddr = baseAddr.add(OFFSET);
    Interceptor.attach(targetAddr, {
        onEnter: function(args) {
            this.p_clcb = args[0];
            console.log(`[Security Check] Parameter Address: ${this.p_clcb}`);
        },
        onLeave: function(retval) {
            const boolValue = retval.toUInt32() !== 0;
            console.log(`[Security Check] Return Value: ${boolValue ? "Pass" : "Deny"}`);
        }
    });
})();

// 7. GATT Service Discovery
(function hookSecurityCheck() {
    const OFFSET = 0x3910A8;
    const targetAddr = baseAddr.add(OFFSET);
    Interceptor.attach(targetAddr, {
        onEnter: function(args) {
            console.log(`service discovery: ${args[0]}`);
        },
        onLeave: function(retval) {
        
        }
    });
})();

// 8. GATT ReadCallback
(function hookReadChCB() {
    const OFFSET = 0x2C1FFC;
    const targetAddr = baseAddr.add(OFFSET);
    Interceptor.attach(targetAddr, {
        onEnter: function(args) {
            console.log(`read ch callback: ${args[0]}`);
        },
        onLeave: function(retval) {
        
        }
    });
})();

// 9. GATT ReadCallback
(function hookReadDescCB() {
    const OFFSET = 0x2C2728;
    const targetAddr = baseAddr.add(OFFSET);
    Interceptor.attach(targetAddr, {
        onEnter: function(args) {
            console.log(`read des callback: ${args[0]}`);
        },
        onLeave: function(retval) {
        
        }
    });
})();

// 9. GATT Security Cls
(function hookReadDescCB() {
    const OFFSET = 0x390494;
    const targetAddr = baseAddr.add(OFFSET);
    Interceptor.attach(targetAddr, {
        onEnter: function(args) {
            console.log(`GATT Security Cls: ${args[0]}`);
        },
        onLeave: function(retval) {
            console.log(`[Security Check] Return Value: ${retval.toInt32()}`);
        }
    });
})();


console.log("[*] All hooks loaded successfully!");