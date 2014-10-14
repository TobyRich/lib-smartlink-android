package lib.smartlink;

/**
 * Class in charge of maintaining the average of the most recently used values
 * @author Radu Hambasan
 * @date 08 Jul, 2014
 */
public class DataPool {
    private static final int DEFAULT_BUFFER_SIZE = 20;

    private final int bufferSize;
    private int[] buffer;
    private int sum = 0;
    private int currElement = -1;

    /**
     * Create a DataPool of {@value lib.smartlink.DataPool#DEFAULT_BUFFER_SIZE}
     */
    public DataPool() {
        bufferSize = DEFAULT_BUFFER_SIZE;
        buffer = new int[bufferSize];
    }

    /**
     * Create a DataPool of <code>size</code> elements
     * @param size the size of the DataPool
     */
    public DataPool(int size) {
        bufferSize = size;
        buffer = new int[bufferSize];
    }

    /**
     * Add <code>data</code> to the pool
     * @param data the payload
     */
    public void postData(int data) {
        ++currElement;
        currElement %= bufferSize;

        sum = sum - buffer[currElement] + data;
        buffer[currElement] = data;
    }

    /**
     *  Retrieve the average
     *  @return the average
     */
    public int fetchData() {
        return sum / bufferSize;
    }

    /**
     * Reset the DataPool
     */
    public void clear() {
        buffer = new int[bufferSize];
        sum = 0;
        currElement = -1;
    }

    /**
     * Set all elements to <code>val</code>
     * @param val the future value of each element in the DataPool
     */
    public void flood(int val) {
        sum = bufferSize * val;
        for (int i = 0; i < bufferSize; i++) {
            buffer[i] = val;
        }
    }

}
