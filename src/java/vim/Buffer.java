package vim;

import java.util.HashMap;
import java.util.Map;

public class Buffer {
    /**
     * Something incredibly bad has happened.
     *
     * This occurs when the code detects that the cached Buffer object's buffer
     * number and system pointer mismatch. Expect crashes or undefined
     * behavior.
     */
    public static class BufferCacheInconsistentException extends Exception {
        public BufferCacheInconsistentException(String message) {
            super(message);
        }
    }

    /**
     * This buffer object is referring to a Vim buffer that has been freed.
     *
     * An exception is thrown to prevent corrupting memory. You should discard
     * this reference immediately.
     */
    public class FreedBufferException extends Exception {
        public FreedBufferException(String message) {
            super(message);
        }
    }

    ////////////////////////////////////////////////////////////////////////////

    private static Map<Integer, Buffer> buffers;
    static
    {
        buffers = new HashMap<Integer, Buffer>();
    }

    private long vimBufferPointer;
    private int bufferNumber;
    private boolean isValid;

    /**
     * Returns the appropriate Java Buffer object associated with the Vim
     * buffer, or creates and caches a new one.
     *
     * Use this in place of the constructor. Has some runtime assertions to
     * make sure buffer number and the system pointers are consistent.
     */
    public static Buffer getOrCreate(int bufferId, long bufferPointer)
        throws BufferCacheInconsistentException
    {
        if (buffers.containsKey(bufferId)) {
            return buffers.get(bufferId);
        }

        Buffer buffer = new Buffer(bufferPointer);
        if (buffer.getNumber() != bufferId ||
                buffer.vimBufferPointer != bufferPointer)
        {
            throw new BufferCacheInconsistentException(
                    "inconsistent vim.Buffer internal cache state");
        }
        buffers.put(bufferId, buffer);
        return buffer;
    }

    /**
     * Marks this buffer as invalid and sets the Buffer object in the cache to
     * be null.
     *
     * Due to the way Buffer objects are constructed, there should at most be only
     * one Java Buffer object per Vim buffer.
     */
    public static void markBufferInvalid(int bufferId)
    {
        Buffer buffer = buffers.get(bufferId);
        buffer.isValid = false;
        buffers.put(bufferId, null);
    }

    /**
     * Creates a new buffer object given a raw system C pointer.
     *
     * Only one instance of a Java Buffer object should ever be created per Vim
     * buffer.  This ensures that it is easy to invalidate the Java Buffer when
     * it is freed on the Vim side.
     *
     */
    private Buffer(long pointer)
    {
        vimBufferPointer = pointer;
        isValid = true;
        setNumberFromVim();
    }

