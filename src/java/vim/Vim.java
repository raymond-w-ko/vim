package vim;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Collections;
import java.lang.Thread;
import java.util.concurrent.Semaphore;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicInteger;

public class Vim {
    ////////////////////////////////////////////////////////////////////////////
    // Singleton (for REPLs that need a java.lang.Object of this class)
    ////////////////////////////////////////////////////////////////////////////
    private static final Vim instance = new Vim();
    private Vim() { }
    public static Vim getInstance() { return instance; }

    ////////////////////////////////////////////////////////////////////////////
    // Helper Classes
    ////////////////////////////////////////////////////////////////////////////

    public static abstract class Operation {
        public Semaphore semaphore;
        public Exception e;

        public Operation() {
            semaphore = new Semaphore(0);
        }
        public void waitUntilDone() throws Exception {
            numPendingOperations.incrementAndGet();
            operationQueue.add(this);
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (this.e != null) {
                throw e;
            }
        }
        public abstract void Do() throws Exception;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Variaables
    ////////////////////////////////////////////////////////////////////////////

    /**
     * X of the last command in Vim that was in the form of ":X,Yjava cmd".
     *
     * Try not to change this as any piece of code might refer to it.
     */
    public static int rangeFirstLine;
    /**
     * Y of the last command in Vim that was in the form of ":X,Yjava cmd".
     *
     * Try not to change this as any piece of code might refer to it.
     */
    public static int rangeLastLine;

    /**
     * The ID number of the main thread of the JVM.
     *
     * Used to see if a Vim interop function can be executed immediately,
     * or if the operation needs to be queued for the main thread to execute.
     */
    private static long mainThreadId;

    /**
     * Atomic counter for remaining operations to be performed by the main
     * thread.
     */
    private static AtomicInteger numPendingOperations;
    /**
     * Lists of operations for the main thread to perform.
     */
    private static List<Operation> operationQueue;

    /**
     * The currently selected interpreter, from ":javarepl XXX".
     */
    private static Interpreter interpreter;
    /**
     * Mapping of interpeter names to active instances of interpreters.
     */
    private static Map<String, Interpreter> interpreters;

    ////////////////////////////////////////////////////////////////////////////
    // Functions
    ////////////////////////////////////////////////////////////////////////////

    /**
     * DO NOT USE.
     *
     * This is executed immediately after the JVM is initialized and the native
     * methods in if_java.c have been registered via JNI.
     *
     * Do any initialization that needs to occur before interpreters are created here.
     */
    public static boolean init() {
        operationQueue = Collections.synchronizedList(new LinkedList<Vim.Operation>());
        numPendingOperations = new AtomicInteger(0);

        mainThreadId = Thread.currentThread().getId();

        rangeFirstLine = 1;
        rangeLastLine = 1;

        interpreter = null;
        interpreters = new HashMap<String, Interpreter>();

        return true;
    }

    /**
     * DO NOT USE.
     *
     * Vim calls this when exiting to tell give the interpreters a chance to
     * cleanup things. Especially essential in interpreters like Clojure, where
     * it must signal a thread to exit.
     *
     * The general plan is that each interpreters implements some sort of
     * collection where plugin code can submit callbacks, and this will trigger
     * the process of calling all the callbacks.
     */
    public static void onExit() throws InterruptedException, IOException {
        for (Map.Entry<String, Interpreter> entry : interpreters.entrySet()) {
            entry.getValue().onExit();
        }
    }

    /**
     * DO NO USE.
     *
     * When a ranged command is issued, like ":X,Yjava code", Vim's if_java.c
     * calls this to store X, so that the Java side can know what X was.
     */
    public static void setRangeFirstLine(int lineNumber) {
        Vim.rangeFirstLine = lineNumber;
    }

    /**
     * DO NO USE.
     *
     * When a ranged command is issued, like ":X,Yjava code", Vim's if_java.c
     * calls this to store Y, so that the Java side can know what Y was.
     */
    public static void setRangeLastLine(int lineNumber) {
        Vim.rangeLastLine = lineNumber;
    }

