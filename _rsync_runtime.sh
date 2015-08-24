#!/bin/bash
DIR="$( cd "$( /bin/dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"
cd ..
for D in `/bin/find . -maxdepth 1 -type d`
do
    if [[ ( "$D" != "./src" ) && ( "$D" != "." ) ]]; then
        /bin/rm -rf "$D"
    fi
done
cd src/runtime
/bin/rsync -a . ../../
