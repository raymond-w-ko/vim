:java (Vim/msg (Vim/window "1"))
:java (Vim/msg (.next (Vim/window "1")))
:java (Vim/msg (.previous (Vim/window "1")))
:java (Vim/msg (.setAsCurrent (Vim/window "2")))
:java (Vim/msg (-> (.getBuffer (Vim/window "1")) .getFullName))
:java (Vim/msg (-> (.getBuffer (Vim/window "2")) .getFullName))
:java (Vim/msg (.getLinePos (Vim/window "1")))
:java (Vim/msg (.setLinePos (Vim/window "1") 1))
:java (Vim/msg (.setLinePos (Vim/window "1") 8))
:java (Vim/msg (.getColPos (Vim/window "1")))
:java (Vim/msg (.setColPos (Vim/window "1") 39))
:java (Vim/msg (.setColPos (Vim/window "1") 80))
:java (Vim/msg (.getWidth (Vim/window "1")))
:java (Vim/msg (.setWidth (Vim/window "1") 80))
:java (Vim/msg (.getHeight (Vim/window "1")))
:java (Vim/msg (.setHeight (Vim/window "1") 24))
:java (Vim/msg (.setHeight (Vim/window "1") 9999))
