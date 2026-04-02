package crdt.utils;

public class Clock {

    private int counter;

    public Clock() {
        this.counter = 0;
    }

    public int tick() {
        return ++counter;
    }

    public void update(int remoteCounter) {
        counter = Math.max(counter, remoteCounter) + 1;
    }

    public int get() {
        return counter;
    }

    @Override
    public String toString() {
        return "Clock{counter=" + counter + "}";
    }
}