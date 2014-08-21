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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.ParserConfigurationException;

/*
 * @author pvaibhav
 * @date 13 Feb 20114
 *
 * @edit Radu Hambasan
 * @date 11 Jul 2014
 */
@SuppressWarnings({"ConstantConditions", "AccessStaticViaInstance"})
// because we are already checking for null pointers for delegate
public class BluetoothDevice extends BluetoothGattCallback implements BluetoothAdapter.LeScanCallback {
    private static final int MAX_QUEUE_SIZE = 20;

    public interface Delegate {
        public void didStartService(BluetoothDevice device, String serviceName, BLEService service);

        public void didUpdateSignalStrength(BluetoothDevice device, float signalStrength);

        public void didStartScanning(BluetoothDevice device);

        public void didStartConnectingTo(BluetoothDevice device, float signalStrength);

        public void didDisconnect(BluetoothDevice device);
    }

    private static final String TAG = "lib-smartlink-BluetoothDevice";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ADV_128BIT_UUID_ALL = 0x06;
    private static final int ADV_128BIT_UUID_MORE = 0x07;

    public WeakReference<Delegate> delegate;
    public boolean automaticallyReconnect = false;
    private int rssiHigh = -25;
    private int rssiLow = -96;

    private final Activity mOwner;
    private android.bluetooth.BluetoothDevice mDevice;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private UUID[] mPrimaryServices;
    private final Semaphore mSemaphore = new Semaphore(1); // single threaded access

    boolean allowRudder = true;
    boolean allowMotor = true;

    protected class BleCommand implements Comparable<BleCommand>, Runnable {
        public static final int ENABLE_NOTIFICATION = 0;
        public static final int DISABLE_NOTIFICATION = 1;
        public static final int WRITE = 2;
        public static final int READ = 3;
        public static final int DISCONNECT = 4;
        public static final int UPDATE_RSSI = 5;
        public static final int DISCOVER_SERVICES = 6;
        public static final int SCAN = 7;
        public static final int CONNECT = 8;

        public static final int NO_EXTRA = 0;
        public static final int EXTRA_RUDDER = 1;
        public static final int EXTRA_MOTOR = 2;

        private final int operationType;
        private final int extraOpt;

        private final BluetoothGattCharacteristic field;

        public BleCommand(int operationType, BluetoothGattCharacteristic field) {
            this.operationType = operationType;
            this.field = field;
            this.extraOpt = NO_EXTRA;
        }

        public BleCommand(int operationType, BluetoothGattCharacteristic field, int extraOpt) {
            this.operationType = operationType;
            this.field = field;
            this.extraOpt = extraOpt;
        }

        @Override
        public boolean equals(Object another) {
            if (this == another)
                return true;
            if (!(another instanceof BleCommand))
                return false;
            BleCommand that = (BleCommand) another;
            return this.field == that.field && this.operationType == that.operationType;
        }

