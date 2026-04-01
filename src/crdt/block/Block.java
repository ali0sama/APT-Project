package crdt.block;

import crdt.character.CharacterCRDT;

public class Block {
    private final BlockID blockId;
    private final CharacterCRDT content; 
    private boolean isDeleted; 

    public Block(BlockID blockId) {
        this.blockId = blockId;
        this.content = new CharacterCRDT();
        this.isDeleted = false;
    }

    
    public boolean isValidSize() {
        String text = content.getDocument();
        if (text.isEmpty()) return false;
        String[] lines = text.split("\r\n|\r|\n");
        return lines.length >= 2 && lines.length <= 10;
    }

    public void delete() {
        this.isDeleted = true;  
    }

    public boolean isVisible() {
        return !isDeleted;
    }

    public BlockID getBlockId() {
        return blockId;
    }

    public CharacterCRDT getContent() {
        return content;
    }
}
