all: vim.jar ..\..\runtime\vim.jar

# if you are using Java 1.5 or below, you will have to specify each JAR
# individually in the following classpath argument as wildcard expansion was
# only implemented in Java 1.6 and above
vim.jar: Make_mvc.mak vim/*.java
	fetch_jars.bat
	javac -classpath "$(MAKEDIR)/thirdparty/*" vim/*.java
	jar cf vim.jar vim

..\..\runtime\vim.jar: vim.jar
	copy vim.jar ..\..\runtime\vim.jar
	
clean:
	if exist vim.jar del vim.jar
	if exist vim/*.class del vim/*.class
