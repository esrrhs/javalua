package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/** Independent alignment probes vs Lua 5.3.4 (non-GC). */
class AlignProbeTest {

    @Test
    void typeMtPairsIpairs() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local old = debug.getmetatable(0)
debug.setmetatable(0, {__pairs=function(x)
  return function(s,i)
    i=(i or 0)+1
    if i>2 then return end
    return i, x*i
  end, x, 0
end, __ipairs=function(x)
  return function(s,i)
    i=i+1
    if i>1 then return end
    return i, "ip"..tostring(x)
  end, x, 0
end})
local acc={}
for k,v in pairs(7) do acc[#acc+1]=k.."="..v end
print("P", table.concat(acc,","))
local acc2={}
for k,v in ipairs(7) do acc2[#acc2+1]=k.."="..v end
print("I", table.concat(acc2,","))
debug.setmetatable(0, old)
""");
    }

    @Test
    void gotoBreakUpvalueSemanticsAndDumpBytes() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local function mk()
  local x = 1
  local function f() return x end
  if false then goto L end
  x = x + 1
  ::L::
  return f
end
print(mk()())
""");
        Diff.assertMatches("""
local function dumpbytes(src)
  local f = assert(load(src, "=T"))
  local d = string.dump(f, true)
  local t = {#d}
  for i=1,#d do t[#t+1]=string.byte(d,i) end
  return table.concat(t, ",")
end
print(dumpbytes([[
local function outer()
  local a = 10
  local function inner() return a end
  for i=1,3 do
    if i==2 then break end
    a = a + i
  end
  return inner
end
return outer
]]))
print(dumpbytes([[
local function f(...)
  local a = select("#", ...)
  return a, ...
end
return f
]]))
print(dumpbytes([[
local function f(a,b)
  if a then goto L end
  b = b + 1
  ::L::
  return a,b
end
return f
]]))
print(dumpbytes([[
repeat
  local x = 1
  do
    local y = 2
    break
  end
until false
]]))
""");
    }

    @Test
    void tableLenMetamethodInLibs() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local t = setmetatable({10,20,30,40}, {__len=function() return 2 end})
print(#t)
print(pcall(function() table.insert(t, 99); return t[3], rawlen(t) end))
print(pcall(function() return table.concat(t, ",") end))
print(pcall(function() return table.unpack(t) end))
print(pcall(function() table.remove(t); return rawget(t,1), rawget(t,2), rawlen(t) end))
""");
    }

    @Test
    void stringFormatQAndPack() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
print(string.format("%q", "a\\nb"))
print(string.format("%q", "a\\0b"))
print(string.packsize("i8c4"))
print(string.pack("i8", -1):byte(1,8))
""");
    }

    @Test
    void debugInfoAndHookCount() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local function f(a,b) local c=a+b; return c end
local i = debug.getinfo(f, "nSu")
print(i.what, i.nparams, i.isvararg, i.nups)
local n=0
debug.sethook(function(ev) n=n+1 end, "", 10)
for i=1,100 do end
debug.sethook()
print(n > 0)
""");
    }

    @Test
    void coroutineWrapErrors() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local w = coroutine.wrap(function() error("boom") end)
print(pcall(w))
local w2 = coroutine.wrap(function(x) return x+1 end)
print(w2(3))
print(pcall(function() return w2(1) end))
""");
    }

    @Test
    void undumpHeaderWhyStrings() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local d = string.dump(function() return 1 end)
print(select(2, load(d:sub(1,1)..string.rep("\\0",40))))
local bad = d:sub(1,6) .. string.rep("X", #d-6)
print(select(2, load(bad)))
print(select(2, load(d:sub(1,20))))
""");
    }

    @Test
    void loadErrMemSurvivesMsghandler() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local g = load("return function() return 'hi' end", "=X")()
local base = string.dump(g)
local s = base:sub(1, 34) .. string.char(255) .. base:sub(36)
local f, e = load(s, "=C")
print(f and "ok" or ("err "..tostring(e)))
print("still alive")
""");
    }

    @Test
    void xpcallLoadReaderError() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local function H(err) return "H:"..tostring(err) end
print(xpcall(function()
  return load(function() error("boom") end)
end, H))
print(xpcall(function()
  return load("x = =")
end, H))
""");
    }

    @Test
    void bit32AndUtf8Edges() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
print(bit32.arshift(-1, 1))
print(bit32.extract(0xFFFFFFFF, 0, 32))
print(bit32.btest(1, 2), bit32.btest(3, 1))
print(#utf8.charpattern)
print(utf8.len("A\\x80B"))
print(utf8.offset("abc", 2), utf8.offset("abc", -1))
""");
    }

    @Test
    void packageSearchersAndRequire() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
print(type(package.searchers), #package.searchers)
package.preload.m = function(mod) return {name=mod}, "extra" end
local a,b = require("m")
print(a.name, tostring(b), package.loaded.m == a)
""");
    }

    @Test
    void numericForEdges() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local n=0
for i=0.5, 2.5, 0.5 do n=n+1 end
print(n)
local m=0
for i=1, 0, -0.5 do m=m+i end
print(m)
print(pcall(function() for i=1,10,0 do end end))
""");
    }

    @Test
    void ioLinesReturnArityAndUpvalues() {
        assumeTrue(ReferenceLua.find() != null);
        Diff.assertMatches("""
local f = io.tmpfile(); f:write("a\nb\n"); f:seek("set")
local x,y,z,w = f:lines()
print(type(x), type(y), type(z), type(w))
for line in f:lines() do print("L", line) end
f:close()
local function outer()
  local a,b = 1,2
  return function() return a+b end
end
local cl = outer()
print(debug.getupvalue(cl, 1))
print(debug.setupvalue(cl, 2, 9))
print(cl())
local d = string.dump(function() return 1 end)
print(pcall(function() assert(load(d, "=x", "t")) end))
print(assert(load(d, "=x", "b"))())
""");
    }
}