    /**
     * DO NOT USE.
     *
     * Helper function used by Vim to detect reference cycles in collections.
     */
    public static void setRefInCollections(int copyID) {
        vim.List.setVimGCRefOnAllLists(copyID);
        vim.Dict.setVimGCRefOnAllDicts(copyID);
    }

    /**
     * Tells Vim on the C side to decrement the reference count for
     * Java List and Dict objects that have been GCed.
     *
     * You may call this at any time.
     */
    private static void doVimCollectionGC() {
        vim.List.purgeGarbage();
        vim.Dict.purgeGarbage();
    }

    /**
     * implementation of ":java XXX", DO NOT USE unless you are if_java.c.
     *
     * Assumes that ":javarepl XXX" has been called and is successful.
     *
     * @return Will be displayed as a normal message, unless null.
     */
    public static String ex_java(String arg) {
        doVimCollectionGC();

        if (interpreter == null)
            return null;

        String ret;
        try {
            ret = interpreter.ex_java(arg);
        } catch (Exception e) {
            ret = e.toString();
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                ret = ret + "\n" + ste.toString();
            }
        }
        if (ret != null) {
            String lines[] = ret.split("\\r?\\n");
            for (String line : lines) {
                Vim.msg(line);
            }
        }

        doVimCollectionGC();

        return null;
    }

    /**
     * implementation of ":javafile XXX", DO NOT USE unless you are if_java.c.
     *
     * Assumes that ":javarepl XXX" has been called and is successful.
     *
     * @return Will be displayed as a normal message, unless null.
     */
    public static String ex_javafile(String path) {
        doVimCollectionGC();

        if (interpreter == null)
            return null;

        String ret;
        try {
            ret = interpreter.ex_javafile(path);
        } catch (Exception e) {
            ret = e.toString();
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                ret = ret + "\n" + ste.toString();
            }
        }

        if (ret != null) {
            String lines[] = ret.split("\\r?\\n");
            for (String line : lines) {
                Vim.msg(line);
            }
        }

        doVimCollectionGC();

