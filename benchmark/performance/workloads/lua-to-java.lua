local n = ...
local host = bench_host
local s = 0
for i = 1, n do s = s + host(i) end
return s
