import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class fileSystem extends userprocess {

    private final FsDirectory root = new FsDirectory("/");
    // Each client's current directory, keyed by its pid. A process that has
    // never called SetDirectory isn't in the map yet and starts at root.
    private final Map<Integer, FsDirectory> currentDirectories = new HashMap<>();

    /** Thrown by path processing and the actions; its message becomes the ERROR text. */
    static class FsException extends Exception {
        FsException(String message) { super(message); }
    }

    protected fileSystem(String name, int queueCapacity) {
        super(name, queueCapacity);
    }


    // Server loop: handle one request at a time, in the order they arrive
    @Override
    protected void main() {
        while (true) {
            message msg = getMessage(); // blocks until something arrives
            if (msg == null) break;     // interrupted
            try {
                handle(msg);
            } catch (FsException e) {
                send(msg.getSenderPid(), fsysMessagetype.ERROR, e.getMessage());
            }
            cooperate(); // give up the CPU here if our quantum is used up
        }
    }

    // Works out which action the message asks for and runs it
    private void handle(message msg) throws FsException {
        int pid = msg.getSenderPid();
        fsysMessagetype type;
        try {
            type = fsysMessagetype.fromWhat(msg.getWhat());
        } catch (IllegalArgumentException e) {
            throw new FsException("Unknown filesystem request " + msg.getWhat());
        }
        switch (type) {
            case SET_DIRECTORY -> setDirectory(pid, pathOf(msg));
            case ADD_DIRECTORY -> addDirectory(pid, pathOf(msg));
            case REMOVE_FILE -> removeFile(pid, pathOf(msg));
            case REMOVE_DIRECTORY -> removeDirectory(pid, pathOf(msg));
            case LIST -> list(pid, pathOf(msg));
            case READ_FILE -> readFile(pid, pathOf(msg));
            case ADD_FILE -> addFile(pid, pathOf(msg), dataOf(msg));
            case WRITE_FILE -> writeFile(pid, pathOf(msg), dataOf(msg));
            default -> throw new FsException("Unknown filesystem request " + type);
        }
    }

    // ---------------------------------------------------------------- actions

    // SetDirectory: the path must name an existing directory
    private void setDirectory(int pid, String path) throws FsException {
        FsNode node = resolve(pid, path);

        if (!(node instanceof FsDirectory dir)) throw new FsException("SetDirectory: " + path + ": Not a directory");
        currentDirectories.put(pid, dir);
    }

    // AddFile: creates a new file holding contents. The parent directory must
    // already exist and nothing may already have that name.
    private void addFile(int pid, String path, byte[] contents) throws FsException {

        // A trailing "/" means a directory, so it can't name a new file.
        if (path.endsWith("/")) throw new FsException("AddFile: " + path + ": a file name cannot end in /");
        Location loc = resolveParent(pid, path, "AddFile");

        if (loc.dir.contains(loc.name)) throw new FsException("AddFile: " + path + ": File exists");
        loc.dir.add(new FsFile(loc.name, contents));
    }

    // AddDirectory: creates a new, empty directory. Like mkdir without -p, parent must already exist
    private void addDirectory(int pid, String path) throws FsException {
        Location loc = resolveParent(pid, path, "AddDirectory");
        if (loc.dir.contains(loc.name)) throw new FsException("AddDirectory: " + path + ": File exists");

        loc.dir.add(new FsDirectory(loc.name));
    }

    // RemoveFile: deletes a file. Refuses directories, which have their own action.
    private void removeFile(int pid, String path) throws FsException {
        if (path.endsWith("/")) throw new FsException("RemoveFile: " + path + ": Not a file");

        Location loc = resolveParent(pid, path, "RemoveFile");
        FsNode node = loc.dir.get(loc.name);
        if (node == null) throw new FsException("RemoveFile: " + path + ": No such file or directory");

        if (!(node instanceof FsFile)) throw new FsException("RemoveFile: " + path + ": Is a directory");

        loc.dir.remove(loc.name);
    }

    // RemoveDirectory: deletes a directory, but only if it is empty, so files
    // can never be deleted by accident along with their directory.
    private void removeDirectory(int pid, String path) throws FsException {
        Location loc = resolveParent(pid, path, "RemoveDirectory");
        FsNode node = loc.dir.get(loc.name);
        if (node == null) throw new FsException("RemoveDirectory: " + path + ": No such file or directory");

        if (!(node instanceof FsDirectory dir)) throw new FsException("RemoveDirectory: " + path + ": Not a directory");

        if (!dir.isEmpty()) throw new FsException("RemoveDirectory: " + path + ": Directory not empty");

        loc.dir.remove(loc.name);
    }

    /** Sends one entry per item (or the file itself, like ls), then LIST_END. */
    private void list(int pid, String path) throws FsException {
        FsNode node = resolve(pid, path.isEmpty() ? "." : path);

        if (node instanceof FsDirectory dir) {
            for (FsNode entry : dir.list()) {
                send(pid, fsysMessagetype.LIST_ENTRY, entry.describe());
            }
        } else {
            send(pid, fsysMessagetype.LIST_ENTRY, node.describe());
        }
        send(pid, fsysMessagetype.LIST_END, null);
    }

    // ReadFile: replies FILE_CONTENTS with the file's bytes in data0.
    private void readFile(int pid, String path) throws FsException {
        FsNode node = resolve(pid, path);

        if (!(node instanceof FsFile file)) throw new FsException("ReadFile: " + path + ": Is a directory");
        send(pid, fsysMessagetype.FILE_CONTENTS, file.getContents());
    }

    // WriteFile: replaces an existing file's contents.
    private void writeFile(int pid, String path, byte[] contents) throws FsException {
        FsNode node = resolve(pid, path);

        if (!(node instanceof FsFile file)) throw new FsException("WriteFile: " + path + ": Is a directory");
        file.setContents(contents);
    }

    // -------------------------------------------------------- path processing

    /** The directory a parent path resolved to plus the final name in the path. */
    private record Location(FsDirectory dir, String name) {}

    /** A process's current directory; new processes start at root. */
    private FsDirectory currentDirectory(int pid) throws FsException {
        FsDirectory cwd = currentDirectories.computeIfAbsent(pid, p -> root);
        if (!isAttached(cwd)) {
            throw new FsException("Current directory has been removed; use an absolute path or SetDirectory");
        }
        return cwd;
    }

    /** True if walking up the parents reaches root (i.e. the directory has not been removed). */
    private boolean isAttached(FsNode node) {
        while (node != null && node != root) node = node.getParent();
        return node == root;
    }

    /** Follows the whole path and returns the node it names. Used when the thing the path names must already exist */
    private FsNode resolve(int pid, String path) throws FsException {
        if (path.isEmpty()) throw new FsException("Empty path");
        FsNode node = path.startsWith("/") ? root : currentDirectory(pid);
        StringBuilder walked = new StringBuilder(path.startsWith("/") ? "" : ".");


        // Take one name at a time
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            // Only a directory can have something inside it
            if (!(node instanceof FsDirectory dir)) throw new FsException(path + ": " + walked + " is not a directory");
            if (part.equals("..")) {
                node = (dir == root) ? root : dir.getParent();
            } else {
                FsNode child = dir.get(part);
                if (child == null) throw new FsException(path + ": No such file or directory");
                node = child;
            }
            walked.append('/').append(part);
        }
        // "file.txt/" or "file.txt/." names a directory in Unix, which a file is not
        if (node instanceof FsFile && (path.endsWith("/") || path.endsWith("/."))) {
            throw new FsException(path + ": Not a directory");
        }
        return node;
    }

    /** Splits a path into its parent directory (resolved) and final name, for create/remove. */
    private Location resolveParent(int pid, String path, String action) throws FsException {
        // Drop trailing slashes, so "docs/" is treated as "docs"
        String trimmed = path;
        while (trimmed.length() > 1 && trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isEmpty()) throw new FsException(action + ": Empty path");
        // Root has no parent, so it can't be created or removed
        if (trimmed.equals("/")) throw new FsException(action + ": cannot operate on the root directory");

        // Everything before the last "/" is the parent: no "/" at all means the
        // current directory, and a "/" only at the front means root
        int slash = trimmed.lastIndexOf('/');
        String parentPath = slash < 0 ? "." : (slash == 0 ? "/" : trimmed.substring(0, slash));
        String name = trimmed.substring(slash + 1);

        // "." and ".." always mean "here" and "parent", so they can't be used as
        // names; that also stops "RemoveDirectory .." from deleting the parent.
        if (name.equals(".") || name.equals("..")) {
            throw new FsException(action + ": " + path + ": invalid name '" + name + "'");
        }

        FsNode parent = resolve(pid, parentPath);
        if (!(parent instanceof FsDirectory dir)) throw new FsException(action + ": " + parentPath + ": Not a directory");
        return new Location(dir, name);
    }

    // ------------------------------------------------------------- messaging

    private static String pathOf(message msg) {
        return (msg.getData(0) instanceof String path) ? path : "";
    }

    private static byte[] dataOf(message msg) throws FsException {
        if (!(msg.getData(1) instanceof byte[] data)) {
            throw new FsException(fsysMessagetype.fromWhat(msg.getWhat()) + ": malformed path/data payload");
        }
        return data;
    }

    private void send(int pid, fsysMessagetype what, Object data) {
        sendMessage(new message(pid, what.toWhat(), data));
    }

    // ------------------------------------------------------------- tree nodes

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    abstract static class FsNode {
        private final String name;
        private FsDirectory parent;
        private final LocalDateTime created = LocalDateTime.now();
        private LocalDateTime modified = created;

        FsNode(String name) { this.name = name; }

        String getName() { return name; }
        FsDirectory getParent() { return parent; }

        void touch() { modified = LocalDateTime.now(); }

        String dates() {
            return "created " + created.format(DATE_FORMAT) + "  modified " + modified.format(DATE_FORMAT);
        }

        /** One line for a LIST entry: type, name, dates, size (if file). */
        abstract String describe();
    }

    static class FsDirectory extends FsNode {
        private final Map<String, FsNode> children = new TreeMap<>(); // sorted, like ls

        FsDirectory(String name) { super(name); }

        boolean contains(String name) { return children.containsKey(name); }
        FsNode get(String name) { return children.get(name); }
        boolean isEmpty() { return children.isEmpty(); }
        List<FsNode> list() { return new ArrayList<>(children.values()); }

        void add(FsNode node) {
            node.parent = this;
            children.put(node.getName(), node);
            touch();
        }

        // Detaching the node is what makes isAttached() false for a removed directory.
        void remove(String name) {
            FsNode node = children.remove(name);
            if (node != null) node.parent = null;
            touch();
        }

        @Override
        String describe() { return "DIR   " + getName() + "/  " + dates(); }
    }

    static class FsFile extends FsNode {
        private byte[] contents;

        FsFile(String name, byte[] contents) {
            super(name);
            this.contents = contents;
        }

        byte[] getContents() { return contents; }

        void setContents(byte[] contents) {
            this.contents = contents;
            touch();
        }

        /** Size in disk blocks, rounded up. */
        int sizeInBlocks() {
            return (contents.length + diskmessagetype.BLOCK_SIZE - 1) / diskmessagetype.BLOCK_SIZE;
        }

        @Override
        String describe() {
            return "FILE  " + getName() + "  " + sizeInBlocks() + " block(s), " + contents.length + " bytes  " + dates();
        }
    }
}