        @Override
        public String toString() {
            String[] optype = {"NOTIF_ENABLE", "NOTIF_DISABLE", "WRITE", "READ", "DISCONNECT", "UPDATE_RSSI", "DISCOVER_SERVICES", "SCAN"};
            String fieldname = (this.field == null) ? "--" : uuidToName.get(uuidHarmonize(this.field.getUuid().toString()));
            return "{" + optype[this.operationType] + ": " + fieldname + "}";
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public int compareTo(BleCommand that) {
            if (this.field == that.field && this.operationType == that.operationType)
                return 0;
            if (this.operationType < that.operationType)
                return -1;
            else
                return 1;
        }

        @Override
        public void run() {
            try {
                if (mBluetoothGatt == null) // got disconnected
                    return;
                // Acquire permission first
                mSemaphore.acquire();
                BluetoothGattCharacteristic c = this.field;
                switch (this.operationType) {
                    case READ:
                        mBluetoothGatt.readCharacteristic(c);
                        break;
                    case WRITE:
                        switch (extraOpt) {
                            case NO_EXTRA:
                                mBluetoothGatt.writeCharacteristic(c);
                                break;
                            case EXTRA_MOTOR:
                                BLEService smServM = charToDriver.get(c);
                                int valM = smServM.mEngineDP.fetchData();
                                c.setValue(valM, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                                mBluetoothGatt.writeCharacteristic(c);
                                allowMotor = true;
                                break;
                            case EXTRA_RUDDER:
                                BLEService smServR = charToDriver.get(c);
                                int valR = smServR.mRudderDp.fetchData();
                                c.setValue(valR, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                                mBluetoothGatt.writeCharacteristic(c);
                                allowRudder = true;
                                try {
                                    Thread.sleep(85);
                                } catch (InterruptedException ex) {
                                    Log.wtf("Sleeping: ", "Interrupt occurred");
                                }
                                break;
                            default:
                                Log.wtf(TAG, "Unexpected EXTRA option");
                                break;
                        }
                        break;
                    case ENABLE_NOTIFICATION:
                        writeNotificationDescriptor(c, true);
                        break;
                    case DISABLE_NOTIFICATION:
                        writeNotificationDescriptor(c, false);
                        break;
                    case DISCONNECT:
                        if (mBluetoothGatt != null) {
                            mBluetoothGatt.disconnect();
                        }
                        break;
                    case UPDATE_RSSI:
                        if (mBluetoothGatt != null) {
                            mBluetoothGatt.readRemoteRssi();
                        }
                        break;
                    case DISCOVER_SERVICES:
                        mBluetoothGatt.discoverServices();
                        break;
                    case SCAN:
                        startScanning();
                        break;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "op queue interrupted while performing " + this);
            } catch (NullPointerException e) {
                //Log.e(TAG, "Tried to perform operation " + this + "on non-existent something");
            }
        }
    }

    // For serializing access to Bluetooth stack
    // Not using Executors.newSingleThreadExecutor because we want to use a priority queue
    private ThreadPoolExecutor mCommandQueue;

    private HashMap<String, String> uuidToName;
    private HashMap<String, String> mServiceNameToDriverClass;
    private final HashMap<BluetoothGattCharacteristic, BLEService> charToDriver = new HashMap<BluetoothGattCharacteristic, BLEService>();

    private static String uuidHarmonize(String old) {
        // takes a 16 or 128 bit uuid string (with dashes) and harmonizes it into a 128 bit uuid
        String uuid = old.toUpperCase();
        if (uuid.length() == 4) {
            // 16 bit UUID. Convert to 128 bit using Bluetooth base UUID.
            uuid = "0000" + uuid + "-0000-1000-8000-00805F9B34FB";
        }
        return uuid;
    }

    public BluetoothDevice(InputStream plistFile, Activity owner) {
        mOwner = owner;
        mCommandQueue = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>());
        NSDictionary mPlist;

        try {
            mPlist = (NSDictionary) PropertyListParser.parse(plistFile);
        } catch (PropertyListFormatException e) {
            throw new IllegalArgumentException("invalid plistFile");
        } catch (ParserConfigurationException e) {
            throw new IllegalArgumentException("invalid plistFile");
        } catch (ParseException e) {
            throw new IllegalArgumentException("invalid plistFile");
        } catch (SAXException e) {
            throw new IllegalArgumentException("invalid plistFile");
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid plistFile");
        }
        // Collect basic settings
        rssiHigh = ((NSNumber) mPlist.objectForKey("rssi high")).intValue();
        rssiLow = ((NSNumber) mPlist.objectForKey("rssi low")).intValue();


        // Build a list of all primary services to scan for
        NSDictionary services = (NSDictionary) mPlist.objectForKey("Services");
        List<UUID> uuidList = new ArrayList<UUID>();

        for (String serviceName : services.allKeys()) { // enumerate over all keys

            NSDictionary service = (NSDictionary) services.objectForKey(serviceName);

            NSNumber isPrimary = ((NSNumber) service.objectForKey("Primary"));
            if (isPrimary == null)
                continue;

            if (isPrimary.boolValue()) {
                UUID uuid = UUID.fromString(service.objectForKey("UUID").toString());
                uuidList.add(uuid);
                Log.d(TAG, "Includes primary '" + serviceName + "' service: " + uuid);
            }
        }

        // Pre-convert to an array because it will be used quite often (on every scan start)
        mPrimaryServices = new UUID[uuidList.size()]; // allocate array with just enough elements
        uuidList.toArray(mPrimaryServices);

        // Now build our HashMap of uuid -> name.
        uuidToName = new HashMap<String, String>();
        // And also one to store which driver handles a particular service
        mServiceNameToDriverClass = new HashMap<String, String>();

        for (String serviceName : services.allKeys()) {

            // Get service information dictionary
            NSDictionary service = (NSDictionary) services.objectForKey(serviceName);

            // Add service name, its uuid and driver class
            String uuid = uuidHarmonize(service.objectForKey("UUID").toString());
            uuidToName.put(uuid, serviceName);
            mServiceNameToDriverClass.put(serviceName, service.objectForKey("DriverClass").toString());

            Log.i(TAG, "|--" + serviceName + " : " + uuid);

            // Now iterate over its characteristics
            NSDictionary fields = (NSDictionary) service.objectForKey("Fields");
            for (String charName : fields.allKeys()) {
                String charUuid = uuidHarmonize(fields.objectForKey(charName).toString());
                uuidToName.put(charUuid, serviceName + "/" + charName);
                Log.i(TAG, "|     " + charName + " : " + charUuid);
            }

        }
    }

    public void connect() throws BluetoothDisabledException {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) mOwner.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            startScanning();
        } else {
            throw new BluetoothDisabledException("Bluetooth disabled.");
        }
    }

