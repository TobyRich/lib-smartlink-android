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
