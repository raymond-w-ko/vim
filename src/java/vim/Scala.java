package vim;

import java.io.IOException;
import java.io.File;

public class Scala implements Interpreter {
    public Scala() {
    }

    @Override
    public void onExit() {
        ;
    }

    @Override
    public String ex_java(String text) {
            return null;
    }

    @Override
    public String ex_javafile(String path) throws IOException {
        return null;
    }

    @Override
    public Object do_javaeval(String text) {
        return null;
    }
}