    /**
     * Sets this buffer as the current buffer in Vim.
     */
    public void setAsCurrent() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _setAsCurrent();
        } else {
            SetAsCurrentOperation op =
                new SetAsCurrentOperation(this);
            op.waitUntilDone();
        }
    }
    private void _setAsCurrent() throws Exception {
        if (!isValid)
            throw new FreedBufferException("setAsCurrent()");
        vim.Buffer._setAsCurrent(vimBufferPointer);
    }
    private native static void _setAsCurrent(long pointer);

    /**
     * Gets the number of lines in the buffer.
     */
    public int getNumLines() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getNumLines();
        } else {
            GetNumLinesOperation op =
                new GetNumLinesOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private int _getNumLines() throws Exception {
        if (!isValid)
            throw new FreedBufferException("getNumLines()");
        return vim.Buffer._getNumLines(vimBufferPointer);
    }
    private native static int _getNumLines(long pointer);

    /**
     * Returns the line at lineNumber in the buffer.
     */
    public String getLine(int lineNumber) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getLine(lineNumber);
        } else {
            GetLineOperation op =
                new GetLineOperation(this, lineNumber);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private String _getLine(int lineNumber) throws Exception {
        if (!isValid)
            throw new FreedBufferException("getLine()");
        return vim.Buffer._getLine(vimBufferPointer, lineNumber);
    }
    private native static String _getLine(long pointer, int lineNumber);

    public String[] getLines(int startLineNumber, int endLineNumber) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getLines(startLineNumber, endLineNumber);
        } else {
            GetLinesOperation op =
                new GetLinesOperation(this, startLineNumber, endLineNumber);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private String[] _getLines(int startLineNumber, int endLineNumber) throws Exception {
        if (!isValid)
            throw new FreedBufferException("getLines()");
        return vim.Buffer._getLines(vimBufferPointer, startLineNumber, endLineNumber);
    }
    private native static String[] _getLines(
            long pointer, int startLineNumber, int endLineNumber);

    /**
     * Returns a String array of all lines in the buffer.
     */
    public String[] getAllLines() throws Exception {
        return this.getLines(1, this.getNumLines());
    }

    /**
     * If newLine is null, the line is deleted.
     */
    public void setLine(int lineNumber, String newLine) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _setLine(lineNumber, newLine);
        } else {
            SetLineOperation op =
                new SetLineOperation(this, lineNumber, newLine);
            op.waitUntilDone();
        }
    }
    private void _setLine(int lineNumber, String newLine) throws Exception {
        if (!isValid)
            throw new FreedBufferException("setLine()");
        vim.Buffer._setLine(vimBufferPointer, lineNumber, newLine);
    }
    private native static void _setLine(long pointer, int lineNumber, String newLine);

    /**
     * Gets the short name of the buffer.
     */
    public String getName() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getName();
        } else {
            GetNameOperation op =
                new GetNameOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private String _getName() throws Exception {
        if (!isValid)
            throw new FreedBufferException("getName()");
        return vim.Buffer._getName(vimBufferPointer);
    }
    private native static String _getName(long pointer);

    /**
     * Gets the full name of the buffer.
     */
    public String getFullName() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getFullName();
        } else {
            GetFullNameOperation op =
                new GetFullNameOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private String _getFullName() throws Exception {
        if (!isValid)
            throw new FreedBufferException("getFullName()");
        return vim.Buffer._getFullName(vimBufferPointer);
    }
    private native static String _getFullName(long pointer);

    public int getNumber()
    {
        return bufferNumber;
    }
    /**
     * Used by constructor to obtain an monotonic, unchanging ID number for the
     * buffer.
     *
     * This is what is used to determine validity of the buffer. We must do this
     * as we can't get the buffer number the data structure has already been
     * freed on the C side.
     *
     * This is only called from the constructor, which is only called by
     * getOrCreate(), which should only be called from Vim.buffer(), so it
     * doesn't need to be multi-thread safe.
     */
    private void setNumberFromVim()
    {
        bufferNumber = vim.Buffer.getNumber(vimBufferPointer);
    }
    private native static int getNumber(long pointer);

    public void insertLine(String newLine) throws Exception {
        insertLine(newLine, -1);
    }
    /**
     * Insert a line into the buffer.
     *
     * if index == 0, newLine becomes the first line.
     * if index == -1, newLine becomes the last line.
     */
    public void insertLine(String newLine, int index) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _insertLine(newLine, index);
        } else {
            InsertLineOperation op =
                new InsertLineOperation(this, newLine, index);
            op.waitUntilDone();
        }
    }
    private void _insertLine(String newLine, int index) throws Exception {
        if (!isValid)
            throw new FreedBufferException("insertLine()");
        vim.Buffer._insertLine(vimBufferPointer, newLine, index);
    }
    private native static void _insertLine(long vimBufferPointer, String newLine, int index);

    /**
     * Returns the buffer "next" to this buffer.
     */
    public Buffer next() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _next();
        } else {
            NextOperation op = new NextOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private Buffer _next() throws Exception {
        if (!isValid)
            throw new FreedBufferException("next()");
        return vim.Buffer._next(vimBufferPointer);
    }
    private native static Buffer _next(long pointer);

    /**
     * Returns the buffer "previous" to this buffer.
     */
    public Buffer previous() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _previous();
        } else {
            PreviousOperation op = new PreviousOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private Buffer _previous() throws Exception {
        if (!isValid)
            throw new FreedBufferException("previous()");
        return vim.Buffer._previous(vimBufferPointer);
    }
    private native static Buffer _previous(long pointer);

    /**
     * Tests if this Java Buffer is referring to a valid in-memory Vim buffer,
     * or an invalid freed Vim buffer.
     */
    public boolean isValid()
    {
        return isValid;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Internal operations for this class
    ////////////////////////////////////////////////////////////////////////////

    private static class SetAsCurrentOperation extends Vim.Operation {
        Buffer buffer;
        public SetAsCurrentOperation(Buffer buffer) {
            super();
            this.buffer = buffer;
        }
        @Override
        public void Do() throws Exception {
            buffer.setAsCurrent();
        }
    }

    private static class GetNumLinesOperation extends Vim.Operation {
        Buffer buffer;
        int ret;
        public GetNumLinesOperation(Buffer buffer) {
            super();
            this.buffer = buffer;
        }
        @Override
        public void Do() throws Exception {
            ret = buffer.getNumLines();
        }
    }

    private static class GetLineOperation extends Vim.Operation {
        Buffer buffer;
        int lineNumber;
        String ret;
        public GetLineOperation(Buffer buffer, int lineNumber) {
            super();
            this.buffer = buffer;
            this.lineNumber = lineNumber;
        }
        @Override
        public void Do() throws Exception {
            ret = buffer.getLine(lineNumber);
        }
    }

    private static class GetLinesOperation extends Vim.Operation {
        Buffer buffer;
        int startLineNumber;
        int endLineNumber;
        String[] ret;
        public GetLinesOperation(Buffer buffer, int startLineNumber, int endLineNumber) {
            super();
            this.buffer = buffer;
            this.startLineNumber = startLineNumber;
            this.endLineNumber = endLineNumber;
        }
        @Override
        public void Do() throws Exception {
            ret = buffer.getLines(startLineNumber, endLineNumber);
        }
    }

    private static class SetLineOperation extends Vim.Operation {
        Buffer buffer;
        int lineNumber;
        String newLine;
        public SetLineOperation(Buffer buffer, int lineNumber, String newLine) {
            super();
            this.buffer = buffer;
            this.lineNumber = lineNumber;
            this.newLine = newLine;
        }
        @Override
        public void Do() throws Exception {
            buffer.setLine(lineNumber, newLine);
        }
    }

    private static class GetNameOperation extends Vim.Operation {
        Buffer buffer;
        String ret;
        public GetNameOperation(Buffer buffer) {
            super();
            this.buffer = buffer;
        }
        @Override
        public void Do() throws Exception {
            ret = buffer.getName();
        }
    }

    private static class GetFullNameOperation extends Vim.Operation {
        Buffer buffer;
        String ret;
        public GetFullNameOperation(Buffer buffer) {
            super();
            this.buffer = buffer;
        }
        @Override
        public void Do() throws Exception {
            ret = buffer.getFullName();
        }
    }

    private static class InsertLineOperation extends Vim.Operation {
        Buffer buffer;
        String newLine;
        int index;
        public InsertLineOperation(Buffer buffer, String newLine, int index) {
            super();
            this.buffer = buffer;
            this.newLine = newLine;
            this.index = index;
        }
        @Override
        public void Do() throws Exception {
            buffer.insertLine(newLine, index);
        }
    }

    private static class NextOperation extends Vim.Operation {
        Buffer buffer;
        Buffer ret;
        public NextOperation(Buffer buffer) {
            super();
            this.buffer = buffer;
        }
        @Override
        public void Do() throws Exception {
            ret = buffer.next();
        }
    }

    private static class PreviousOperation extends Vim.Operation {
        Buffer buffer;
        Buffer ret;
        public PreviousOperation(Buffer buffer) {
            super();
            this.buffer = buffer;
        }
        @Override
        public void Do() throws Exception {
            ret = buffer.previous();
        }
    }
}
