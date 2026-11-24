local n = require('lpeg')
local calls = 0
local p = n.Cmt(n.C(n.R('az')^1), function(subject, pos, word)
  calls = calls + 1
  assert(subject == 'hello' and pos == 6 and word == 'hello')
  return pos, word:upper(), 42, false
end)
for i=1,300 do
  local word, value, flag = p:match('hello')
  assert(word == 'HELLO' and value == 42 and flag == false)
end
assert(calls == 300)
local many = n.Ct((n.C(n.P(1)) / function(x) return x, #x end)^0):match(string.rep('x', 300))
assert(#many == 600 and many[600] == 1)
print(calls, #many)
