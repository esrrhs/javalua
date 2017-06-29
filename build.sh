#! /bin/sh

cd jflexbison
jflex jflex.flex
mv Yylex.java ../src/fakescript/Yylex.java -f

bison -L JAVA YYParser.y
mv YYParser.java ../src/fakescript/YYParser.java -f


