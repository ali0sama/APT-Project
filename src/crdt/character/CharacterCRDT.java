package crdt.character;

import java.util.*;

public class CharacterCRDT {

    private final Map<CharId, CRDTChar> charMap = new HashMap<>();
    private final Map<CharId, List<CRDTChar>> children = new HashMap<>();

    public CharacterCRDT() {
        children.put(null, new ArrayList<>());
    }

    public void insert(CharId id, char value, CharId parentID) {
        if (charMap.containsKey(id))
            return;

        CRDTChar newChar = new CRDTChar(id, value, parentID);
        charMap.put(id, newChar);

        children.computeIfAbsent(parentID, k -> new ArrayList<>()).add(newChar);
        children.get(parentID).sort(Comparator.comparing(c -> c.id));
    }

    public void delete(CharId targetID) {
        CRDTChar target = charMap.get(targetID);
        if (target == null)
            throw new NoSuchElementException("Character not found: " + targetID);
        target.markDeleted();
    }

    public String getDocument() {
        StringBuilder sb = new StringBuilder();
        collectText(null, sb);
        return sb.toString();
    }

    private void collectText(CharId parentID, StringBuilder sb) {
        List<CRDTChar> kids = children.get(parentID);
        if (kids == null)
            return;
        for (CRDTChar child : kids) {
            if (!child.isDeleted())
                sb.append(child.value);
            collectText(child.id, sb);
        }
    }

    public List<CRDTChar> getVisibleChars() {
        List<CRDTChar> result = new ArrayList<>();
        collectVisible(null, result);
        return result;
    }

    private void collectVisible(CharId parentID, List<CRDTChar> result) {
        List<CRDTChar> kids = children.get(parentID);
        if (kids == null)
            return;
        for (CRDTChar child : kids) {
            if (!child.isDeleted())
                result.add(child);
            collectVisible(child.id, result);
        }
    }

    public List<CRDTChar> getAllChars() {
        List<CRDTChar> result = new ArrayList<>();
        collectAll(null, result);
        return result;
    }

    private void collectAll(CharId parentID, List<CRDTChar> result) {
        List<CRDTChar> kids = children.get(parentID);
        if (kids == null)
            return;
        for (CRDTChar child : kids) {
            result.add(child);
            collectAll(child.id, result);
        }
    }

    public CRDTChar getChar(CharId id) {
        return charMap.get(id);
    }

    public boolean contains(CharId id) {
        return charMap.containsKey(id);
    }

    public int visibleSize() {
        return getVisibleChars().size();
    }

    public int totalSize() {
        return charMap.size();
    }

    public void setBold(CharId id, boolean bold) {
        CRDTChar c = charMap.get(id);
        if (c != null && !c.isDeleted())
            c.setBold(bold);
    }

    public void setItalic(CharId id, boolean italic) {
        CRDTChar c = charMap.get(id);
        if (c != null && !c.isDeleted())
            c.setItalic(italic);
    }

    public void merge(CharacterCRDT remote) {
        for (CRDTChar rc : remote.getAllChars()) {
            if (!charMap.containsKey(rc.id))
                insert(rc.id, rc.value, rc.parentID);
            if (rc.isDeleted())
                charMap.get(rc.id).markDeleted();
        }
    }
}