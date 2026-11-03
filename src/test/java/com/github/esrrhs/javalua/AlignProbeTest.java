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
        // stripped dump bytes must match stock (print as decimal bytes)
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

}
