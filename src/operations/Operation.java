package operations;

public abstract class Operation {

    public final int userID;
    public final int clock;

    public enum Type {
        INSERT, DELETE
    }

    protected Operation(int userID, int clock) {
        this.userID = userID;
        this.clock = clock;
    }

    public abstract Type getType();
}