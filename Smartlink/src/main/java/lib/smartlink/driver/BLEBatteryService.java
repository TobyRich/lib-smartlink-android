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

import lib.smartlink.BLEService;

import java.lang.ref.WeakReference;

/**
 * Driver class for battery interaction
 * @author Prashant Vaibhav
 * @date 10/04/2014.
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
