package lib.smartlink;

/*
 * @author Radu Hambasan
 * @date 08 Jul 2014
 */
public class DataPool {
    private static final int DEFAULT_BUFFER_SIZE = 1;

    private final int bufferSize;
    private int[] buffer;
    private int sum = 0;
    private int currElement = -1;

    public DataPool() {
        bufferSize = DEFAULT_BUFFER_SIZE;
        buffer = new int[bufferSize];
    }

    public DataPool(int size) {
        bufferSize = size;
        buffer = new int[bufferSize];
    }

    public void postData(int data) {
        ++currElement;
        currElement %= bufferSize;

        sum = sum - buffer[currElement] + data;
        buffer[currElement] = data;
    }

    public int fetchData() {
        return sum / bufferSize;
    }

    public void clear() {
        buffer = new int[bufferSize];
        sum = 0;
        currElement = -1;
    }

    public void flood(int val) {
        sum = bufferSize * val;
        for (int i = 0; i < bufferSize; i++) {
            buffer[i] = val;
        }
    }

}
