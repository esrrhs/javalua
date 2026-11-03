// Translation of lbitlib.c (PUC-Lua 5.3.4 v1.30): bit32 library (LUA_COMPAT_BITLIB).
package core

// number of bits to consider in a number (LUA_NBITS; default 32, not overridable here)
const luaNBits = 32

/*
** a lua_Unsigned with its first LUA_NBITS bits equal to 1. (Shift must
** be made in two parts to avoid problems when LUA_NBITS is equal to the
** number of bits in a lua_Unsigned.)
** C: #define ALLONES (~(((~(lua_Unsigned)0) << (LUA_NBITS - 1)) << 1))
** Computed at init: Go rejects the same expression as a typed uint64 constant
** (intermediate << overflows the const checker).
 */
var allOnes = allOnesFromC()

func allOnesFromC() uint64 {
	ones := ^uint64(0)
	return ^((ones << (luaNBits - 1)) << 1)
}

// trim extra bits (C macro trim)
func trim(x uint64) uint64 { return x & allOnes }

// builds a number with 'n' ones (1 <= n <= LUA_NBITS); C macro mask(n)
func mask(n int) uint64 {
	return ^((allOnes << 1) << uint(n-1))
}

// C: #define checkunsigned(L,i) ((lua_Unsigned)luaL_checkinteger(L,i))
func checkUnsigned(L *State, arg int) uint64 {
	return uint64(L.CheckInteger(arg))
}

// C: #define pushunsigned(L,n) lua_pushinteger(L, (lua_Integer)(n))
func pushUnsigned(L *State, n uint64) {
	L.PushInteger(int64(n))
}

func andaux(L *State) uint64 {
	n := L.GetTop()
	r := ^uint64(0)
	for i := 1; i <= n; i++ {
		r &= checkUnsigned(L, i)
	}
	return trim(r)
}

func b_and(L *State) int {
	r := andaux(L)
	pushUnsigned(L, r)
	return 1
}

func b_test(L *State) int {
	r := andaux(L)
	L.PushBoolean(r != 0)
	return 1
}

func b_or(L *State) int {
	n := L.GetTop()
	var r uint64
	for i := 1; i <= n; i++ {
		r |= checkUnsigned(L, i)
	}
	pushUnsigned(L, trim(r))
	return 1
}

func b_xor(L *State) int {
	n := L.GetTop()
	var r uint64
	for i := 1; i <= n; i++ {
		r ^= checkUnsigned(L, i)
	}
	pushUnsigned(L, trim(r))
	return 1
}

func b_not(L *State) int {
	r := ^checkUnsigned(L, 1)
	pushUnsigned(L, trim(r))
	return 1
}

func b_shift(L *State, r uint64, i int64) int {
	if i < 0 { // shift right?
		i = -i
		r = trim(r)
		if i >= luaNBits {
			r = 0
		} else {
			r >>= uint(i)
		}
	} else { // shift left
		if i >= luaNBits {
			r = 0
		} else {
			r <<= uint(i)
		}
		r = trim(r)
	}
	pushUnsigned(L, r)
	return 1
}

func b_lshift(L *State) int {
	return b_shift(L, checkUnsigned(L, 1), L.CheckInteger(2))
}

func b_rshift(L *State) int {
	return b_shift(L, checkUnsigned(L, 1), -L.CheckInteger(2))
}

func b_arshift(L *State) int {
	r := checkUnsigned(L, 1)
	i := L.CheckInteger(2)
	if i < 0 || (r&(uint64(1)<<(luaNBits-1))) == 0 {
		return b_shift(L, r, -i)
	}
	// arithmetic shift for 'negative' number
	if i >= luaNBits {
		r = allOnes
	} else {
		// C: trim((r >> i) | ~(trim(~(lua_Unsigned)0) >> i))
		r = trim((r >> uint(i)) | ^(trim(^uint64(0)) >> uint(i)))
	}
	pushUnsigned(L, r)
	return 1
}

func b_rot(L *State, d int64) int {
	r := checkUnsigned(L, 1)
	i := int(d & int64(luaNBits-1)) // i = d % NBITS  (C: int i = d & (LUA_NBITS - 1))
	r = trim(r)
	if i != 0 { // avoid undefined shift of LUA_NBITS when i == 0
		r = (r << uint(i)) | (r >> uint(luaNBits-i))
	}
	pushUnsigned(L, trim(r))
	return 1
}

func b_lrot(L *State) int {
	return b_rot(L, L.CheckInteger(2))
}

func b_rrot(L *State) int {
	return b_rot(L, -L.CheckInteger(2))
}

/*
** get field and width arguments for field-manipulation functions,
** checking whether they are valid.
** ('luaL_error' / Error2 called without return to avoid later warnings about
** 'width' being used uninitialized.)
 */
func fieldArgs(L *State, farg int, width *int) int {
	f := L.CheckInteger(farg)
	w := L.OptInteger(farg+1, 1)
	L.ArgCheck(0 <= f, farg, "field cannot be negative")
	L.ArgCheck(0 < w, farg+1, "width must be positive")
	if f+w > luaNBits {
		L.Error2("trying to access non-existent bits")
	}
	*width = int(w)
	return int(f)
}

func b_extract(L *State) int {
	var w int
	r := trim(checkUnsigned(L, 1))
	f := fieldArgs(L, 2, &w)
	r = (r >> uint(f)) & mask(w)
	pushUnsigned(L, r)
	return 1
}

func b_replace(L *State) int {
	var w int
	r := trim(checkUnsigned(L, 1))
	v := trim(checkUnsigned(L, 2))
	f := fieldArgs(L, 3, &w)
	m := mask(w)
	r = (r & ^(m << uint(f))) | ((v & m) << uint(f))
	pushUnsigned(L, r)
	return 1
}

var bitlib = []Reg{
	{Name: "arshift", Func: b_arshift},
	{Name: "band", Func: b_and},
	{Name: "bnot", Func: b_not},
	{Name: "bor", Func: b_or},
	{Name: "bxor", Func: b_xor},
	{Name: "btest", Func: b_test},
	{Name: "extract", Func: b_extract},
	{Name: "lrotate", Func: b_lrot},
	{Name: "lshift", Func: b_lshift},
	{Name: "replace", Func: b_replace},
	{Name: "rrotate", Func: b_rrot},
	{Name: "rshift", Func: b_rshift},
}

// OpenBit32 opens the bit32 library (luaopen_bit32 under LUA_COMPAT_BITLIB).
// Go always builds the compat body (no deprecated stub).
func OpenBit32(L *State) int {
	L.NewLib(bitlib)
	return 1
}
