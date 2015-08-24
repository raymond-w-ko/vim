package vim;

import java.util.HashMap;
import java.util.Map;

public class Window {
    /**
     * This Window object is referring to a Vim window that has been freed.
     *
     * An exception is thrown to prevent corrupting memory. You should discard
     * this reference immediately.
     */
    public class FreedWindowException extends Exception {
        public FreedWindowException(String message) {
            super(message);
        }
    }

    private static Map<Long, Window> windows;
    static {
        windows = new HashMap<Long, Window>();
    }

    private long vimWindowPointer;
    private boolean isValid;

    /**
     * Returns the appropriate Java Window object associated with the Vim
     * window, or creates and caches a new one.
     *
     * Use this in place of the constructor.
     *
     * Note that due the fact that Vim windows have no ID number, there is a
     * really small chance that this could give you two different Java objects
     * with the same system pointer, but only one of them is valid. This
     * happens when a window is freed, and a new window is created at the exact
     * memory offset.
     */
    public static Window getOrCreate(long windowPointer) {
        if (windows.containsKey(windowPointer)) {
            return windows.get(windowPointer);
        }

        Window window = new Window(windowPointer);
        windows.put(windowPointer, window);
        return window;
    }

    /**
     * Marks this window as invalid and set the Window object in the cache
     * to be null.
     *
     * Due to the way Window objects are constructed, there should be at most
     * one Java Window object per Vim window. Note that since Vim windows don't
     * have distinct IDs, it is possible for there to be two window objects
     * with the same pointer, but only one should have isValid == true and the
     * rest should have isValid == false
     */
    public static void markWindowInvalid(long windowPointer) {
        Window window = windows.get(windowPointer);
        window.isValid = false;
        windows.remove(windowPointer);
    }

    /**
     * Creates a new window object given a raw system C pointer.
     *
     * Only one instance of a Java Window object should ever be created per Vim
     * window. This ensure that it is easy to invalidate the Java Window when
     * it is freed on the Vim side. Like mentioned in markWindowInvalid(), it
     * is possible for two Window objects to have the same vimWindowPointer.
     */
    private Window(long pointer) {
        vimWindowPointer = pointer;
        isValid = true;
    }

