package vim;

import java.io.IOException;
import java.io.File;
import org.jruby.embed.ScriptingContainer;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.PathType;

public class JRuby implements Interpreter {
    ScriptingContainer container;
    public JRuby() {
        container = new ScriptingContainer(LocalVariableBehavior.PERSISTENT);
    }

    @Override
    public void onExit() {
        ;
    }

    @Override
    public String ex_java(String text) {
        Object ret = container.runScriptlet(text);
        if (ret != null)
            return ret.toString();
        else
            return null;
    }

    @Override
    public String ex_javafile(String path) throws IOException {
        Object ret = container.runScriptlet(PathType.ABSOLUTE, path);
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
