package vim;

import java.lang.String;
import java.io.IOException;

interface Interpreter {
    public abstract void onExit();

    public abstract String ex_java(String text);
    public abstract String ex_javafile(String path) throws IOException;
    public abstract Object do_javaeval(String text);
}
