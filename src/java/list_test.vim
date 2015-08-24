:let g:test_list = [1, 'item', 3.14159]
:let g:test_list2 = ['foo', 'bar', 'baz']
:let g:test_list3 = [1 , 2, 3, 4, 5, 6, 7, 8, 9, 10]
:java (Vim/msg (Vim/eval "g:test_list"))
:java (Vim/msg (def test_list (Vim/eval "g:test_list")))
:java (Vim/msg (def test_list2 (Vim/eval "g:test_list2")))
:java (Vim/msg (def test_list3 (Vim/eval "g:test_list3")))

:java (Vim/msg (.size test_list))

:java (Vim/msg (.get test_list 0))
:java (Vim/msg (.get test_list 1))
:java (Vim/msg (.get test_list 2))
:java (.get test_list 3)

:java (.get test_list 100)

:java (.set test_list 1 "foobar")
:echo g:test_list
:java (.set test_list 0 31337)
:echo g:test_list
:java (.set test_list 0 31337.31337)
:echo g:test_list
:java (.set test_list 0 test_list2)
:echo g:test_list

:java (.remove test_list 1)
:echo g:test_list

:java (.add test_list "fizzbuzz")
:echo g:test_list

:java (.insert test_list "foobar" 0)
:echo g:test_list

:java (.insert test_list "meow" 4)
:java (.insert test_list "meow" 6)
:echo g:test_list

:java (def test_list nil)
:unlet g:test_list
:java (for [i (range 100)] (System/gc))

:echo g:test_list3
:java (map (fn [x] (Vim/msg (str x))) (filter even? (seq test_list3)))
:java (map (fn [x] (Vim/msg (str x))) (filter odd? (seq test_list3)))
:java (print (map str test_list3))
