local n = ...
local s = 0
for i = 1, n do
  local t = {i, i + 1, i + 2, tag = "node"}
  s = s + t[2]
end
return s
