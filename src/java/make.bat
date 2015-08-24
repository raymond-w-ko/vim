@echo off
pushd %~dp0

cls

javac -cp ^
C:/Users/root/java/clojure-1.5.1.jar;^
C:/Users/root/java/groovy-all-2.1.2-indy.jar;^
 ^
vim/*.java

jar 0cf vim.jar vim

REM call header.bat
