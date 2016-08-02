#! /bin/sh

cd jflexcup
jflex jflex.flex
mv jflex.java ../src/fakescript/jflex.java -f

java -jar ../lib/java-cup-11b.jar -interface -expect 52 -parser cup cup.cup
mv sym.java ../src/fakescript/sym.java -f
mv cup.java ../src/fakescript/cup.java -f


