local n = require('cjson')
local s = string.char(0,1,8,9,10,13,34,92,127,128,255)
assert(n.decode(n.encode(s)) == s)
local large = string.rep('ab"\n', 20000)
for i=1,10 do assert(n.decode(n.encode(large)) == large) end
local t = n.decode([["\u0041\u20ac\ud834\udd1e"]])
assert(t == 'A' .. utf8.char(0x20ac,0x1d11e))
print(#large, #n.encode(large), #t)
