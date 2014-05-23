package lib.smartlink.driver;

import android.util.Log;

import java.lang.ref.WeakReference;

import lib.smartlink.BLEService;

/**
 * Created by pvaibhav on 13/02/2014.
 */
public class BLESmartplaneService
        extends BLEService {

    public interface Delegate {
        void didStartChargingBattery();

        void didStopChargingBattery();
    }

    public WeakReference<Delegate> delegate;

    private short lastEngine = 0;
    private short lastRudder = 0;

    public void setMotor(short value) {
        if (value == lastEngine)
            return;
        if (value > 254)
            value = 254;
        if (value < 0)
            value = 0;
        writeUint8Value(value, "engine");
        lastEngine = value;
    }

    public void setRudder(short value) {
        if (value == lastRudder)
            return;
        if (value > 126)
            value = 126;
        if (value < -126)
            value = -126;
        writeInt8Value((byte)value, "rudder");
        lastRudder = value;
    }

    public void updateChargingStatus() {
        updateField("chargestatus");
    }

    @Override
    protected void attached() {
        // Reset to zero
        setWriteNeedsResponse(false, "engine");
        setWriteNeedsResponse(false, "rudder");
        writeUint8Value((short) 0, "engine");
        writeInt8Value((byte) 0, "rudder");
    }

    @Override
    protected void didUpdateValueForCharacteristic(String c) {
        if (delegate.get() == null)
            return;

        if (c.equalsIgnoreCase("chargestatus")) {
            int status = getUint8ValueForCharacteristic("chargestatus");
            try {
                if (status == 0)
                    delegate.get().didStopChargingBattery();
                else
                    delegate.get().didStartChargingBattery();
            } catch (NullPointerException ex) {
                Log.w(this.getClass().getName(), "No delegate set");
            }
        }
    }
}
