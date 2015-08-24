package vim;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.lang.ref.WeakReference;

public class List implements Iterable<Object> {
    public static class ListLockedException extends Exception {
        public ListLockedException(String message) {
            super(message);
        }
    }

    public class ListIterator implements Iterator<Object> {
        private List list;
        private int index;

        public ListIterator(List list) {
            this.list = list;
            index = 0;
        }

        public boolean hasNext() {
            int size = -1;
            try { size = list.size(); } catch (Exception e) {}
            if (index < size) {
                return true;
            }
            else {
                return false;
            }
        }

        public Object next() {
            Object o = null;
            try { o = list.get(index); } catch (Exception e) {}
            index++;
            return o;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    ////////////////////////////////////////////////////////////////////////////

    /**
     * This is used in finalize() to prevent race conditions that could
     * possibly occur when decrementing reference counts.
     *
     * Note, that I'm sure how the JVM GC works, and this could possibly not
     * even be an issue.
     */
    private static Object lock;
    private static java.util.Set<Long> garbageLists;
    private static java.util.Map<Long, WeakReference<vim.List>> activeLists;

    static {
        garbageLists = new HashSet<Long>();
        activeLists = new HashMap<Long, WeakReference<vim.List>>();
        lock = new Object();
    }

    /**
     * Decrements the Vim reference count of all Java List objects that have
     * been deleted by the GC.
     *
     * Mainly called by vim/Vim, but anyone can safely call this.
     *
     * Only call this from the main thread.
     */
    public static void purgeGarbage() {
        if (Thread.currentThread().getId() != Vim.getMainThreadId())
            return;

        synchronized (vim.List.lock) {
            for (Long pointer : garbageLists) {
                vim.List.decrementReferenceCount(pointer.longValue());
            }
            garbageLists.clear();
        }
    }

    /**
     * Purge this raw C pointer from garbageLists.
     *
     * Failure to do so can cause double free() in rare cases.
     */
    public static void purge(long pointer) {
        synchronized(vim.List.lock) {
            garbageLists.remove(pointer);
        }
    }

    private native static void incrementReferenceCount(long pointer);
    private native static void decrementReferenceCount(long pointer);

    private long vimListPointer;

    /**
     * Get or create a Java List object given a raw system C pointer.
     */
    public static vim.List getOrCreate(long pointer) {
        synchronized (vim.List.lock) {
            garbageLists.remove(pointer);

            WeakReference<vim.List> ref = activeLists.get(pointer);
            // hasn't been created yet
            if (ref == null) {
                vim.List list = new List(pointer);
                list.incrementReferenceCount(pointer);
                ref = new WeakReference<vim.List>(list);
                activeLists.put(pointer, ref);
                return list;
            }
            else {
                vim.List potential_list = ref.get();
                // freed by GC
                if (potential_list == null) {
                    vim.List list = new List(pointer);
                    list.incrementReferenceCount(pointer);
                    ref = new WeakReference<vim.List>(list);
                    activeLists.put(pointer, ref);
                    return list;
                }
                // already exists
                else {
                    return potential_list;
                }
            }
        }
    }
    private List(long pointer) {
        vimListPointer = pointer;
    }
    public long getPointer() {
        return vimListPointer;
    }

    /**
     * Supporting clone() would make reference counting on Vim side much more
     * complicated.
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Override
    protected void finalize() throws Throwable {
        synchronized (vim.List.lock) {
            activeLists.remove(vimListPointer);
            garbageLists.add(vimListPointer);
        }
    }
    public static void setVimGCRefOnAllLists(int copyID) {
        synchronized (vim.List.lock) {
            Collection<WeakReference<vim.List>> collection = activeLists.values();
            for (WeakReference<vim.List> ref : collection) {
                vim.List list = ref.get();
                if (list != null) {
                    setVimGCRef(list.getPointer(), copyID);
                }
            }
        }
    }
    public native static void setVimGCRef(long pointer, int copyID);

    /**
     * Returns the number of items in this list.
     *
     * Equivalent to len(l) in Vim.
     */
    public int size() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _size();
        } else {
            SizeOperation op = new SizeOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private int _size() {
        return vim.List._size(vimListPointer);
    }
    private native static int _size(long pointer);

    /**
     * Returns the element at the specified index of the list.
     */
    public Object get(int index) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _get(index);
        } else {
            GetOperation op = new GetOperation(this, index);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private Object _get(int index) {
        return vim.List._get(vimListPointer, index);
    }
    private native static Object _get(long pointer, int index);

    /**
     * Replace the element at 'index' in the list list with 'item'.
     */
    public void set(int index, Object item) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _set(index, item);
        } else {
            SetOperation op = new SetOperation(this, index, item);
            op.waitUntilDone();
        }
    }
    private void _set(int index, Object item) {
        vim.List._set(vimListPointer, index, item);
    }
    private native static void _set(long pointer, int index, Object item);

