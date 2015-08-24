REM @ECHO OFF

SET CLOJURE_VERSION=1.5.1
SET GROOVY_VERSION=2.1.6
SET JRUBY_VERSION=1.7.4

if not "%TEMP%"=="" (
    set _TEMP=%TEMP%
)
if not "%TMP%"=="" (
    set _TEMP=%TMP%
)

if "%_TEMP%"=="" (
    echo Could not find a temp directory, both TEMP and TMP environment variables empty
    EXIT /b 1
)

mkdir "%_TEMP%\vim-java"

if not exist thirdparty\clojure-%CLOJURE_VERSION%.jar (
    if not exist "%_TEMP%\vim-java\clojure-%CLOJURE_VERSION%.jar" (
        curl -o "%_TEMP%\vim-java\clojure-%CLOJURE_VERSION%.jar" http://repo1.maven.org/maven2/org/clojure/clojure/%CLOJURE_VERSION%/clojure-%CLOJURE_VERSION%.jar
    )
    copy "%_TEMP%\vim-java\clojure-%CLOJURE_VERSION%.jar" thirdparty
)

if not exist thirdparty\groovy-all-%GROOVY_VERSION%.jar (
    if not exist "%_TEMP%\vim-java\groovy-all-%GROOVY_VERSION%.jar" (
        curl -o "%_TEMP%\vim-java\groovy-all-%GROOVY_VERSION%.jar" http://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/%GROOVY_VERSION%/groovy-all-%GROOVY_VERSION%.jar
    )
    copy "%_TEMP%\vim-java\groovy-all-%GROOVY_VERSION%.jar" thirdparty
)

if not exist thirdparty\jruby-%JRUBY_VERSION%.jar (
    if not exist "%_TEMP%\vim-java\jruby-%JRUBY_VERSION%.jar" (
        curl -o "%_TEMP%\vim-java\jruby-%JRUBY_VERSION%.jar" http://repo1.maven.org/maven2/org/jruby/jruby/1.7.4/jruby-1.7.4.jar
    )
    copy "%_TEMP%\vim-java\jruby-%JRUBY_VERSION%.jar" thirdparty
)

:END
