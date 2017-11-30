#! /bin/sh

cd jflexbison
jflex jflex.flex
mv Yylex.java ../src/com/github/esrrhs/fakescript/Yylex.java -f

bison -L JAVA YYParser.y
mv YYParser.java ../src/com/github/esrrhs/fakescript/YYParser.java -f


