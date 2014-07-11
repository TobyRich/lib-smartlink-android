/*

Copyright (c) 2014, TobyRich GmbH
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package lib.smartlink;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * @author pvaibhav
 * @date 13 Feb 2014
 *
 * @edit Radu Hambasan
 * @date 11 Jul 2014
 */
public abstract class BLEService {

    private static final String TAG = "lib-smartlink-BLEService";
    protected WeakReference<BluetoothDevice> mParent;
    protected HashMap<String, BluetoothGattCharacteristic> mFields;
    protected BluetoothGatt mGatt;

    public DataPool mEngineDP;
    public DataPool mRudderDp;

    public void attach(BluetoothGatt gatt, HashMap<String, BluetoothGattCharacteristic> fields, BluetoothDevice bluetoothDevice) {
        this.mParent = new WeakReference<BluetoothDevice>(bluetoothDevice);
        this.mGatt = gatt;
        this.mFields = fields;
        mEngineDP = new DataPool(1);
        mRudderDp = new DataPool(1);
        attached();
        Log.d(TAG, "Initialized service driver");
    }

    protected abstract void attached();

    protected abstract void didUpdateValueForCharacteristic(String c);

    protected void updateField(String name) {
        try {
            mParent.get().enqueueOperation(BluetoothDevice.BleCommand.READ, mFields.get(name));
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
    }

    protected void setNotification(String name, boolean enable) {
        try {
            if (enable) {
                mParent.get().enqueueOperation(BluetoothDevice.BleCommand.ENABLE_NOTIFICATION,
                        mFields.get(name));
            } else {
                mParent.get().enqueueOperation(BluetoothDevice.BleCommand.DISABLE_NOTIFICATION,
                        mFields.get(name));
            }
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
    }

    protected String getStringValueForCharacteristic(String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        return c.getStringValue(0);
    }

    protected Integer getUint8ValueForCharacteristic(String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        return c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
    }

    protected Integer getUin16ValueForCharacteristic(String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        return c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
    }

    protected byte[] getBytesForCharacteristic(String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        return c.getValue();
    }

    protected Integer getInt8ValueForCharacteristic(String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        return c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
    }

    protected void setWriteNeedsResponse(boolean responsNeeded, String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        c.setWriteType(responsNeeded ? BluetoothGattCharacteristic.WRITE_TYPE_SIGNED : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
    }

    @SuppressWarnings("StringEquality")
    protected void writeUint8Value(short value, String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        BluetoothDevice bDevice = mParent.get();
        if (bDevice == null) {
            Log.w(TAG, "No delegate set.");
            return;
        }
        int extraOpt = BluetoothDevice.BleCommand.NO_EXTRA;
        // intentional literal comparison with ==, for efficiency
        if (characteristic == "engine") {
            mEngineDP.postData(value);
            extraOpt = BluetoothDevice.BleCommand.EXTRA_MOTOR;
        } else if (characteristic == "rudder") {
            mRudderDp.postData(value);
            extraOpt = BluetoothDevice.BleCommand.EXTRA_RUDDER;
        } else {
            c.setValue(value, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        mParent.get().enqueueOperation(BluetoothDevice.BleCommand.WRITE, c, extraOpt);
    }

    protected void writeInt8Value(byte value, String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        BluetoothDevice bDevice = mParent.get();
        if (bDevice == null) {
            Log.w(TAG, "No delegate set.");
            return;
        }
        int extraOpt = BluetoothDevice.BleCommand.NO_EXTRA;
        // intentional literal comparison with ==, for efficiency
        if (characteristic == "engine") {
            mEngineDP.postData(value);
            extraOpt = BluetoothDevice.BleCommand.EXTRA_MOTOR;
        } else if (characteristic == "rudder") {
            mRudderDp.postData(value);
            extraOpt = BluetoothDevice.BleCommand.EXTRA_RUDDER;
        } else {
            c.setValue(value, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
        }

        mParent.get().enqueueOperation(BluetoothDevice.BleCommand.WRITE, c, extraOpt);
    }

    protected void writeBytes(byte[] value, String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        c.setValue(value);
        try {
            mParent.get().enqueueOperation(BluetoothDevice.BleCommand.WRITE, c);
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
    }
}
