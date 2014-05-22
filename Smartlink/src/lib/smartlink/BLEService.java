package lib.smartlink;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Created by pvaibhav on 13/02/2014.
 */
public abstract class BLEService {

    private static final String TAG = "lib-smartlink-BLEService";
    protected WeakReference<BluetoothDevice> mParent;
    protected HashMap<String, BluetoothGattCharacteristic> mFields;
    protected BluetoothGatt mGatt;

    public void attach(BluetoothGatt gatt, HashMap<String, BluetoothGattCharacteristic> fields, BluetoothDevice bluetoothDevice) {
        this.mParent = new WeakReference<BluetoothDevice>(bluetoothDevice);
        this.mGatt = gatt;
        this.mFields = fields;
        attached();
        Log.d(TAG, "Initialized service driver");
    }

    protected abstract void attached();

    protected abstract void didUpdateValueForCharacteristic(String c);

    protected void updateField(String name) {
        try {
            mParent.get().enqueOperation(BluetoothDevice.BleCommand.READ, mFields.get(name));
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
    }

    protected void setNotification(String name, boolean enable) {
        try {
            mParent.get().enqueOperation(BluetoothDevice.BleCommand.ENABLE_NOTIFICATION, mFields.get(name));
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

    protected void writeUint8Value(short value, String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        c.setValue(value, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        try {
            mParent.get().enqueOperation(BluetoothDevice.BleCommand.WRITE, c);
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
    }

    protected void writeInt8Value(byte value, String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        c.setValue(value, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
        try {
            mParent.get().enqueOperation(BluetoothDevice.BleCommand.WRITE, c);
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
    }

    protected void writeBytes(byte[] value, String characteristic) {
        BluetoothGattCharacteristic c = mFields.get(characteristic);
        c.setValue(value);
        try {
            mParent.get().enqueOperation(BluetoothDevice.BleCommand.WRITE, c);
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
    }
}
