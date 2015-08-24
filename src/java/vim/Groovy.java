package vim;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import java.io.IOException;
import java.io.File;

public class Groovy implements Interpreter {
    private Binding binding;
    private GroovyShell shell;

    public Groovy() {
        binding = new Binding();
        binding.setVariable("Vim", Vim.getInstance());
        shell = new GroovyShell(binding);
    }

    @Override
    public void onExit() {
        ;
    }

    @Override
    public String ex_java(String text) {
        Object ret = shell.evaluate(text);
        if (ret != null)
            return ret.toString();
        else
            return null;
    }

    @Override
    public String ex_javafile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            Vim.emsg("Path given to Groovy.evalFile() does not exist: " +
                    path);
            return null;
        }
        if (!file.canRead()) {
            Vim.emsg("Path given to Groovy.evalFile() cannot be read: " +
                    path);
            return null;
        }

        Object ret = shell.evaluate(file);
        if (ret != null)
            return ret.toString();
        else
            return null;
    }

    @Override
    public Object do_javaeval(String text) {
        return null;
    }
}
