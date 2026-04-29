package filemanagement;

import database.FileRepository;
import crdt.block.BlockCRDT;
import java.sql.SQLException;
import java.util.UUID;
import java.util.List;

public class FileManager {
    private final FileRepository fileRepository;

    public FileManager(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public String createNewFile(String name) throws SQLException {
        // Task 1: Generate unique document ID using UUID [cite: 76, 81]
        String docId = UUID.randomUUID().toString();
        
        // Task 1: Generate two access codes [cite: 76]
        String editorCode = ShareCodeManager.generateEditorCode();
        String viewerCode = ShareCodeManager.generateViewerCode();
        
        // Task 1: Save to database via FileRepository [cite: 76]
        fileRepository.saveFile(docId, name, editorCode, viewerCode, new BlockCRDT());
        
        return docId;
    }

    public BlockCRDT openFile(String docId) throws SQLException {
        // Task 1: Load the BlockCRDT from FileRepository [cite: 77]
        return fileRepository.loadFile(docId);
    }

    public void renameFile(String docId, String newName) throws SQLException {
        // Task 1: Update the name field (Reuse save logic with existing codes) [cite: 78]
        // Note: You might need to fetch the codes first if your repository doesn't have a rename-only method
        List<String[]> records = fileRepository.getAllFileRecords();
        for (String[] rec : records) {
            if (rec[0].equals(docId)) {
                fileRepository.saveFile(docId, newName, rec[2], rec[3], fileRepository.loadFile(docId));
                break;
            }
        }
    }

    public void deleteFile(String docId) throws SQLException {
        // Task 1: Remove from the database [cite: 79]
        fileRepository.deleteFile(docId);
    }
}