:let g:test_dict = {'foo' : 'bar', 'baz' : 8, 'meow' : 3.14159}
:echo g:test_dict
:java (Vim/msg (def test_dict (Vim/eval "g:test_dict")))

:java (Vim/msg (.size test_dict))

:java (Vim/msg (.get test_dict "foo"))
:java (Vim/msg (.get test_dict "cheeseburger"))
:java (Vim/msg (.get test_dict "baz"))
:java (Vim/msg (.get test_dict "meow"))

:java (.put test_dict "zerg" "mutalisk")
:java (.put test_dict "meow" "kitty")
:java (.put test_dict "galaxy" 42)
echo g:test_dict

:java (.remove test_dict "foo")
:java (.remove test_dict "bar")
:java (.remove test_dict "baz")
:java (.remove test_dict "meow")
:java (.remove test_dict "zerg")
echo g:test_dict

:java (doall (map #(Vim/msg (clojure.string/join "," (list (.getKey %) (.getValue %)))) test_dict))
:java (map (fn [x] (Vim/msg (str x))) (range 30))
:java (doall (map (fn [x] (Vim/msg (str x))) (range 30)))
:java ((fn [] (Vim/msg "ichi") (Vim/msg "ni") (Vim/msg "san")))

:echo g:test_dict
