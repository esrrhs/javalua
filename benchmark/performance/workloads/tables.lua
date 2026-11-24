local n = ...
local t = {}
for i = 1, n do t[i] = i; t[-i] = i * 2 end
local s = 0
for i = 1, n do s = s + t[i] + t[-i] end
return s
