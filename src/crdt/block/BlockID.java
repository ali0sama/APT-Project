
package crdt.block;

import java.util.Objects;

public class BlockID implements Comparable<BlockID> {
    private final int counter;
    private final int userId;

    public BlockID(int counter, int userId) {
        this.counter = counter;
        this.userId = userId;
    }

    public int getCounter() { return counter; }
    public int getUserId() { return userId; }

    @Override
    public int compareTo(BlockID other) {
        if (this.counter != other.counter) {
            return Integer.compare(this.counter, other.counter);
        }
        return Integer.compare(this.userId, other.userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockID blockId = (BlockID) o;
        return counter == blockId.counter && userId == blockId.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(counter, userId);
    }
}