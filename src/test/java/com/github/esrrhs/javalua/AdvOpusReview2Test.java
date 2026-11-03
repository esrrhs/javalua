package com.github.esrrhs.javalua;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;
class AdvOpusReview2Test {
  @Test void bitUtf8StringOsTable() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local function go(tag, f)
  local r=table.pack(pcall(f)); print(tag, r[1], table.unpack(r,2,r.n))
end
-- bit32 library (5.3)
go("B1", function() return bit32.band(7, 5), bit32.bor(1,2), bit32.bxor(7,3), bit32.bnot(0) end)
go("B2", function() return bit32.lshift(1, 31), bit32.rshift(-1, 1), bit32.arshift(-1, 1) end)
go("B3", function() return bit32.extract(0xFF, 2, 4), bit32.replace(0, 0xF, 4, 4) end)
go("B4", function() return bit32.band(3.0, 1.0) end)
go("B5", function() return bit32.band(3.5, 1) end)
go("B6", function() return bit32.lshift(1, 32), bit32.lshift(1, 33) end)
go("B7", function() return bit32.btest(4, 5), bit32.btest(4, 2) end)

go("U1", function() return utf8.len("abc"), utf8.len("a\\x80b") end)
go("U2", function() return utf8.offset("hällo", 2), utf8.offset("abc", -1) end)
go("U3", function() return utf8.char(0x20AC, 0x1F600) end)
go("U4", function()
  local t={}
  for p,c in utf8.codes("a€b") do t[#t+1]=p; t[#t+1]=c end
  return table.concat(t,",")
end)
go("U5", function() return utf8.codepoint("a€", 2) end)
go("U6", function() return utf8.len("abc", 4) end)

go("P1", function() return string.find("abc", "%f[%a]b") end)
go("P2", function() return string.match("12ab34", "%d+(%a+)%d+") end)
go("P3", function() return string.gsub("a b c", "%s+", "-") end)
go("P4", function() return pcall(string.find, "a", "%") end)

go("T1", function() return table.concat({1,2,3}, ",", 2, 3) end)
go("T2", function() return table.pack(nil, 2).n, table.unpack({1,nil,3},1,3) end)
go("T3", function()
  local t={3,1,2}; table.sort(t); return table.concat(t,",")
end)
go("T4", function() return pcall(table.sort, {1,2}, "nope") end)

go("O1", function() return os.clock() >= 0 end)
go("O2", function() return type(os.time()), type(os.difftime(os.time(), os.time())) end)
go("O3", function() return os.date("%Y", 0):sub(1,2) end) -- year prefix only
go("O4", function() return pcall(os.date, "%!") end)
go("O5", function() return os.setlocale() ~= nil end) -- may differ locale name - skip exact
""");
  }

  @Test void codegenHeavyDump() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local function sig(src)
  local d = string.dump(assert(load(src)), true)
  -- hash-like: length + first 40 + last 20 bytes
  local t={#d}
  for i=1,math.min(40,#d) do t[#t+1]=string.byte(d,i) end
  for i=math.max(1,#d-19),#d do t[#t+1]=string.byte(d,i) end
  return table.concat(t,",")
end
print(sig("return true"))
print(sig("local a,b=1,2; return a+b"))
print(sig("local function f(x) if x then return x else return 0 end end; return f"))
print(sig("local s=0; for i=1,10 do s=s+i end; return s"))
print(sig("local s=0; for k,v in pairs({a=1,b=2}) do s=s+v end; return s"))
print(sig("local function f(...) return select('#',...),... end; return f"))
print(sig([[
local function f()
  local a = 1
  ::L::
  a = a + 1
  if a < 3 then goto L end
  return a
end
return f
]]))
print(sig("local t={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50}; return #t"))
print(sig("return function(a,b) return a & b | c ~ d << 1 >> 2 end"))
print(sig("local a <const> = 1")) -- should fail load; guard
""");
  }

  @Test void undumpAndLoadErrors() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local function go(tag, f)
  local r=table.pack(pcall(f)); print(tag, r[1], table.unpack(r,2,r.n))
end
go("U1", function() return load("\\27Lua") end)
go("U2", function()
  local d=string.dump(function() end)
  return load(d:sub(1,6))
end)
go("U3", function()
  local d=string.dump(function() end)
  return load(d:sub(1,#d-1))
end)
go("U4", function()
  local d=string.dump(function() return 1 end, true)
  -- corrupt opcode area carefully: flip last byte
  d = d:sub(1,#d-1) .. string.char((string.byte(d,#d)+1)%256)
  return load(d)
end)
go("L1", function() return load("return end") end)
go("L2", function() return load(nil) end)
go("L3", function() return load({}) end)
go("L4", function()
  local n=0
  return load(function()
    n=n+1
    if n==1 then return "return "
    elseif n==2 then return "42"
    end
  end)()
end)
""");
  }

  @Test void metamethodYieldVmAndC() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local rawts=tostring
local function dump(tag, ...)
  local n=select("#",...)
  io.write(tag)
  for i=1,n do
    local v=select(i,...)
    local s = (type(v)=="string" and v) or ((type(v)=="number" or type(v)=="boolean" or type(v)=="nil") and rawts(v)) or type(v)
    io.write("\\t", s)
  end
  io.write("\\n")
end
-- VM metamethod yield OK
do
  local co=coroutine.create(function()
    local t=setmetatable({},{__index=function(_,k) return coroutine.yield("i",k) end})
    return t.x
  end)
  dump("I1", coroutine.resume(co))
  dump("I2", coroutine.resume(co, 7))
end
do
  local co=coroutine.create(function()
    local t=setmetatable({},{__newindex=function(_,k,v) coroutine.yield("n",k,v) end})
    t.x = 5
    return "ok"
  end)
  dump("N1", coroutine.resume(co))
  dump("N2", coroutine.resume(co))
end
do
  local co=coroutine.create(function()
    local t=setmetatable({},{__len=function() return coroutine.yield("L") end})
    return #t
  end)
  dump("L1", coroutine.resume(co))
  dump("L2", coroutine.resume(co, 3))
end
do
  local co=coroutine.create(function()
    local t=setmetatable({},{__call=function(_,a) return coroutine.yield("c",a) end})
    return t(9)
  end)
  dump("C1", coroutine.resume(co))
  dump("C2", coroutine.resume(co, 8))
end
-- C-call noyield: tostring via print already covered; tostring() itself
do
  local co=coroutine.create(function()
    return tostring(setmetatable({},{__tostring=function() return coroutine.yield("t") end}))
  end)
  dump("TS", coroutine.resume(co))
  dump("TSst", coroutine.status(co))
end
""");
  }

  @Test void ioLinesAndFile() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local function go(tag, f)
  local r=table.pack(pcall(f)); print(tag, r[1], table.unpack(r,2,r.n))
end
go("T", function() return io.type(io.stdin), io.type(io.stdout), io.type({}) end)
go("R", function()
  -- write temp via io.tmpfile
  local f = io.tmpfile()
  f:write("a\\nb\\n")
  f:seek("set")
  local a=f:read("*l"); local b=f:read("*l"); local c=f:read("*l")
  return a,b,c, io.type(f)
end)
go("L", function()
  local f = io.tmpfile()
  f:write("1\\n2\\n3\\n")
  f:seek("set")
  local t={}
  for line in f:lines() do t[#t+1]=line end
  return table.concat(t,",")
end)
""");
  }
}
