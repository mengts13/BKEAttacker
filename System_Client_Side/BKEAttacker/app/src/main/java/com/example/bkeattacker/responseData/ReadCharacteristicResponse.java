package com.example.bkeattacker.responseData;

import android.bluetooth.BluetoothDevice;

public class ReadCharacteristicResponse {
    public BluetoothDevice device = null;
    public int requestId =0 ;
    public int offset =0;

    public void reset(){
        device= null;
        requestId=0;
        offset = 0;
    }

    public void setValue(BluetoothDevice device, int requestId, int offset){
        this.device = device;
        this.requestId=requestId;
        this.offset = offset;
    }

    public boolean IsSet(){
        return device!=null;
    }

}
