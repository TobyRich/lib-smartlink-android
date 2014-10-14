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
import lib.smartlink.Util;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;
/**
 * Driver class for firmware upload
 * @author Prashant Vaibhav
 * @date 10/04/2014.
 */
public class BLEFirmwareUploadService extends BLEService {
    private static final String TAG = "Firmware";
    // Image Identification size
    private static int OAD_IMG_ID_SIZE = 4;
    // Image header size (version + length + image id size)
    private static int OAD_IMG_HDR_SIZE = (2 + 2 + OAD_IMG_ID_SIZE);
    private static final int HAL_FLASH_WORD_SIZE = 4;
    // The Image is transported in 16-byte blocks in order to avoid using blob operations.
    private static int OAD_BLOCK_SIZE = 16;

    public interface Delegate {
        void didGetFirmwareRejected(BLEFirmwareUploadService driver, String fwName);
        void didUploadFirmwareUpto(BLEFirmwareUploadService driver, float percent);
        void didFinishUploadingFirmware(BLEFirmwareUploadService driver);
        void didReceiveFirmwareVersion(BLEFirmwareUploadService driver, String fwVersion);
        boolean shouldStartUploadingFirmware(BLEFirmwareUploadService driver, String fwName);
    }

    private String fwVersionOnDevice;
    private boolean _canceled;
    private Timer _programmingTimer;
    private int _imgVersion;
    private byte[] _imageData;
    private int _nBlocks;
    private int _iBlocks;
    private int _iBytes;

    public WeakReference<Delegate> delegate;

    private static class ImageHeader {
        public int versionCode;
        public int versionNumber;
        public String imageID;
        public int length;
        public boolean isImgA;

        public String toString() {
            return imageID + "-" + versionNumber + "-" + (isImgA ? "A" : "B");
        }

        public byte[] toByteArray() {
            byte[] out = new byte[12];
            ByteBuffer b = ByteBuffer.wrap(out);
            b.putShort((short) versionCode);
            b.putShort((short) length);
            b.put(imageID.getBytes());
            return out;
        }

        public ImageHeader(byte[] hdr) {
            ByteBuffer in = ByteBuffer.wrap(hdr);
            in.order(ByteOrder.LITTLE_ENDIAN);
            versionCode = in.getShort() & 0xffff;
            length = in.getShort() & 0xffff;
            versionNumber = versionCode >> 1;
            isImgA = (versionCode & 0x1) == 0;
            byte[] id = new byte[4];
            in.get(id, 0, 4);
            imageID = new String(id);
        }

        public ImageHeader() {
            versionCode = 0;
            versionNumber = versionCode >> 1;
            isImgA = (versionCode & 0x1) == 0;
            length = 0;
            imageID = "----";
        }
    }

