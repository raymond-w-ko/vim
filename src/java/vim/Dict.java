package vim;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.lang.ref.WeakReference;

public class Dict implements Iterable<Dict.DictPair>{
    ////////////////////////////////////////////////////////////////////////////
    // Helper Classes
    ////////////////////////////////////////////////////////////////////////////
    public static class DictLockedException extends Exception {
        public DictLockedException(String message) {
            super(message);
        }
    }

    public static class DictPair {
        private String key;
        private Object value;
        public DictPair(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return this.key;
        }

        public Object getValue() {
            return this.value;
        }
    }

    public static class DictIterator implements Iterator<DictPair> {
        private Dict dict;
        private long hashItemPointer;
        private long numItems;
        private long index;

        public DictIterator(Dict dict) {
            this.dict = dict;
            try { numItems = dict.size(); } catch (Exception e) {}
            index = 0;
            hashItemPointer = getHashTableArrayPointer(dict.getPointer());
        }

        public boolean hasNext() {
            if (index < numItems) {
                return true;
            }
            else {
                return false;
            }
        }

        public DictPair next() {
            if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
                return _next();
            } else {
                NextOperation op = new NextOperation(this);
                // we can't throw exceptions since this function has to have
                // the exact same declaration as the interface
                try { op.waitUntilDone(); } catch (Exception e) {}
                return op.ret;
            }
        }
        private DictPair _next() {
            hashItemPointer = getNextHashItemPointer(hashItemPointer);
            String key = getKeyOfHashItem(hashItemPointer);
            Object value = getValueOfHashItem(hashItemPointer);
            hashItemPointer = incrementHashItemPointer(hashItemPointer);
            index++;
            return new DictPair(key, value);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
        * INTERNAL FUNCTION, not meant for general use.
        *
        * Returns the pointer the dict's hashtable's array.
        */
        private native static long getHashTableArrayPointer(long pointer);
        /**
         * INTERNAL FUNCTION, not meant for general use.
         *
         * Traverses the hash item pointer till the next non-empty hash item
         * pointer.
         */
        private native static long getNextHashItemPointer(long pointer);

        /**
         * INTERNAL FUNCTION, not meant for general use.
         *
         * Gets the key of the hash item pair.
         */
        private native static String getKeyOfHashItem(long pointer);

        /**
         * INTERNAL FUNCTION, not meant for general use.
         *
         * Gets the value of the hash item pair.
         */
        private native static Object getValueOfHashItem(long pointer);

        /**
         * INTERNAL FUNCTION, not meant for general use.
         *
         * Increments a pointer by sizeof(hashitem_T)
         */
        private native static long incrementHashItemPointer(long pointer);

        ////////////////////////////////////////////////////////////////////////
        // Internal operations for this class
        ////////////////////////////////////////////////////////////////////////
        public static class NextOperation extends Vim.Operation {
            DictIterator dictIterator;
            DictPair ret;

            public NextOperation(DictIterator dictIterator) {
                super();
                this.dictIterator = dictIterator;
            }
            @Override
            public void Do() throws Buffer.FreedBufferException, Window.FreedWindowException {
                ret = dictIterator.next();
            }
        }
    }

    /**
     * This is used in finalize() to prevent race conditions that could
     * possibly occur when decrementing reference counts.
     *
     * Note, that I'm sure how the JVM GC works, and this could possibly not
     * even be an issue.
     */
    private static Object lock;
    private static java.util.Set<Long> garbageDicts;
    private static java.util.Map<Long, WeakReference<vim.Dict>> activeDicts;

    static {
        garbageDicts = new HashSet<Long>();
        activeDicts = new HashMap<Long, WeakReference<vim.Dict>>();
        lock = new Object();
    }

    /**
     * Decrements the Vim reference count of all Java Dict objects that have
     * been deleted by the GC.
     *
     * Mainly called by vim/Vim, but anyone can safely call this.
     *
     * Only call this from the main thread.
     */
    public static void purgeGarbage() {
        if (Thread.currentThread().getId() != Vim.getMainThreadId())
            return;

        synchronized (vim.Dict.lock) {
            for (Long pointer : garbageDicts) {
                vim.Dict.decrementReferenceCount(pointer.longValue());
            }
            garbageDicts.clear();
        }
    }

    /**
     * Purge this raw C pointer from garbageDicts.
     *
     * Failure to do so can cause double free() in rare cases.
     */
    public static void purge(long pointer) {
        synchronized(vim.Dict.lock) {
            garbageDicts.remove(pointer);
        }
    }

    private native static void incrementReferenceCount(long pointer);
    private native static void decrementReferenceCount(long pointer);

