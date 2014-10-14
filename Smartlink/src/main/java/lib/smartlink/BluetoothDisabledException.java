package lib.smartlink;

/**
 * Bluetooth must be enabled by user
 * @author Radu Hambasan
 * @date 21 Jul 2014
 */
public class BluetoothDisabledException extends Exception {
    public BluetoothDisabledException(String msg) {
        super(msg);
    }
}