        return null;
    }

    public static Object do_javaeval(String arg) {
        doVimCollectionGC();

        if (interpreter == null)
            return null;

        Object ret;
        String errorMessage = null;
        try {
            ret = interpreter.do_javaeval(arg);
        } catch (Exception e) {
            ret = null;
            errorMessage = e.toString();
        }

        if (errorMessage != null) {
            String lines[] = errorMessage.split("\\r?\\n");
            for (String line : lines) {
                Vim.emsg(line);
            }
        }

        doVimCollectionGC();

        return "do_javaeval() not implemented yet";
    }

    /**
     * implementation of ":javarepl XXX", DO NOT USE unless you are if_java.c.
     *
     * Current the only valid arguments are "groovy" and "clojure".
     *
     * It should be fairly easy to add others if they have some sort of REPL
     * built in and can perform Java interop.
     */
    public static void ex_javarepl(String replName) {
        doVimCollectionGC();

        // see if we have created an instance of the requested interpreter
        if (interpreters.containsKey(replName)) {
            interpreter = interpreters.get(replName);
            return;
        }

        Interpreter inst;
        // a switch statement should be used, but that is Java 7 only :-(
        if (replName.equals("clojure")) {
            inst = new Clojure();
        } else if (replName.equals("groovy")) {
            inst = new Groovy();
        } else if (replName.equals("jruby")) {
            inst = new JRuby();
        } else if (replName.equals("scala")) {
            inst = new Scala();
        } else {
            inst = null;
        }

        if (inst != null) {
            interpreters.put(replName, inst);
            interpreter = inst;
        }

        doVimCollectionGC();
    }

    /**
     * Process operations requested by another thread on the main thread.
     *
     * @return true, if this function should be called again as soon as possible;
     *         false, if it is not necessary to re-call this function at this time.
     */
    private static boolean processOperationQueue()
        throws
        Buffer.BufferCacheInconsistentException,
        Buffer.FreedBufferException,
        Window.FreedWindowException
    {
        if (Thread.currentThread().getId() != mainThreadId) {
            Vim._emsg("Vim.processOperationQueue() MUST be called from main thread!");
            return false;
        }

        boolean operationPerformed = false;

        while (operationQueue.size() > 0) {
            Operation op = operationQueue.remove(0);
            try {
                op.Do();
            } catch (Exception e) {
                op.e = e;
            } finally {
                op.semaphore.release();
                numPendingOperations.decrementAndGet();
            }
            operationPerformed = true;
        }

        return operationPerformed;
    }

    public static long getMainThreadId() {
        return mainThreadId;
    }

    /**
     * Used by the various interpreter classes to queue operations that need to
     * be executed in the main thread.
     */
    public static void addPendingOperation(Operation op) {
        operationQueue.add(op);
    }

    /**
     * Used by the various interpreter classes to poll and process any pending
     * operations that need to be performed on the main thread.
     *
     * As a result any interpreter classes that need this need to spin-wait
     */
    public static void pollAndProcessOperationQueue()
        throws
        Buffer.FreedBufferException,
        Buffer.BufferCacheInconsistentException,
        Window.FreedWindowException
    {
        while (numPendingOperations.get() > 0) {
            boolean maybeAnotherOperation = Vim.processOperationQueue();
            while (maybeAnotherOperation) {
                maybeAnotherOperation = Vim.processOperationQueue();
            }
        }
    }

    /**
     * Displays the given text as a normal message in VIM.
     */
    public static void msg(String text) {
        if (Thread.currentThread().getId() == mainThreadId) {
            _msg(text);
        } else {
            MsgOperation op = new MsgOperation(text);
            try { op.waitUntilDone(); } catch (Exception e) {}
        }
    }
    public static void msg(Object object) {
        Vim.msg(object.toString());
    }
    private static native void _msg(String text);

    /**
     * Displays the given text as an error message in VIM.
     *
     * This message is usually red if you have a color enabled version of VIM.
     */
    public static void emsg(String text) {
        if (Thread.currentThread().getId() == mainThreadId) {
            _emsg(text);
        } else {
            EmsgOperation op = new EmsgOperation(text);
            try { op.waitUntilDone(); } catch (Exception e) {}
        }
    }
    public static void emsg(Object object) {
        Vim.emsg(object.toString());
    }
    private static native void _emsg(String text);

    /**
     * Evaluates the given Vim expression and converts it to the equivalent
     * Java object.
     */
    public static Object eval(String text) throws Exception {
        if (Thread.currentThread().getId() == mainThreadId) {
            return _eval(text);
        } else {
            EvalOperation op = new EvalOperation(text);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private static native Object _eval(String text);

    /**
     * Evaluate the given text as a VIM Ex command.
     *
     * Useful for setting or manipulating Vim settings and variables.
     */
    public static void command(String text) throws Exception {
        if (Thread.currentThread().getId() == mainThreadId) {
            _command(text);
        } else {
            CommandOperation op = new CommandOperation(text);
            op.waitUntilDone();
        }
    }
    private static native void _command(String text);

    /**
     * Makes Vim beep.
     */
    public static void beep() throws Exception {
        if (Thread.currentThread().getId() == mainThreadId) {
            _beep();
        } else {
            BeepOperation op = new BeepOperation();
            op.waitUntilDone();
        }
    }
    private static native void _beep();

    /**
     * Returns a Java proxy object that represents a Vim buffer.
     *
     * If arg is a number, then it returns a buffer with the number arg. If it
     * isn't a number, return the buffer with the short name or long name of
     * arg. Otherwise, returns null if no matching buffer is found.
     */
    public static Buffer buffer(String arg) throws Exception {
        if (Thread.currentThread().getId() == mainThreadId) {
            return _buffer(arg);
        } else {
            BufferOperation op = new BufferOperation(arg);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private static native Buffer _buffer(String arg);

    /**
     * Marks that the buffer with buffer ID number has been freed, and
     * no operations should further be performed on it.
     *
     * Called by Vim on the C side to signal that a buffer has been deleted.
     */
    public static void markBufferInvalid(int bufferId) {
        Buffer.markBufferInvalid(bufferId);
    }

    /**
     * Returns a Java proxy object that represents a Vim window.
     *
     * If arg is a number, then it returns a window with the number arg. Note
     * that this simply retrieves the n-th window in the linked list. If arg
     * equals the string "true", then the first window is returned. If arg
     * equals the string "false", then the current window is returned. When a
     * value of null is returned, indicates that no matching window was found.
     */
    public static Window window(String arg) throws Exception {
        if (Thread.currentThread().getId() == mainThreadId) {
            return _window(arg);
        } else {
            WindowOperation op = new WindowOperation(arg);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private static native Window _window(String arg);
    /**
     * Marks that a particular Vim window has been freed, and that no
     * operations should further be performed on it.
     *
     * Called by Vim on the C side to signal that a window has been deleted.
     */
    public static void markWindowInvalid(long pointer) {
        Window.markWindowInvalid(pointer);
    }

    /**
     * Returns the current line (without the trailing <EOL>).
     */
    public static String line() throws Exception {
        if (Thread.currentThread().getId() == mainThreadId) {
            return _line();
        } else {
            LineOperation op = new LineOperation();
            op.waitUntilDone();
            return op.ret;
        }
    }
    private static native String _line();

    /**
     * Opens a new buffer for file "fname" and returns it.
     *
     * Note that the buffer is not set as current.
     */
    public static Buffer open(String fname) throws Exception {
        if (Thread.currentThread().getId() == mainThreadId) {
            return _open(fname);
        } else {
            OpenOperation op = new OpenOperation(fname);
            op.waitUntilDone();
            return op.ret;
        }
    }
    private static native Buffer _open(String fname);

    ////////////////////////////////////////////////////////////////////////////
    // Internal operations for this class
    ////////////////////////////////////////////////////////////////////////////

    private static class MsgOperation extends Operation {
        String text;
        public MsgOperation(String text) {
            super();
            this.text = text;
        }
        @Override
        public void Do() throws Exception {
            Vim.msg(text);
        }
    }

    private static class EmsgOperation extends Operation {
        String text;
        public EmsgOperation(String text) {
            super();
            this.text = text;
        }
        @Override
        public void Do() throws Exception {
            Vim.emsg(text);
        }
    }

    private static class EvalOperation extends Operation {
        String text;
        Object ret;
        public EvalOperation(String text) {
            super();
            this.text = text;
        }
        @Override
        public void Do() throws Exception {
            ret = Vim.eval(text);
        }
    }

    private static class CommandOperation extends Operation {
        String text;
        public CommandOperation(String text) {
            super();
            this.text = text;
        }
        @Override
        public void Do() throws Exception {
            Vim.command(text);
        }
    }

    private static class BeepOperation extends Operation {
        String text;
        Object ret;
        public BeepOperation() {
            super();
        }
        @Override
        public void Do() throws Exception {
            Vim.beep();
        }
    }

    private static class BufferOperation extends Operation {
        String arg;
        Buffer ret;
        public BufferOperation(String arg) {
            super();
            this.arg = arg;
        }
        @Override
        public void Do() throws Exception {
            ret = Vim.buffer(arg);
        }
    }

    private static class WindowOperation extends Operation {
        String arg;
        Window ret;
        public WindowOperation(String arg) {
            super();
            this.arg = arg;
        }
        @Override
        public void Do() throws Exception {
            ret = Vim.window(arg);
        }
    }

    private static class LineOperation extends Operation {
        String ret;
        public LineOperation() {
            super();
        }
        @Override
        public void Do() throws Exception {
            ret = Vim.line();
        }
    }

    private static class OpenOperation extends Operation {
        String fname;
        Buffer ret;
        public OpenOperation(String fname) {
            super();
            this.fname = fname;
        }
        @Override
        public void Do() throws Exception {
            ret = Vim.open(fname);
        }
    }
}
