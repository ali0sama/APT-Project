package crdt.block;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BlockCRDT {
    private final List<Block> blocks;

    public BlockCRDT() {
        this.blocks = new ArrayList<>();
    }

    public void insertBlock(BlockID id, int index) {
        Block newBlock = new Block(id);
        if (index >= 0 && index <= blocks.size()) {
            blocks.add(index, newBlock);
        }
    }

    public void deleteBlock(BlockID id) {
        for (Block b : blocks) {
            if (b.getBlockId().equals(id)) {
                b.delete();
                break;
            }
        }
    }

    public void moveBlock(BlockID id, int newIndex) {
        Block target = null;
        for (Block b : blocks) {
            if (b.getBlockId().equals(id)) {
                target = b;
                break;
            }
        }
        if (target != null) {
            blocks.remove(target);
            int safeIndex = Math.min(newIndex, blocks.size());
            blocks.add(safeIndex, target);
        }
    }

public List<Block> getVisibleBlocks() {
    return blocks.stream()
            .filter(Block::isVisible)
            .sorted((b1, b2) -> b1.getBlockId().compareTo(b2.getBlockId())) 
            .collect(Collectors.toList());
}


    public void insertBlock(Block b) {
    this.blocks.add(b);
}

public void splitBlock(BlockID targetId, crdt.character.CharId splitPoint, BlockID newBlockId) {
   
    for (int i = 0; i < blocks.size(); i++) {
        if (blocks.get(i).getBlockId().equals(targetId)) {
            blocks.add(i + 1, new Block(newBlockId));
            return;
        }
    }
}

public String getDocumentText() {
    StringBuilder sb = new StringBuilder();
    for (Block b : getVisibleBlocks()) {
        sb.append(b.getContent().getDocument());
    }
    return sb.toString();
}

}

