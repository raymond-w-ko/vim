PUSHD %~dp0
REM C:\cygwin\bin\bash -c "/usr/bin/hg purge --all"
CD src

SET SDK_INCLUDE_DIR=C:\Program Files\Microsoft SDKs\Windows\v7.1\Include
SET VC_DIR="C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC

if "%VCINSTALLDIR%"=="" (CALL %VC_DIR%\vcvarsall.bat" amd64)

REM JAVA=C:\opt\Java DYNAMIC_JAVA=yes JAVA_VER=17 ^

SET VIM_CONFIG_OPTIONS=^
FEATURES=HUGE GUI=yes DIRECTX=yes OLE=yes MBYTE=yes ^
IME=yes DYNAMIC_IME=yes GIME=yes ^
PYTHON=C:\opt\Python27 DYNAMIC_PYTHON=yes PYTHON_VER=27 ^
SNIFF=yes CSCOPE=yes ICONV=yes GETTEXT=yes POSTSCRIPT=yes ^
NETBEANS=yes XPM=no ^
CPU=AMD64 WINVER=0x0500 ^
DEBUG=yes

nmake -f Make_mvc.mak %VIM_CONFIG_OPTIONS% clean
nmake -f Make_mvc.mak %VIM_CONFIG_OPTIONS%

C:\Windows\System32\xcopy.exe /y /f gvimd.exe ..\..

cd ..

POPD
