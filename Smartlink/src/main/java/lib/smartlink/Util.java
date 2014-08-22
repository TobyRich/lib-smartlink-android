package lib.smartlink;

/**
 * @author Radu Hambasan
 * @date 11 Jul 2014
 */

/* TODO: more safety checks for the methods below */
public class Util {
    /**
     * Produces a new byte array containing the elements between the start and end indices.
     * <p/>
     * The start index is inclusive, the end index exclusive. Null array input produces null output.
     *
     * @param array the array
     * @param startIndexInclusive the starting index.
     * @param endIndexExclusive elements up to endIndex-1 are present in the returned subarray.
     * @return a new array containing the elements between the start and end indices.
     */
    public static byte[] subarray(byte[] array, int startIndexInclusive, int endIndexExclusive) {
        if ((array == null) || (endIndexExclusive < startIndexInclusive)) {
            return null;
        }
        int length = endIndexExclusive - startIndexInclusive;
        byte[] _subarray =  new byte[length];
        System.arraycopy(array, startIndexInclusive, _subarray, 0, length);

        return _subarray;
    }

    /**
     * Reverses the order of the given array.
     * <p/>
     * This method does nothing for a null input array.
     * @param array the array to reverse, may be null
     */
    public static void reverse(byte[] array) {
        if (array == null) {
            return;
        }
        int leftPtr = 0;
        int rightPtr = array.length - 1;

        while (leftPtr < rightPtr) {
            byte temp = array[leftPtr];
            array[leftPtr] = array[rightPtr];
            array[rightPtr] = temp;

            ++leftPtr;
            --rightPtr;
        }
    }

    /**
     * Converts a byte array to a hex string
     * @param bytes bytes for string
     * @return hex string
     */
    public static String bytesToHex(byte[] bytes) {
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
