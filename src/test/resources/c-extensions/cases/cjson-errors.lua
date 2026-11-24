local n = require('cjson')
for _,s in ipairs({'{', '[1,]', [["\uZZZZ"]]}) do
  local ok, err = pcall(n.decode, s)
  assert(not ok and type(err) == 'string')
  print(ok, err)
end
local circular = {}; circular[1] = circular
n.encode_max_depth(10)
local ok, err = pcall(n.encode, circular)
assert(not ok and type(err) == 'string')
print(ok, err)
for i=1,100 do
  assert(not pcall(n.encode, function() end))
  assert(n.encode({i}) == '[' .. i .. ']')
end
print('recovered', n.decode('[1,2]')[2])
