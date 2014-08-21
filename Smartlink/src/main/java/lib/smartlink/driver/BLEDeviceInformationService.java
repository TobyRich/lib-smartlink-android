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
import lib.smartlink.Util;

/**
 * Created by pvaibhav on 17/02/2014.
 */
public class BLEDeviceInformationService
        extends BLEService {
    public interface Delegate {
        void didUpdateSerialNumber(BLEDeviceInformationService device, String serialNumber);

        void didUpdateSystemID(BLEDeviceInformationService device, String systemID);
    }

    private String mSerialNumber;
    private String mSystemID;
    public WeakReference<Delegate> delegate;

    public String getSerialNumber() {
        return mSerialNumber;
    }

    public String getSystemID() {
        return mSystemID;
    }

    @Override
    public void attached() {
        updateField("serialnumber");
        updateField("systemid");
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
        } else if (c.equalsIgnoreCase("systemid")) {
            byte[] sysIDBytes = getBytesForCharacteristic("systemid");

            byte[] newSysIDBytes = new byte[6];
            System.arraycopy(sysIDBytes, 0, newSysIDBytes, 0, 3);
            System.arraycopy(sysIDBytes, 5, newSysIDBytes, 3, 3);

            Util.reverse(newSysIDBytes);

            mSystemID = Util.bytesToHex(newSysIDBytes).toLowerCase();

            if (mSystemID != null)
                Log.i("lib-smartlink-devinfo", "System ID updated: " + mSystemID);

            if (delegate != null) {
                try {
                    delegate.get().didUpdateSystemID(this, mSystemID);
                } catch (Exception ex) {
                    Log.i("lib-smartlin-devinfo", "Error in delegate.");
                }
            }
        }
    }
}