    private long vimDictPointer;

    /**
     * Gets or creates a Java Dict object given a raw system C pointer.
     */
    public static vim.Dict getOrCreate(long pointer) {
        synchronized (vim.Dict.lock) {
            garbageDicts.remove(pointer);

            WeakReference<vim.Dict> ref = activeDicts.get(pointer);
            // hasn't been created yet
            if (ref == null) {
                vim.Dict dict = new Dict(pointer);
                dict.incrementReferenceCount(pointer);
                ref = new WeakReference<vim.Dict>(dict);
                activeDicts.put(pointer, ref);
                return dict;
            }
            else {
                vim.Dict potential_dict = ref.get();
                // freed by GC
                if (potential_dict == null) {
                    vim.Dict dict = new Dict(pointer);
                    dict.incrementReferenceCount(pointer);
                    ref = new WeakReference<vim.Dict>(dict);
                    activeDicts.put(pointer, ref);
                    return dict;
                }
                // already exists
                else {
                    return potential_dict;
                }
            }
        }
    }
    private Dict(long pointer) {
        vimDictPointer = pointer;
    }
    public long getPointer() {
        return vimDictPointer;
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
        synchronized (vim.Dict.lock) {
            activeDicts.remove(vimDictPointer);
            garbageDicts.add(vimDictPointer);
        }
    }
    public static void setVimGCRefOnAllDicts(int copyID) {
        synchronized (vim.Dict.lock) {
            Collection<WeakReference<vim.Dict>> collection = activeDicts.values();
            for (WeakReference<vim.Dict> ref : collection) {
                vim.Dict dict = ref.get();
                if (dict != null) {
                    setVimGCRef(dict.getPointer(), copyID);
                }
            }
        }
    }
    public native static void setVimGCRef(long pointer, int copyID);

    /**
     * Returns the number of items (pairs) in this dict.
     *
     * Equivalent to len(d) in Vim.
     */
    public long size() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _size();
        } else {
            SizeOperation op = new SizeOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private long _size() {
        return vim.Dict._size(vimDictPointer);
    }
    private native static long _size(long pointer);

    /**
     * Gets the item associated with 'key'.
     */
    public Object get(String key) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _get(key);
        } else {
            GetOperation op = new GetOperation(this, key);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private Object _get(String key) {
        return vim.Dict._get(vimDictPointer, key);
    }
    private native static Object _get(long pointer, String key);

    /**
     * Insert the key-value pair into the dictionary, and replaces any
     * existing key-value pair with the same key.
     */
    public void put(String key, Object value) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _put(key, value);
        } else {
            PutOperation op = new PutOperation(this, key, value);
            op.waitUntilDone();
        }
    }
    private void _put(String key, Object value) {
        vim.Dict._put(vimDictPointer, key, value);
    }
    private native static void _put(long pointer, String key, Object value);

    /**
     * Removes a key-value pair from the dictionary.
     */
    public void remove(String key) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _remove(key);
        } else {
            RemoveOperation op = new RemoveOperation(this, key);
            op.waitUntilDone();
        }
    }
    private void _remove(String key) {
        vim.Dict._remove(vimDictPointer, key);
    }
    private native static void _remove(long pointer, String key);

    /**
     * Provides an iterator over this collection and implements Iterable.
     */
    public DictIterator iterator() {
        return new DictIterator(this);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Internal operations for this class
    ////////////////////////////////////////////////////////////////////////////

    private static class SizeOperation extends Vim.Operation {
        Dict dict;
        long ret;
        public SizeOperation(Dict dict) {
            super();
            this.dict = dict;
        }
        @Override
        public void Do() throws Exception {
            ret = dict.size();
        }
    }

    private static class GetOperation extends Vim.Operation {
        Dict dict;
        String key;
        Object ret;
        public GetOperation(Dict dict, String key) {
            super();
            this.dict = dict;
            this.key = key;
        }
        @Override
        public void Do() throws Exception {
            ret = dict.get(key);
        }
    }

    private static class PutOperation extends Vim.Operation {
        Dict dict;
        String key;
        Object value;
        public PutOperation(Dict dict, String key, Object value) {
            super();
            this.dict = dict;
            this.key = key;
            this.value = value;
        }
        @Override
        public void Do() throws Exception {
            dict.put(key, value);
        }
    }

    private static class RemoveOperation extends Vim.Operation {
        Dict dict;
        String key;
        String value;
        public RemoveOperation(Dict dict, String key) {
            super();
            this.dict = dict;
            this.key = key;
            this.value = value;
        }
        @Override
        public void Do() throws Exception {
            dict.remove(key);
        }
    }

}
