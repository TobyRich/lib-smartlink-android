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
}
