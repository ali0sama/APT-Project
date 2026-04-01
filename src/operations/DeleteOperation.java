package operations;

import crdt.character.CharId;
import crdt.character.CharacterCRDT;

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

    // ✅ ADD THIS
    @Override
    public void apply(CharacterCRDT crdt) {
        crdt.delete(targetID);
    }
}