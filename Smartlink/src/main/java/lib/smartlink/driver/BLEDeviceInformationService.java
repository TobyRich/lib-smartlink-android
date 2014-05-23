package lib.smartlink.driver;

import android.util.Log;

import lib.smartlink.BLEService;

import java.lang.ref.WeakReference;

/**
 * Created by pvaibhav on 17/02/2014.
 */
public class BLEDeviceInformationService
        extends BLEService {
    public interface Delegate {
        void didUpdateSerialNumber(BLEDeviceInformationService device, String serialNumber);
    }

    private String mSerialNumber;
    public WeakReference<Delegate> delegate;

    public String getSerialNumber() {
        return mSerialNumber;
    }

    @Override
    public void attached() {
        updateField("serialnumber");
    }

    @Override
    protected void didUpdateValueForCharacteristic(String c) {
        if (c.equalsIgnoreCase("serialnumber")) {
            mSerialNumber = getStringValueForCharacteristic("serialnumber").trim();
            Log.i("lib-smartlink-devinfo", "Serial number updated: " + mSerialNumber + " (len=" + mSerialNumber.length() + ")");
            try {
                delegate.get().didUpdateSerialNumber(this, mSerialNumber);
            } catch (NullPointerException ex) {
                Log.w("lib-smartlink-devinfo", "No delegate set");
            }
        }
    }
}
