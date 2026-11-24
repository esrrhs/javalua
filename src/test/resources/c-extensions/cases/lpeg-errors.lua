local n = require('lpeg')
local marker = {}
local p = n.C(n.P(1)) / function() error(marker, 0) end
for i=1,100 do
  local ok, err = pcall(n.match, p, 'x')
  assert(not ok and rawequal(err, marker))
  assert(n.P('ok'):match('ok') == 3)
end
local ok, err = pcall(n.P, {})
assert(not ok and type(err) == 'string')
print(ok, err)
