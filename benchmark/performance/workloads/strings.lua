local n = ...
local s = 0
for i = 1, n do
  local x = string.format("%08d", i)
  local y = string.reverse(x)
  s = s + string.byte(y, 1) + #string.sub(x, 3, 6)
end
return s