    private void initialize() {
        _canceled = false;

        // The below code is going to be very confusing for someone who doesn't know Java or
        // programming in general. :-|
        final byte[] zero = new byte[1];
        zero[0] = 0;
        final byte[] one = new byte[1];
        one[0] = 1;

        Log.d(TAG, "imgidentify notif enabled");
        setNotification("imgidentify", true);
        setNotification("blockrequest", true);
        writeBytes(zero, "imgidentify"); // write dummy data to get fw version
        Log.d(TAG, "imgidentify version-getter A written");
        Timer _imgDetectTimer = new Timer();
        _imgDetectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // If we have reached here, it means
                writeBytes(one, "imgidentify");
                Log.d(TAG, "imgidentify version-getter B written");
            }
        }, 1500); // msec
        _imgVersion = 0xffff;
        Log.i(TAG, "initialized");
    }

    public static String getFwVersionFromFile(InputStream f) {
        try {
            /* header format is:
                    uint16  crc0; --- ommitted in RX hdr
                    uint16  crc1  --- ommitted in RX hdr
                    uint16  version;
                    uint16  image_length;
                    uint8   imgID[4];
                    uint8   reserved[4];
            */
            byte[] header = new byte[16];
            f.read(header, 0, 16);
            // Skip first 4 bytes (CRC)
            ImageHeader hdr = new ImageHeader(Util.subarray(header, 4, 17)); // end index exclusive
            return hdr.toString();
        } catch (FileNotFoundException e) {
            return "Unknown";
        } catch (IOException e) {
            return "Unknown";
        }
    }

    private boolean validateImage(InputStream in) {
        // read the entire firmware image into memory
        _imageData = new byte[126976]; // always 128kB. XXX: make it better. XXXXX: really?
        try {
            Log.i(TAG, "Read " + in.read(_imageData) + " from fw file");
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }

        if (isCorrectImage()) {
            uploadImage();
        } else {
            Log.e(TAG, "Wrong fw image type " + _imgVersion);
            try {
                delegate.get().didGetFirmwareRejected(this, "fw");
            } catch (NullPointerException ex) {
                Log.e(TAG, "Delegate not set");
            }
        }
        return false;
    }

    private boolean isCorrectImage() {
        // Check if given image is correct, i.e. of a different "A/B" type than the one on device
        ImageHeader hdr = new ImageHeader(Util.subarray(_imageData, 4, 17));
        return (hdr.versionCode & 0x1) != (_imgVersion & 0x1);
    }

    private void uploadImage() {
        _canceled = false;

        byte[] requestData = new byte[12];
        ByteBuffer b = ByteBuffer.wrap(requestData).order(ByteOrder.LITTLE_ENDIAN);

        ImageHeader imgHeader = new ImageHeader(Util.subarray(_imageData, 4, 17));
        b.putShort((short) (imgHeader.versionCode & 0xffff));
        b.putShort((short) (imgHeader.length & 0xffff));
        b.put(imgHeader.imageID.getBytes());
        b.putShort((short) 12); // not sure why these are being added but it's in the original
        b.putShort((short) 15);

        writeBytes(requestData, "imgidentify");

        _nBlocks = imgHeader.length / ((OAD_BLOCK_SIZE / HAL_FLASH_WORD_SIZE));
        _iBlocks = 0;
        _iBytes = 0;

        try {
            if (delegate.get().shouldStartUploadingFirmware(this, "fw") == false)
                return;
            else {
                _programmingTimer = new Timer();
                _programmingTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        BLEFirmwareUploadService.this.programmingTimerTick();
                    }
                }, 1500); // ms
            }
        } catch (NullPointerException e) {
            Log.w(TAG, "No delegate set");
        }
    }

    private void writeNextBlock() {
        // Prepare block
        byte[] requestData = new byte[2 + OAD_BLOCK_SIZE];
        ByteBuffer b = ByteBuffer.wrap(requestData).order(ByteOrder.LITTLE_ENDIAN);

        b.putShort((short) (_iBlocks & 0xffff));
        b.put(Util.subarray(_imageData, _iBytes, _iBytes + OAD_BLOCK_SIZE));
        writeBytes(requestData, "blockrequest");

        _iBlocks++;
        _iBytes += OAD_BLOCK_SIZE;
    }

    private void programmingTimerTick() {
        if (_canceled) {
            _canceled = false;
            return;
        }

        writeNextBlock();

        if (_iBlocks == _nBlocks) {
            try {
                delegate.get().didFinishUploadingFirmware(this);
            } catch (NullPointerException e) {
                Log.w(TAG, "No delegate set");
            }
            return;
        } else {
//            _programmingTimer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    BLEFirmwareUploadService.this.programmingTimerTick();
//                }
//            }, 20 /* ms */);
        }

        final float progress = (float) _iBlocks / (float) _nBlocks;
        try {
            if (_iBlocks % 20 == 0)
            delegate.get().didUploadFirmwareUpto(this, progress * 100);
        } catch (NullPointerException e) {
            Log.w(TAG, "No delegate set");
        }
    }


    public String getFwVersionOnDevice() {
        return fwVersionOnDevice;
    }

    public void uploadFirmware(InputStream fwImage) {
        validateImage(fwImage);
    }

    public void cancelUpload() {
        _canceled = true;
    }

    @Override
    protected void attached() {
        initialize();
    }

    @Override
    protected void didUpdateValueForCharacteristic(String c) {
        Log.d(TAG, "received " + c);
        if (c.equalsIgnoreCase("imgidentify")) {
            if (_imgVersion == 0xffff) {
                _imgVersion = getUin16ValueForCharacteristic("imgidentify");
                Log.i(TAG, "self.imgVersion: " + _imgVersion);
                ImageHeader hdr = new ImageHeader(getBytesForCharacteristic("imgidentify"));
                fwVersionOnDevice = hdr.toString();
                Log.i(TAG, "Current fw on device: " + fwVersionOnDevice);
                try {
                    delegate.get().didReceiveFirmwareVersion(this, fwVersionOnDevice);
                } catch (NullPointerException ex) {
                    Log.w(TAG, "Delegate not set");
                }
            }
        } else if (c.equalsIgnoreCase("blockrequest")) {
            //Log.i(TAG, "Block request received");
            if (!_canceled)
                programmingTimerTick();
        }
    }


}