    /**
     * Removes the element at 'index' in the list.
     */
    public void remove(int index) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _remove(index);
        } else {
            RemoveOperation op = new RemoveOperation(this, index);
            op.waitUntilDone();
        }
    }
    private void _remove(int index) {
        vim.List._remove(vimListPointer, index);
    }
    private native static void _remove(long pointer, int index);

    /**
     * Add 'item' to the end of the list.
     */
    public void add(Object item) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _add(item);
        } else {
            AddOperation op = new AddOperation(this, item);
            op.waitUntilDone();
        }
    }
    private void _add(Object item) {
        vim.List._add(vimListPointer, item);
    }
    private native static void _add(long pointer, Object item);

    /**
     * Inserts 'item' at the specified position in the list.
     */
    public void insert(Object item, int position) throws Exception{
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _insert(item, position);
        } else {
            InsertOperation op = new InsertOperation(this, item, position);
            op.waitUntilDone();
        }
    }
    private void _insert(Object item, int position) {
        vim.List._insert(vimListPointer, item, position);
    }
    private native static void _insert(long pointer, Object item, int position);

    /**
     * Provides an iterator over this collection and implements Iterable.
     */
    public Iterator<Object> iterator() {
        return new ListIterator(this);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Internal operations for this class
    ////////////////////////////////////////////////////////////////////////////

    private static class SizeOperation extends Vim.Operation {
        List list;
        int ret;
        public SizeOperation(List list) {
            super();
            this.list = list;
        }
        @Override
        public void Do() throws Exception {
            ret = list.size();
        }
    }

    private static class GetOperation extends Vim.Operation {
        List list;
        int index;
        Object ret;
        public GetOperation(List list, int index) {
            super();
            this.list = list;
            this.index = index;
        }
        @Override
        public void Do() throws Exception {
            ret = list.get(index);
        }
    }

    private static class SetOperation extends Vim.Operation {
        List list;
        int index;
        Object item;
        public SetOperation(List list, int index, Object item) {
            super();
            this.list = list;
            this.index = index;
            this.item = item;
        }
        @Override
        public void Do() throws Exception {
           list.set(index, item);
        }
    }

    private static class RemoveOperation extends Vim.Operation {
        List list;
        int index;
        public RemoveOperation(List list, int index) {
            super();
            this.list = list;
            this.index = index;
        }
        @Override
        public void Do() throws Exception {
           list.remove(index);
        }
    }

    private static class AddOperation extends Vim.Operation {
        List list;
        Object item;
        public AddOperation(List list, Object item) {
            super();
            this.list = list;
            this.item = item;
        }
        @Override
        public void Do() throws Exception {
           list.add(item);
        }
    }

    private static class InsertOperation extends Vim.Operation {
        List list;
        Object item;
        int position;
        public InsertOperation(List list, Object item, int position) {
            super();
            this.list = list;
            this.item = item;
            this.position = position;
        }
        @Override
        public void Do() throws Exception {
           list.insert(item, position);
        }
    }

}
