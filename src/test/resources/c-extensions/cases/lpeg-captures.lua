local n = require('lpeg')
local digit = n.R('09')^1 / tonumber
local grammar = n.P{'sum', sum = n.Cf(digit * (n.P('+') * digit)^0, function(a,b) return a+b end) * -n.P(1)}
assert(grammar:match('12+30+4') == 46)
local t = n.Ct((n.C(n.R('az')^1) * (n.P(',') + -n.P(1)))^1):match('one,two,three')
assert(#t == 3 and t[2] == 'two')
assert(n.Cs((n.P('a') / 'X' + n.P(1))^0):match('banana') == 'bXnXnX')
print(grammar:match('1+2+3'), table.concat(t, ':'))