    public void disconnect() {
        enqueueOperation(BleCommand.DISCONNECT);
    }

    public void updateSignalStrength() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.readRemoteRssi();
        }
    }

    private void initializeBluetooth() {
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) mOwner.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mOwner.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            startScanning();
        }
    }

    private void startScanning() {
        mBluetoothAdapter.stopLeScan(this); // in case scan was already running
        mBluetoothAdapter.startLeScan(this);
        try {
            delegate.get().didStartScanning(this);
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
    }

    private static boolean uuidEqualToByteArray(UUID uuid, byte[] b) {
        // http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation
        final String s = uuid.toString().replace("-", "");
        final String given = Util.bytesToHex(b);

        return given.equalsIgnoreCase(s);
    }

    private boolean includesPrimaryService(byte[] scanRecord) {
        if (scanRecord.length < 3)
            return false; // cuz we need at least 3 bytes: len, type, data

        int offset = 0;
        do {
            final int len = scanRecord[offset++];
            final int type = scanRecord[offset++];
            if (type == ADV_128BIT_UUID_ALL || type == ADV_128BIT_UUID_MORE) {
                byte[] uuidBytes = Util.subarray(scanRecord, offset, offset + len - 1);
                Util.reverse(uuidBytes);

                for (UUID primary : mPrimaryServices) {
                    if (uuidEqualToByteArray(primary, uuidBytes))
                        return true;
                }
            } else {
                offset += len - 1;
            }
        } while (offset < scanRecord.length - 1); // len-1 cuz each time we read at least 2 bytes
        return false;
    }

    @Override
    public void onLeScan(android.bluetooth.BluetoothDevice d, int rssi, byte[] scanRecord) {
        // When scan results are received
        mDevice = d;
        if (mDevice.getName() == null) // some sort of error happened
            return;

        // Touch-n-Go
        if (!(rssiLow <= rssi && rssi <= rssiHigh)) {
            // rssi outside acceptable range, ignore this result.
            Log.w(TAG, "Rssi " + rssi + " outside range [" + rssiLow + ", " + rssiHigh + "]");
            return;
        }

        Log.i(TAG, mDevice.getName() + " found");
        // We are hardcoding the device name for now, because filtering scan results on 128 bit UUID
        // for some reason, on SOME devices we get an IndexOutOfBoundsException
        // when using includesPrimaryServices() length:62, index: -67
        if (mDevice.getName().equalsIgnoreCase("TailorToys PowerUp") ||
                mDevice.getName().equalsIgnoreCase("TobyRich SmartPlane")) {
            Log.i(TAG, "Trying to connect to " + mDevice.getName());
            Delegate delegRef = delegate.get();
            if (delegRef != null) {
                /* XXX: should be d, not this */
                delegRef.didStartConnectingTo(this, rssi);
            } else {
                Log.i(TAG, "Delegate was not set");
            }
            // Connection is done on the command queue. Since this needs extra data (mDevice, mOwner etc.),
            // we'll create a special-case subclass of BleCommand and override its run method.
            // Using the command queue is necessary to work around Samsung bug. (check T4 on Phabricator).
            mCommandQueue.execute(new BleCommand(BleCommand.CONNECT, null) {
                @Override
                public void run() {
                    try {
                        Log.i(TAG, "Attempting to acquire BLE lock and start connecting (" + mCommandQueue.getQueue().size() + " ops pending)");
                        mSemaphore.acquire();
                        mDevice.connectGatt(mOwner.getApplicationContext(), false, BluetoothDevice.this);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Thread interrupted while connecting");
                    }
                }
            });
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.d(TAG, "Connection state changed to " + newState + " (status: " + status + ")");
        switch (newState) {
            case BluetoothProfile.STATE_CONNECTED:
                Log.i(TAG, "Connected to device");
                mBluetoothAdapter.stopLeScan(this);
                mBluetoothGatt = gatt;
                mSemaphore.release(); // because connection is also a queued operation
                enqueueOperation(BleCommand.DISCOVER_SERVICES);
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                charToDriver.clear();
                mCommandQueue.shutdownNow();
                // Make a fresh command queue
                mCommandQueue = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>());
                mSemaphore.release();
                mBluetoothGatt.close();
                mBluetoothGatt.close();
                mBluetoothGatt = null;

                if (delegate.get() != null) {
                    delegate.get().didDisconnect(this);
                }
                if (automaticallyReconnect)
                    startScanning();
                break;
            default:
                break;
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.i(TAG, "Services discovered on device:");
        List<BluetoothGattService> gattServiceList = mBluetoothGatt.getServices();

        charToDriver.clear(); // start afresh

        for (BluetoothGattService s : gattServiceList) {
            // Find service name corresponding to this service's UUID, as per plist file
            String sName = uuidToName.get(uuidHarmonize(s.getUuid().toString()));

            // If it was not found (in plist), just continue to next
            if (sName == null)
                continue;

            BLEService driver;
            String driverClassName = this.getClass().getName().substring(0, this.getClass().getName().lastIndexOf(".")) + ".driver." + mServiceNameToDriverClass.get(sName);
            try {
                // Try to instantiate an instance of the driver's class (as specified in the plist)
                Log.i(TAG, "Initializing driver " + driverClassName);
                driver = (BLEService) Class.forName(driverClassName).newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
                continue; // to next service
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                continue;
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "|---" + sName + " ?? " + driverClassName + " ?? not found");
                continue;
            }

            // ----- Now that we created the driver, process this service's fields.
            Log.d(TAG, "|---" + sName + " :: " + mServiceNameToDriverClass.get(sName));

            // Create a hashmap to store the mapping of field names to chars.
            // This will be sent to the driver so it can output data to chars directly.
            HashMap<String, BluetoothGattCharacteristic> listOfFields = new HashMap<String, BluetoothGattCharacteristic>();

            // Iterate over each char, and if it's in the plist, add it to our global
            // and driver-specific lists.
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                // Find the name for this char based on its uuid, as specified in the plist
                String cName = uuidToName.get(uuidHarmonize(c.getUuid().toString()));
                if (cName == null) {
                    // this field was not in plist so skip it
                    continue;
                }
                Log.d(TAG, "|       " + cName);

                // Remove the service name before sending to the driver, as they only get
                // the field names
                String cNameWithoutSname = cName.substring(cName.indexOf("/") + 1);
                listOfFields.put(cNameWithoutSname, c);

                // Also store the mapping of char to its designated driver
                charToDriver.put(c, driver);
            }

            // Attach this information to the driver instance, and notify the app
            driver.attach(mBluetoothGatt, listOfFields, this);
            try {
                delegate.get().didStartService(this, sName, driver);
            } catch (NullPointerException ex) {
                Log.w(TAG, "No delegate set");
            }
        }

        // now perform all queued up operations
        mSemaphore.release();
    }

    protected void enqueueOperation(int operation, BluetoothGattCharacteristic c) {
        // Android ignores requests if any previous requests are pending. So we must serialize
        // all read requests using a FIFO or priority queue.
        final BleCommand op = new BleCommand(operation, c);
        final int size = mCommandQueue.getQueue().size();
        if (size >= 20 && size % 20 == 0)
            Log.w(TAG, "op queue too large: " + mCommandQueue.getQueue().size());
        // Sometimes for some reason, too many items get queued
//        if (mCommandQueue.getQueue().size() > 20) {
//            Log.w(TAG, "op queue nuked!!");
//            mCommandQueue.getQueue().clear(); // nuke the queue
//        }
        mCommandQueue.execute(op); // actually queues the op, executes when BLE stack is free
    }

    protected void enqueueOperation(int operation) {
        // For operations that don't need a characteristic
        enqueueOperation(operation, null);
    }

    protected void enqueueOperation(int operation, BluetoothGattCharacteristic c, int extra) {
        // This means that other write commands are being enqued,
        // so they will set the desired value
        final int qSize = mCommandQueue.getQueue().size();
        if (qSize > MAX_QUEUE_SIZE) {
            Log.e(TAG, "The queue size was too large. Had to nuke it, captain.");
            mCommandQueue.getQueue().clear();
            return;
        }
        if (extra == BleCommand.EXTRA_RUDDER) {
            if (!allowRudder) return;
            else allowRudder = false;
        } else if (extra == BleCommand.EXTRA_MOTOR) {
            if (!allowMotor) return;
            else allowMotor = false;
        }
        final BleCommand op = new BleCommand(operation, c, extra);

        mCommandQueue.execute(op); // actually queues the op, executes when BLE stack is free
    }

    private void writeNotificationDescriptor(BluetoothGattCharacteristic c, boolean enable) {
        // ALL HAIL GOOGLE
        final UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        mBluetoothGatt.setCharacteristicNotification(c, enable);
        BluetoothGattDescriptor descriptor = c.getDescriptor(CCC);
        descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00, 0x00});
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        String charName = uuidToName.get(uuidHarmonize(characteristic.getUuid().toString()));

        // Find which driver handles it and send it a message
        BLEService driver = charToDriver.get(characteristic);
        //Log.i(TAG, "Received: " + charName + " -> " + driver.toString().substring(driver.toString().lastIndexOf(".")));
        if (driver != null)
            driver.didUpdateValueForCharacteristic(charName.substring(charName.indexOf("/") + 1));
        mSemaphore.release();
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        // Happens on notification.
        onCharacteristicRead(gatt, characteristic, 0);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic, int status) {
        mSemaphore.release();
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                  int status) {
        mSemaphore.release();
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        try {
            delegate.get().didUpdateSignalStrength(this, rssi);
        } catch (NullPointerException ex) {
            Log.w(TAG, "No delegate set");
        }
        mSemaphore.release();
    }
}
