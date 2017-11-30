#! /bin/sh

cd jflexbison
jflex jflex.flex
mv Yylex.java ../src/main/java/com/github/esrrhs/fakescript/Yylex.java -f

bison -L JAVA YYParser.y
mv YYParser.java ../src/main/java/com/github/esrrhs/fakescript/YYParser.java -f


