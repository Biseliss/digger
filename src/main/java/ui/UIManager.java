package ui;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry owned by a Screen: id -> UIObject and tag -> set of UIObject.
 * Objects register on attach (added to a tree rooted at a Screen) and
 * unregister on detach (removed), so lookups never return stale objects.
 */
public class UIManager {
    private final Map<Integer, UIObject> byId = new HashMap<>();
    private final Map<String, Set<UIObject>> byTag = new HashMap<>();

    void register(UIObject o) {
        byId.put(o.getId(), o);
    }

    void unregister(UIObject o) {
        byId.remove(o.getId());
    }

    void registerTag(String tag, UIObject o) {
        byTag.computeIfAbsent(tag, k -> new HashSet<>()).add(o);
    }

    void unregisterTag(String tag, UIObject o) {
        Set<UIObject> set = byTag.get(tag);
        if (set == null) return;
        set.remove(o);
        if (set.isEmpty()) byTag.remove(tag);
    }

    public UIObject findById(int id) {
        return byId.get(id);
    }

    public Set<UIObject> findByTag(String tag) {
        return Collections.unmodifiableSet(byTag.getOrDefault(tag, Collections.emptySet()));
    }
}
