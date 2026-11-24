local n = require('lfs')
local dir = '$DATA/iterator'
os.remove(dir .. '/a'); os.remove(dir .. '/b'); n.rmdir(dir)
assert(n.mkdir(dir))
for _, name in ipairs({'b','a'}) do local f=assert(io.open(dir..'/'..name,'w')); f:close() end
local next, state = n.dir(dir)
local names = {}
while true do local s = next(state); if not s then break end; names[#names+1]=s end
table.sort(names)
assert(table.concat(names, ',') == '.,..,a,b')
print(table.concat(names, ','))
assert(os.remove(dir .. '/a')); assert(os.remove(dir .. '/b')); assert(n.rmdir(dir))
