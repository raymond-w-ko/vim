package vim;

import clojure.lang.Compiler;
import clojure.lang.LineNumberingPushbackReader;
import clojure.lang.Namespace;
import clojure.lang.Symbol;
import clojure.lang.Var;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Runnable;
import java.lang.Thread;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Clojure implements Interpreter {
    ////////////////////////////////////////////////////////////////////////////
    // Switches
    ////////////////////////////////////////////////////////////////////////////
    /**
     * This switch determines whether output from the REPL is echoed back out
     * to Vim.
     *
     * It is disabled by default because most form evaluations result in 'nil',
     * and you don't want 'nil' to display everytime you run a command,
     * especially in plugins.
     */
    public static boolean PRINT_REPL_OUTPUT = false;

    ////////////////////////////////////////////////////////////////////////////
    // Helper Classes
    ////////////////////////////////////////////////////////////////////////////
    /**
     * The only way to run the REPL in Java seems to be to use a thread that
     * blocks, so we will have to spawn a separate thread.
     */
    private class BackgroundClojureRepl implements Runnable {
        Clojure parent;

        public BackgroundClojureRepl(Clojure parent) {
            this.parent = parent;
        }

        /**
         * Starts a Clojure REPL and blocks.
         *
         * 1. *vim-in*, *vim-out*, and *vim-err* are assumed to be in the user
         *    namespace and valid concrete instances of Reader or Writer.
         * 2. *vim-interpreter* also exists in the user namespace and has a method
         *    "void notifyPromptIsReady()". This indicates that to the parent thread
         *    that the "prompt" has appeared and can continue normal execution.
         *
         *    For our purposes, Vim blocks until this is called, so REPL commands
         *    should be short.
         * 3. When an exception happens, exception information and stacktrace are
         *    reported to the parent.
         */
        public void run() {
            String cmd =
                "(ns user                                                           " +
                "    (:import (clojure.lang LineNumberingPushbackReader))           " +
                "    (:require (clojure main)))                                     " +
                "(binding [*in* (LineNumberingPushbackReader. *vim-in*)             " +
                "          *out* *vim-out*                                          " +
                "          *err* *vim-err*]                                         " +
                "    (clojure.main/repl                                             " +
                "        :need-prompt (fn [] #(identity false))                     " +
                "        :prompt (fn [] nil))) "
                //"    (clojure.main/repl                                             " +
                //"        :need-prompt (fn [] #(identity true))                      " +
                //"        :prompt (fn [] (.notifyPromptIsReady *vim-interpreter*)))) "
                ;

            try {
                StringReader startReplCommand = new StringReader(cmd);
                Compiler.load(startReplCommand);
            }
            catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                String stackTrace = pw.toString();

                parent.recordErrorMessage("\n");
                parent.recordErrorMessage("thread BackgroundClojureRepl died:\n");
                parent.recordErrorMessage(e.toString() + "\n");
                parent.recordErrorMessage(stackTrace + "\n");

                // I'm not sure how to make the REPL forcibly crash to test
                // this, but if it crashes, it's probably safe to assume that
                // the main thread is waiting for the prompt which will never
                // come, so we have to signal ready ourselves
                parent.notifyPromptIsReady();
            }
        }
    }

    private class VimInputReader extends Reader {
        private boolean isTimeToClose;
        private Deque<String> deque;
        private int headRemainingChars;
        private int headIndex;
        private char[] headCharArray;

        public VimInputReader() {
            super();

            isTimeToClose = false;
            deque = new ArrayDeque<String>();
            headRemainingChars = -1;
            headIndex = -1;
        }

        /**
         * Resets some variables to prepare the 'head' for consumption.
         *
         * Also create and cache the toCharArray() call.
         */
        private void prepareDequeHead() {
            headRemainingChars = deque.getFirst().length();
            headIndex = 0;
            headCharArray = deque.getFirst().toCharArray();
        }

        /**
         * Enqueues the text that is sent by ":java XXX" into this Reader
         * so that Clojure REPL will eventually read it.
         *
         * A newline character '\n' is added to the end of input if it does not
         * already exist.
         */
        public void pushUserInput(String text) {
            if (text == null)
                return;

            synchronized (lock) {
                boolean setRemainingChars = deque.size() == 0;

                deque.addLast(text);
                // important that all input ends with a newline, otherwise
                // Clojure will continue to block expecting more input
                if (text.charAt(text.length() - 1) != '\n') {
                    deque.addLast("\n");
                }

                if (setRemainingChars) {
                    prepareDequeHead();
                }

                lock.notifyAll();
            }
        }

        /**
         * Uses deque and internal state to feed text to the Clojure REPL.
         *
         * DO NOT USE, this is for Clojure REPL to consume input.
         */
        @Override
        public int read(char[] cbuf, int off, int len) {
            synchronized (lock) {
                while (deque.size() == 0) {
                    if (isTimeToClose)
                        return -1;

                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (isTimeToClose)
                    return -1;

                int numCharsRead = 0;

                while (!(len == 0 || deque.size() == 0)) {
                    if (headRemainingChars > 0) {
                        int bytesToCopy = Math.min(len, headRemainingChars);
                        System.arraycopy(
                                headCharArray, headIndex,
                                cbuf, off,
                                bytesToCopy);

                        headIndex += bytesToCopy;
                        headRemainingChars -= bytesToCopy;

                        off += bytesToCopy;
                        len -= bytesToCopy;

                        numCharsRead += bytesToCopy;
                    } else {
                        deque.removeFirst();
                        if (deque.size() == 0)
                            break;
                        prepareDequeHead();
                    }
                }

                return numCharsRead;
            }
        }

        /**
         * Makes next read() by Clojure REPL return -1 to mean EOF reached.
         *
         * Called when VIM closes to shutdown Clojure REPL thread.
         */
        @Override
        public void close() {
            synchronized (lock) {
                isTimeToClose = true;
                lock.notifyAll();
            }
        }
    }

    private class VimOutputWriter extends Writer {
        StringBuilder buffer;
        public VimOutputWriter() {
            super();
            buffer = new StringBuilder();
        }

        /**
         * Flushes the input buffer, parses it by newlines, and converts to
         * to a List of Strings.
         *
         * Consumes the last token, even if it doesn't end in a newline.
         */
        public List<String> getOutputLines() {
            synchronized (lock) {
                List<String> realLines = new ArrayList<String>();

                String output = buffer.toString();
                if (output.length() == 0)
                    return realLines;

                String lines[] = buffer.toString().split("\\r?\\n|\\r");
                for (int i = 0; i < lines.length; ++i) {
                    realLines.add(lines[i]);
                }

                buffer = new StringBuilder();

                return realLines;
            }
        }

        /**
         * Does nothing.
         */
        @Override
        public void close() {
            ;
        }

        /**
         * Does nothing.
         */
        @Override
        public void flush() {
            ;
        }

        /**
         * DO NOT USE.
         *
         * Clojure REPL uses this to write its output
         */
        @Override
        public void write(char[] cbuf, int off, int len) {
            synchronized (lock) {
                buffer.append(cbuf, off, len);
            }
        }

        /**
         * Appends a string to the buffer.
         *
         * Does not append newlines, so you might have to do that yourself.
         */
        public void write(String message) {
            synchronized (lock) {
                buffer.append(message);
            }
        }

        /**
         * Empties the internal buffer.
         */
        public void clear() {
            synchronized (lock) {
                buffer = new StringBuilder();
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Variables
    ////////////////////////////////////////////////////////////////////////////
    private Object lock;
    private AtomicInteger promptReadyCounter;
    private VimInputReader vimIn;
    private VimOutputWriter vimOut;
    private VimOutputWriter vimErr;
    private List<java.util.concurrent.Callable> onExitCallbacks;
    private Thread backgroundThread;

    private static Clojure instance;
    public static Clojure getInstance() {
        return instance;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Begin
    ////////////////////////////////////////////////////////////////////////////

    public Clojure() {
        instance = this;

        lock = new Object();
        promptReadyCounter = new AtomicInteger();
        vimIn = new VimInputReader();
        vimOut = new VimOutputWriter();
        vimErr = new VimOutputWriter();
        onExitCallbacks = new ArrayList<java.util.concurrent.Callable>();

        Symbol user = Symbol.create("user");
        Namespace userNs = Namespace.findOrCreate(user);

        Var.intern(userNs, Symbol.create("*vim-in*"), vimIn);
        Var.intern(userNs, Symbol.create("*vim-out*"), vimOut);
        Var.intern(userNs, Symbol.create("*vim-err*"), vimErr);
        Var.intern(userNs, Symbol.create("*vim-interpreter*"), this);

        backgroundThread = new Thread(new BackgroundClojureRepl(this));
        backgroundThread.start();

        // there should never be a Vim related exception here has the
        // interpreter is just starting
        //try {
            //waitForPrompt(promptReadyCounter.getAndIncrement());
        //}
        //catch (Buffer.BufferCacheInconsistentException e) { }
        //catch (Buffer.FreedBufferException e) { }
        //catch (Window.FreedWindowException e) { }

        this.ex_java("(import vim.Vim)");
    }

    /**
     * Blocks execution until notifyPromptIsReady() is called.
     */
    private void waitForPrompt(int doneValue)
        throws
        Buffer.BufferCacheInconsistentException,
        Buffer.FreedBufferException,
        Window.FreedWindowException
    {
        while (true) {
            Vim.pollAndProcessOperationQueue();
            if (promptReadyCounter.get() == doneValue)
                break;
        }

        // using Vim.msg in map can cause Vim.msg to be called after the prompt
        // is ready!
        Vim.pollAndProcessOperationQueue();
    }

    /**
     * DO NOT USE.
     *
     * The Clojure REPL should call this instead of using the default REPL
     * prompt to tell VIM that it is safe to continue operation.
     */
    public void notifyPromptIsReady() {
        promptReadyCounter.decrementAndGet();
    }

    /**
     * Adds an error message to error buffer, which will appear next time you
     * execute a ":java XXX" command or similar.
     *
     * Does not add newlines.
     */
    public void recordErrorMessage(String message) {
        vimErr.write(message);
    }

    public void addOnExitCallback(java.util.concurrent.Callable f) {
        onExitCallbacks.add(f);
    }

    /**
     * Called when VIM is exiting.
     *
     * Mainly closes input stream so that REPL exits.
     */
    @Override
    public void onExit() {
        // we can afford to swallow exceptions here as we are exiting
        try { Vim.pollAndProcessOperationQueue(); }
        catch (Buffer.FreedBufferException e) {}
        catch (Buffer.BufferCacheInconsistentException e) {}
        catch (Window.FreedWindowException e) {}

        for (java.util.concurrent.Callable f : onExitCallbacks) {
            // we are exiting, don't bother dealing with exceptions.
            try {
                f.call();
            }
            catch (Exception e) {
            }
        }

        // send EOF to Clojure REPL
        vimIn.close();

        // we can afford to swallow exceptions here as we are exiting
        try { Vim.pollAndProcessOperationQueue(); }
        catch (Buffer.FreedBufferException e) {}
        catch (Buffer.BufferCacheInconsistentException e) {}
        catch (Window.FreedWindowException e) {}

        try {
            backgroundThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Enters text into the Clojure REPL and waits for it to be ready again.
     *
     * Can potentially never return, depending on input.
     */
    @Override
    public String ex_java(String text) {
        if (promptReadyCounter.get() > 0) {
            Vim.emsg("Recursively calling Clojure.eval() is not supported.");
            return null;
        }

        int doneValue = promptReadyCounter.getAndIncrement();
        vimIn.pushUserInput(text);
        vimIn.pushUserInput("(.notifyPromptIsReady *vim-interpreter*)");
        try {
            waitForPrompt(doneValue);
        }
        catch (Buffer.BufferCacheInconsistentException e) {
            vimErr.write("Buffer.BufferCacheInconsistentException in Clojure.eval()\n");
        }
        catch (Buffer.FreedBufferException e) {
            vimErr.write("Buffer.FreedBufferException in Clojure.eval()\n");
        }
        catch (Window.FreedWindowException e) {
            vimErr.write("Window.FreedWindowException in Clojure.eval()\n");
        }

        List<String> lines;

        if (Clojure.PRINT_REPL_OUTPUT) {
            lines = vimOut.getOutputLines();
            for (String line : lines) {
                Vim.msg(line);
            }
        }
        else {
            vimOut.clear();
        }

        lines = vimErr.getOutputLines();
        for (String line : lines) {
            Vim.emsg(line);
        }

        return null;
    }

    @Override
    public String ex_javafile(String path) {
        int doneValue = promptReadyCounter.getAndIncrement();
        String cmd = "(load-file \"" + path +"\")";
        vimIn.pushUserInput(cmd);
        vimIn.pushUserInput("(.notifyPromptIsReady *vim-interpreter*)");
        try {
            waitForPrompt(doneValue);
        }
        catch (Buffer.BufferCacheInconsistentException e) {
            vimErr.write("Buffer.BufferCacheInconsistentException in Clojure.eval()\n");
        }
        catch (Buffer.FreedBufferException e) {
            vimErr.write("Buffer.FreedBufferException in Clojure.eval()\n");
        }
        catch (Window.FreedWindowException e) {
            vimErr.write("Window.FreedWindowException in Clojure.eval()\n");
        }

        List<String> lines;

        if (Clojure.PRINT_REPL_OUTPUT) {
            lines = vimOut.getOutputLines();
            for (String line : lines) {
                Vim.msg(line);
            }
        }
        else {
            vimOut.clear();
        }

        lines = vimErr.getOutputLines();
        for (String line : lines) {
            Vim.emsg(line);
        }

        return null;
    }

    @Override
    public Object do_javaeval(String text) {
        return null;
    }
}
