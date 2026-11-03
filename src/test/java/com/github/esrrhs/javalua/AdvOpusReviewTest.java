package com.github.esrrhs.javalua;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;
class AdvOpusReviewTest {
  @Test void tracebackBottomCFrame() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertFullMatches("""
local function a(n)
  if n==0 then print(debug.traceback("msg", 0)) else return a(n-1) end
end
a(25)
""");
  }

  @Test void moreNoyieldAndEdges() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local rawts = tostring
local function dump(tag, ...)
  local n = select("#", ...)
  io.write(tag)
  for i = 1, n do
    local v = select(i, ...)
    local s
    if type(v) == "string" then s = v
    elseif type(v) == "boolean" or type(v) == "number" or type(v) == "nil" then s = rawts(v)
    else s = type(v) end
    io.write("\\t", s)
  end
  io.write("\\n")
end
local function go(tag, f)
  local r = table.pack(pcall(f))
  dump(tag, table.unpack(r, 1, r.n))
end

-- table.sort / gsub / sethook noyield (already known)
do
  local co = coroutine.create(function()
    table.sort({3,1,2}, function(a,b) return coroutine.yield(a,b) end)
  end)
  dump("TS", coroutine.resume(co))
end
do
  local co = coroutine.create(function()
    return string.gsub("ab", ".", function(c) return coroutine.yield(c) end)
  end)
  dump("SG", coroutine.resume(co))
end

-- load mode / BOM / shebang
go("BOM", function() return assert(load("\\xEF\\xBB\\xBFreturn 7"))() end)
go("SH", function() return assert(load("#!/bin/lua\\nreturn 8"))() end)
go("LM", function() return load("return 1", nil, "b") end)

-- len metamethod non-int
go("LN", function()
  local t=setmetatable({},{__len=function() return 3.5 end})
  return #t
end)

-- rawequal -0
go("RE", function() return rawequal(0.0,-0.0), 0.0==-0.0, rawequal(1,1.0) end)

-- pairs/ipairs on non-table
go("PR", function() local a,b,c = pairs(3); return type(a), b, c end)
go("IP", function() local a,b,c = ipairs("x"); return type(a), b, c end)

-- require missing shape
go("RQ", function() return require("definitely_missing_mod_zz") end)

-- setmetatable __metatable
go("MT", function()
  local t=setmetatable({},{__metatable="locked"})
  local ok, err = pcall(setmetatable, t, {})
  return getmetatable(t), ok, err
end)

-- bitwise boundary
go("BI", function() return (2.0^53) & 1 end)
go("BIc", function() return ((2^53)+2) & 2 end)

-- arith metamethod yield allowed
do
  local co = coroutine.create(function()
    local a = setmetatable({}, {__add=function() return coroutine.yield("add") end})
    return a + 1
  end)
  dump("AD", coroutine.resume(co))
  dump("ADst", coroutine.status(co))
  if coroutine.status(co)=="suspended" then dump("ADb", coroutine.resume(co, 99)) end
end

-- goto compile errors
go("GT1", function() return load("goto L; local a = 1; ::L::") end)

-- vararg pack
go("VA", function()
  local function f(...) return table.pack(...) end
  local t=f(nil,nil)
  return t.n, t[1], t[2]
end)

-- concat via tostring metamethod must be string
go("CAT", function()
  local t=setmetatable({},{__tostring=function() return 42 end})
  return "x"..t
end)

-- io.open missing
go("IO", function() return io.open("/no/such/file/javalua_x") end)
go("LF", function() return loadfile("/no/such/file/javalua_y") end)
""");
  }

  @Test void dumpRoundtripSelected() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local function dumpbytes(src, strip)
  local d = string.dump(assert(load(src)), strip)
  local t={#d}; for i=1,math.min(#d,80) do t[#t+1]=string.byte(d,i) end
  return table.concat(t,",")
end
print(dumpbytes("return 1,2,3", true))
print(dumpbytes("local a=1; return function(x) return x+a end", true))
print(dumpbytes("for i=1,3 do end; return 0", true))
print(dumpbytes("local t={1,2,3,a=4}; return t", true))
print(select(2, load(string.dump(function()end):sub(1,20))))
""");
  }

  @Test void tableLengthAndNext() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local function go(tag, f)
  local r=table.pack(pcall(f)); print(tag, r[1], table.unpack(r,2,r.n))
end
go("L1", function()
  local t={}; for i=1,10 do t[i]=i end; t[5]=nil; return #t
end)
go("L2", function()
  local t={[1]=1,[2]=2,[4]=4}; return #t
end)
go("L3", function()
  local t={}; t[1]=1; t[2]=2; t[3]=3; t[2]=nil; return #t
end)
go("NX", function()
  local t={a=1,b=2}; local k=next(t); local k2=next(t,k); local k3=next(t,k2); return type(k),type(k2),k3
end)
go("MV", function()
  local a={1,2,3,4,5}; table.move(a,2,4,1); return table.concat(a,",")
end)
""");
  }
}
