package Server;

import Interface.SubjectRI;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SubjectRegistry {
    private static final Map<String, SubjectRI> subjects = new ConcurrentHashMap<>();

    public static void register(String username, SubjectRI subject) {
        subjects.put(username, subject);
    }

    public static void unregister(String username) {
        subjects.remove(username);
    }

    public static SubjectRI get(String username) {
        return subjects.get(username);
    }

    public static Collection<SubjectRI> getAll() {
        return subjects.values();
    }
}