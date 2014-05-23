package lib.smartlink.driver;

import android.util.Log;

import lib.smartlink.BLEService;

import java.lang.ref.WeakReference;

/**
 * Created by pvaibhav on 10/04/2014.
 */
public class BLEBatteryService extends BLEService {
    public interface Delegate {
        void didUpdateBatteryLevel(float percent);
    }

    public WeakReference<Delegate> delegate;

    protected void attached() {
        updateField("level"); // initially
        setNotification("level", true);
    }

    @Override
    protected void didUpdateValueForCharacteristic(String c) {
        if (c.equalsIgnoreCase("level")) {
            int level = getUint8ValueForCharacteristic("level");
            try {
                delegate.get().didUpdateBatteryLevel(level);
            } catch (NullPointerException ex) {
                Log.w(this.getClass().getName(), "No delegate set");
            }
        }
    }
}
