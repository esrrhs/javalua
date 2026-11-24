local n = require('lfs')
for i=1,200 do
  local next, state = n.dir('$DATA')
  assert(type(next(state)) == 'string')
  state:close()
  local ok, err = pcall(next, state)
  assert(not ok and err:find('closed directory'))
end
local next, state = n.dir('$DATA')
state:close()
local ok, err = pcall(next, state)
print(ok, err)