    /**
     * Set this window as the current window in Vim.
     */
    public void setAsCurrent() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _setAsCurrent();
        } else {
            SetAsCurrentOperation op = new SetAsCurrentOperation(this);
            op.waitUntilDone();
        }
    }
    private void _setAsCurrent() throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("setAsCurrent()");
        vim.Window._setAsCurrent(vimWindowPointer);
    }
    private native static void _setAsCurrent(long pointer);

    /**
     * Gets the buffer associated with this window as a Java Buffer object.
     */
    public Buffer getBuffer() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getBuffer();
        } else {
            GetBufferOperation op = new GetBufferOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private Buffer _getBuffer() throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("getBuffer()");
        return vim.Window._getBuffer(vimWindowPointer);
    }
    private native static Buffer _getBuffer(long pointer);

    /**
     * Gets the current cursor line position of this window.
     */
    public int getLinePos() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getLinePos();
        } else {
            GetLinePosOperation op = new GetLinePosOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private int _getLinePos() throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("getLinePos  ()");
        return vim.Window._getLinePos(vimWindowPointer);
    }
    private native static int _getLinePos(long pointer);

    /**
     * Sets the current cursor line position of this window.
     *
     * Note that linePos must between 1 (inclusive) and the buffer's line
     * count (inclusive) or an exception is thrown.
     */
    public void setLinePos(int linePos) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _setLinePos(linePos);
        } else {
            SetLinePosOperation op = new SetLinePosOperation(this, linePos);
            op.waitUntilDone();
        }
    }
    private void _setLinePos(int linePos) throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("setLinePos()");
        boolean ret = vim.Window._setLinePos(vimWindowPointer, linePos);
        if (!ret) {
            throw new java.lang.IndexOutOfBoundsException(
                    "Window.setLinePos()");
        }
    }
    private native static boolean _setLinePos(long pointer, int linePos);

    /**
     * Gets the current cursor column position of this window.
     */
    public int getColPos() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getColPos();
        } else {
            GetColPosOperation op = new GetColPosOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private int _getColPos() throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("getColPos()");
        return vim.Window._getColPos(vimWindowPointer);
    }
    private native static int _getColPos(long pointer);

    /**
     * Sets the current cursor column position of this window.
     */
    public void setColPos(int colPos) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _setColPos(colPos);
        } else {
            SetColPosOperation op = new SetColPosOperation(this, colPos);
            op.waitUntilDone();
        }
    }
    private void _setColPos(int colPos) throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("setColPos()");
        vim.Window._setColPos(vimWindowPointer, colPos);
    }
    private native static void _setColPos(long pointer, int colPos);

    /**
     * Gets the width of this window.
     */
    public int getWidth() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getWidth();
        } else {
            GetWidthOperation op = new GetWidthOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private int _getWidth() throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("getWidth()");
        return vim.Window._getWidth(vimWindowPointer);
    }
    private native static int _getWidth(long pointer);

    /**
     * Sets the width of this window.
     */
    public void setWidth(int width) throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _setWidth(width);
        } else {
            SetWidthOperation op = new SetWidthOperation(this, width);
            op.waitUntilDone();
        }
    }
    private void _setWidth(int width) throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("setWidth()");
        vim.Window._setWidth(vimWindowPointer, width);
    }
    private native static void _setWidth(long pointer, int width);

    /**
     * Gets the height of this window.
     */
    public int getHeight() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _getHeight();
        } else {
            GetHeightOperation op = new GetHeightOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private int _getHeight() throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("getHeight()");
        return vim.Window._getHeight(vimWindowPointer);
    }
    private native static int _getHeight(long pointer);

    /**
     * Sets the height of this window.
     */
    public void setHeight(int height) throws Exception {
         if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            _setHeight(height);
        } else {
            SetHeightOperation op = new SetHeightOperation(this, height);
            op.waitUntilDone();
        }
    }
    private void _setHeight(int height) throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("setHeight()");
        vim.Window._setHeight(vimWindowPointer, height);
    }
    private native static void _setHeight(long pointer, int height);

    /**
     * Returns the window "next" to this window.
     */
    public Window next() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _next();
        } else {
            NextOperation op = new NextOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private Window _next() throws Exception {
        if (!isValid)
            throw new FreedWindowException("next()");
        return vim.Window._next(vimWindowPointer);
    }
    private native static Window _next(long pointer);

    /**
     * Returns the window "previous" to this window.
     */
    public Window previous() throws Exception {
        if (Thread.currentThread().getId() == Vim.getMainThreadId()) {
            return _previous();
        } else {
            PreviousOperation op = new PreviousOperation(this);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private Window _previous() throws FreedWindowException {
        if (!isValid)
            throw new FreedWindowException("previous()");
        return vim.Window._previous(vimWindowPointer);
    }
    private native static Window _previous(long pointer);

    /**
     * Tests if this Java Window is referring to a valid in-memory Vim window,
     * or an invalid freed Vim window.
     */
    public boolean isValid()
    {
        return isValid;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Internal operations for this class
    ////////////////////////////////////////////////////////////////////////////

    private static class SetAsCurrentOperation extends Vim.Operation {
        Window window;
        public SetAsCurrentOperation(Window window) {
            super();
            this.window = window;
        }
        @Override
        public void Do() throws Exception {
            window.setAsCurrent();
        }
    }

    private static class GetBufferOperation extends Vim.Operation {
        Window window;
        Buffer ret;
        public GetBufferOperation(Window window) {
            super();
            this.window = window;
        }
        @Override
        public void Do() throws Exception {
            ret = window.getBuffer();
        }
    }

    private static class GetLinePosOperation extends Vim.Operation {
        Window window;
        int ret;
        public GetLinePosOperation(Window window) {
            super();
            this.window = window;
        }
        @Override
        public void Do() throws Exception {
            ret = window.getLinePos();
        }
    }

    private static class SetLinePosOperation extends Vim.Operation {
        Window window;
        int linePos;
        public SetLinePosOperation(Window window, int linePos) {
            super();
            this.window = window;
            this.linePos = linePos;
        }
        @Override
        public void Do() throws Exception {
            window.setLinePos(linePos);
        }
    }

    private static class GetColPosOperation extends Vim.Operation {
        Window window;
        int ret;
        public GetColPosOperation(Window window) {
            super();
            this.window = window;
        }
        @Override
        public void Do() throws Exception {
            ret = window.getColPos();
        }
    }

    private static class SetColPosOperation extends Vim.Operation {
        Window window;
        int colPos;
        public SetColPosOperation(Window window, int colPos) {
            super();
            this.window = window;
            this.colPos = colPos;
        }
        @Override
        public void Do() throws Exception {
            window.setColPos(colPos);
        }
    }

    private static class GetWidthOperation extends Vim.Operation {
        Window window;
        int ret;
        public GetWidthOperation(Window window) {
            super();
            this.window = window;
        }
        @Override
        public void Do() throws Exception {
            ret = window.getWidth();
        }
    }

    private static class SetWidthOperation extends Vim.Operation {
        Window window;
        int width;
        public SetWidthOperation(Window window, int width) {
            super();
            this.window = window;
            this.width = width;
        }
        @Override
        public void Do() throws Exception {
            window.setWidth(width);
        }
    }

    private static class GetHeightOperation extends Vim.Operation {
        Window window;
        int ret;
        public GetHeightOperation(Window window) {
            super();
            this.window = window;
        }
        @Override
        public void Do() throws Exception {
            ret = window.getHeight();
        }
    }

    private static class SetHeightOperation extends Vim.Operation {
        Window window;
        int height;
        public SetHeightOperation(Window window, int height) {
            super();
            this.window = window;
            this.height = height;
        }
        @Override
        public void Do() throws Exception {
            window.setHeight(height);
        }
    }

    private static class NextOperation extends Vim.Operation {
        Window window;
        Window ret;
        public NextOperation(Window window) {
            super();
            this.window = window;
        }
        @Override
        public void Do() throws Exception {
            ret = window.next();
        }
    }

    private static class PreviousOperation extends Vim.Operation {
        Window window;
        Window ret;
        public PreviousOperation(Window window) {
            super();
            this.window = window;
        }
        @Override
        public void Do() throws Exception {
            ret = window.previous();
        }
    }
}
