// hook_mtu.js
Java.perform(function () {
    // Get the base address of libbluetooth.so
    var baseAddr = Module.findBaseAddress("libbluetooth.so");
    if (baseAddr === null) {
        console.log("[-] Failed to find base address of libbluetooth.so!");
        return;
    }
    console.log("[+] libbluetooth.so base address: " + baseAddr);

    // Calculate the actual function address
    var funcOffset = 0x2C0078;
    var funcAddr = baseAddr.add(funcOffset);
    console.log("[+] Function address: " + funcAddr);

    // Hook the function
    Interceptor.attach(funcAddr, {
        onEnter: function (args) {
            // Log original arguments
            this.conn_id = args[0]; // conn_id
            this.mtu = args[1];     // mtu
            console.log("[+] Hooked btif_gattc_configure_mtu");
            console.log("    conn_id: " + this.conn_id);
            console.log("    Original MTU: " + this.mtu.toInt32() + " [" + this.mtu + "]");

            // Modify MTU value (e.g., force it to 512)
            // this.mtu = 512;
            // args[1] = ptr(this.mtu);
            // console.log("    Modified MTU: " + this.mtu);
        },
        onLeave: function (retval) {
            console.log("[+] Return value: " + retval);
        }
    });
});