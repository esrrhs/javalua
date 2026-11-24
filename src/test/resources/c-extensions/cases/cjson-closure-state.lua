local n = require('cjson')
local a, b = n.new(), n.new()
a.encode_number_precision(3); b.encode_number_precision(10)
assert(a.encode(1.23456789) == '1.23')
assert(b.encode(1.23456789) == '1.23456789')
a.encode_invalid_numbers(true)
b.encode_invalid_numbers(false)
assert(a.encode(0/0):lower() == 'nan')
assert(not pcall(b.encode, 0/0))
for i=1,200 do
  assert(a.decode(a.encode({1,2,3}))[3] == 3)
end
print(a.encode(1.23456789), b.encode(1.23456789), n.encode(1.23456789))
