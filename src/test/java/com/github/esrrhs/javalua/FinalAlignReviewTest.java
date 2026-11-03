package com.github.esrrhs.javalua;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;
/** Final independent re-review vs Lua 5.3.4 after ipairs/undump/ERRMEM fixes. */
class FinalAlignReviewTest {
  @Test void stdlibMetamethods() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
-- __add both sides, __eq, __lt/__le fallback, __concat chain, __call, __len
local function go(tag, f) local r=table.pack(pcall(f)); print(tag, r[1], table.unpack(r,2,r.n)) end
local A = setmetatable({},{__add=function(a,b) return 10 end, __eq=function() return true end})
local B = setmetatable({},{__add=function(a,b) return 20 end})
go("ADD", function() return A+B, B+A end)
go("EQ", function() return A==B, A~=B end)
local X = setmetatable({},{__lt=function(a,b) return true end})
local Y = setmetatable({},{})
go("LT", function() return X<Y, X<=Y end)
local C = setmetatable({},{__concat=function(a,b) return "C("..tostring(a)..","..tostring(b)..")" end})
go("CAT", function() return C .. "x" .. C end)
local F = setmetatable({},{__call=function(t,a) return a*2 end, __len=function() return 7 end})
go("CALL", function() return F(21), #F end)
""");
  }
  @Test void codegenDumpAndParser() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
print(load("local a <close> = nil") == nil)
print(load("local a <const> = 1") == nil)
local function dumpbytes(src)
  local d = string.dump(assert(load(src)), true)
  local t={#d}; for i=1,#d do t[#t+1]=string.byte(d,i) end
  return table.concat(t,",")
end
print(dumpbytes("return true, false, nil"))
print(dumpbytes("local function f(a,...) return a,... end; return f"))
print(dumpbytes([[goto L; ::L:: return 1]]))
""");
  }
  @Test void tableAndUndump() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
local t={}; for i=1,100 do t[i]=i end; print(#t, t[100])
t[50]=nil; print(#t)
local d=string.dump(function() return {1,2,a=true} end, true)
print(assert(load(d))()[2])
print(select(2, load("\\27Lua")))
print(select(2, load(string.dump(function()end):sub(1,1)..string.rep("\\0",40))))
""");
  }
  @Test void cApiVisibleLua() {
    assumeTrue(ReferenceLua.find()!=null);
    Diff.assertMatches("""
-- surfaces that Native/C API bugs often leak into
print(rawequal(1,1.0), rawequal(1,2))
print(select("#", 1,nil,3), select(2, 1,nil,3))
print(type(pcall), table.pack(pcall(error, "x"))[1])
local co = coroutine.create(function() coroutine.yield(1,2); return 3 end)
print(coroutine.resume(co))
print(coroutine.resume(co))
print(coroutine.status(co))
""");
  }
}
