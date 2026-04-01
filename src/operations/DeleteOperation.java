package operations;

import crdt.character.CharId;

public class DeleteOperation extends Operation {

    public final CharId targetID;

    public DeleteOperation(int userID, int clock, CharId targetID) {
        super(userID, clock);
        this.targetID = targetID;
    }

    @Override
    public Type getType() {
        return Type.DELETE;
    }

    @Override
    public String toString() {
        return "DeleteOperation{userID=" + userID + ", clock=" + clock + ", targetID=" + targetID + "}";
    }
}