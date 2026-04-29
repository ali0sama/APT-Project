package database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import crdt.block.Block;
import crdt.block.BlockCRDT;
import crdt.block.BlockID;
import crdt.character.CharId;
import crdt.character.CRDTChar;

import java.util.ArrayList;
import java.util.List;

public class DocumentSerializer {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    // ---- JSON DTO inner classes ----

    private static class CharDTO {
        int counter;
        int userID;
        String value;
        Integer parentCounter;
        Integer parentUserID;
        boolean deleted;
        boolean bold;
        boolean italic;
    }

    private static class BlockIdDTO {
        int counter;
        int userID;
    }

    private static class BlockDTO {
        BlockIdDTO blockId;
        BlockIdDTO parentBlockId; // always null in current flat-list impl
        boolean deleted;
        List<CharDTO> characters;
    }

    private static class DocumentDTO {
        List<BlockDTO> blocks;
    }

    // ---- Serialization ----

    public String serialize(BlockCRDT blockCRDT) {
        DocumentDTO doc = new DocumentDTO();
        doc.blocks = new ArrayList<>();

        for (Block block : blockCRDT.getAllBlocks()) {
            BlockDTO blockDTO = new BlockDTO();

            BlockIdDTO bid = new BlockIdDTO();
            bid.counter = block.getBlockId().getCounter();
            bid.userID  = block.getBlockId().getUserId();
            blockDTO.blockId      = bid;
            blockDTO.parentBlockId = null;
            blockDTO.deleted       = !block.isVisible();
            blockDTO.characters    = new ArrayList<>();

            for (CRDTChar c : block.getContent().getAllChars()) {
                CharDTO cdto = new CharDTO();
                cdto.counter     = c.id.counter;
                cdto.userID      = c.id.userID;
                cdto.value       = String.valueOf(c.value);
                cdto.parentCounter = (c.parentID != null) ? c.parentID.counter : null;
                cdto.parentUserID  = (c.parentID != null) ? c.parentID.userID  : null;
                cdto.deleted = c.isDeleted();
                cdto.bold    = c.isBold();
                cdto.italic  = c.isItalic();
                blockDTO.characters.add(cdto);
            }

            doc.blocks.add(blockDTO);
        }

        return GSON.toJson(doc);
    }

    // ---- Deserialization ----

    public BlockCRDT deserialize(String json) {
        DocumentDTO doc = GSON.fromJson(json, DocumentDTO.class);
        BlockCRDT blockCRDT = new BlockCRDT();

        for (BlockDTO blockDTO : doc.blocks) {
            BlockID blockId = new BlockID(blockDTO.blockId.counter, blockDTO.blockId.userID);
            Block block = new Block(blockId);

            if (blockDTO.deleted) {
                block.delete();
            }

            if (blockDTO.characters != null) {
                List<CRDTChar> chars = new ArrayList<>();
                for (CharDTO cdto : blockDTO.characters) {
                    CharId id = new CharId(cdto.counter, cdto.userID);
                    CharId parentId = (cdto.parentCounter != null && cdto.parentUserID != null)
                            ? new CharId(cdto.parentCounter, cdto.parentUserID) : null;

                    CRDTChar c = new CRDTChar(id, cdto.value.charAt(0), parentId);
                    if (cdto.deleted) c.markDeleted();
                    c.setBold(cdto.bold);
                    c.setItalic(cdto.italic);
                    chars.add(c);
                }
                block.getContent().bulkLoad(chars);
            }

            blockCRDT.insertBlock(block);
        }

        return blockCRDT;
    }
}
