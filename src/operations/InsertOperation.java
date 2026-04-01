package operations;

import crdt.character.CharId;
import crdt.character.CharacterCRDT;

public class InsertOperation extends Operation {

    public final CharId charID;
    public final char value;
    public final CharId parentID;
    public final boolean bold;
    public final boolean italic;

    public InsertOperation(int userID, int clock, char value, CharId parentID, boolean bold, boolean italic) {
        super(userID, clock);
        this.charID = new CharId(clock, userID);
        this.value = value;
        this.parentID = parentID;
        this.bold = bold;
        this.italic = italic;
    }

    public InsertOperation(int userID, int clock, char value, CharId parentID) {
        this(userID, clock, value, parentID, false, false);
    }

    @Override
    public Type getType() {
        return Type.INSERT;
    }

    // ✅ ADD THIS
    @Override
    public void apply(CharacterCRDT crdt) {
        crdt.insert(charID, value, parentID);

        if (bold) crdt.setBold(charID, true);
        if (italic) crdt.setItalic(charID, true);
    }
}