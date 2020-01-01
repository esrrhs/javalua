# fakejava

[<img src="https://img.shields.io/github/license/esrrhs/fakejava">](https://github.com/esrrhs/fakejava)
[<img src="https://img.shields.io/github/languages/top/esrrhs/fakejava">](https://github.com/esrrhs/fakejava)
[<img src="https://img.shields.io/maven-central/v/com.github.esrrhs/fakescript-java">](https://github.com/esrrhs/fakejava)
[<img src="https://img.shields.io/github/workflow/status/esrrhs/fakejava/Java%20CI">](https://github.com/esrrhs/fakejava/actions)

Lightweight embedded scripting language

## Brief introduction
**fakejava** is a lightweight embedded scripting language , using Java language, grammar lessons from lua, golang, erlang, based on jflex, bison generative grammar tree , compiled into byte code interpreted.
<a href="https://github.com/esrrhs/fakescript">fakescript for C/C++</a>

## Script feature
* The syntax is similar to lua
* All function
* Support array, map, unlimited nested
* Support generating routine like fake testfunc (param1), to achieve the effect of multithreading
* Support binding Java Static functions and Java class member functions
* With interpreter
* Support multi return value
* Support profile, can get the script running time of each function
* Support hot update
* Support Int64
* Support const definitions
* Support Package
* Support struct
* Support byte code optimize
* Support global map



## Sample

```


-- Current package name
package mypackage.test

-- include file
include "common.fk"

-- struct define
struct teststruct
	sample_a
	sample_b
	sample_c
end

-- const define
const hellostring = "hello"
const helloint = 1234
const hellomap = {1 : "a" 2 : "b" 3 : [1 2 3]}

-- func1 comment
func myfunc1(arg1, arg2)

	-- Java static function calls and Java class member functions
	arg3 := cfunc1(helloint) + arg2:memfunc1(arg1)

	-- Branch
	if arg1 < arg2 then
		-- create routine
		fake myfunc2(arg1, arg2)
	elseif arg1 == arg2 then
		print("elseif")
	else
		print("else")
	end

	-- for loop
	for var i = 0, i < arg2, i++ then
		print("i = ", i)
	end

	-- array use
	var a = array()
	a[1] = 3

	-- map use
	var b = map()
	b[a] = 1
	b[1] = a

	-- Int64
	var uid = 1241515236123614u
	log("uid = ", uid)

	-- sub func call
	var ret1, var ret2 = myfunc2()

	-- other package call
	ret1 = otherpackage.test.myfunc1(arg1, arg2)

	-- struct use
	var tt = teststruct()
	tt->sample_a = 1
	tt->sample_b = teststruct()
	tt->sample_b->sample_a = 10

	-- switch branch
	switch arg1
		case 1 then
			print("1")
		case "a" then
			print("a")
		default
			print("default")
	end

	-- multi return value
	return arg1, arg3

end
```

## Java Sample

```
// create instance
fake f = fk.newfake(null);
// regist all func marked with @fakescript in package
fk.reg(f, "com.test");
// parse script file
fk.parse(f, "test.fk");
// run script func myfunc1, put in two param 1 and 2
double ret = (double)fk.run(f, "myfunc1", 1, 2);

```

## How to use
#### Maven
```
<dependency>
    <groupId>com.github.esrrhs</groupId>
    <artifactId>fakescript-java</artifactId>
    <version>1.0.12</version>
</dependency>

```

## Debugging environment
* IDE

![image](img/ide.png)

* Command-line

![image](img/debug.png)
