package crdt.block;

import java.util.ArrayList;
import java.util.List;

public class BlockCRDT {
    private final List<Block> blocks;

    private boolean hasBlock(BlockID id) {
        for (Block b : blocks) {
            if (b.getBlockId().equals(id)) {
                return true;
            }
        }
        return false;
    }

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
    List<Block> visible = new ArrayList<>();
    for (Block b : blocks) {
        if (b.isVisible()) {
            visible.add(b);
        }
    }
    return visible;
}

public List<Block> getAllBlocks() {
    return new ArrayList<>(blocks);
}


    public void insertBlock(Block b) {
    if (hasBlock(b.getBlockId())) {
        return;
    }

    int insertAt = 0;
    while (insertAt < blocks.size() && blocks.get(insertAt).getBlockId().compareTo(b.getBlockId()) <= 0) {
        insertAt++;
    }
    this.blocks.add(insertAt, b);
}

public void splitBlock(BlockID targetId, crdt.character.CharId splitPoint, BlockID newBlockId) {
    if (hasBlock(newBlockId)) {
        return;
    }

    for (int i = 0; i < blocks.size(); i++) {
        Block oldBlock = blocks.get(i);
        
        if (oldBlock.getBlockId().equals(targetId)) {
         
            List<crdt.character.CRDTChar> allChars = oldBlock.getContent().getAllChars();
            
            int splitIndex = -1;
            for (int j = 0; j < allChars.size(); j++) {
                if (allChars.get(j).id.equals(splitPoint)) {
                    splitIndex = j;
                    break;
                }
            }

            if (splitIndex != -1) {
               
                List<crdt.character.CRDTChar> stayChars = new ArrayList<>(allChars.subList(0, splitIndex + 1));
                List<crdt.character.CRDTChar> moveChars = new ArrayList<>(allChars.subList(splitIndex + 1, allChars.size()));

               
                Block newBlock = new Block(newBlockId);
                
             
                newBlock.getContent().bulkLoadLinear(moveChars);

            
                oldBlock.getContent().clear();
                oldBlock.getContent().bulkLoadLinear(stayChars);

                blocks.add(i + 1, newBlock);
            }
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

