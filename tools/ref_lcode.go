// Translation of lcode.h / lcode.c (PUC-Lua 5.3.4).
// Re-review align WIP: see NOTES.md.
package core

import (
	"math"
	"unsafe"
)

const MAXREGS = 255
const NO_JUMP = -1

type BinOpr int

const (
	OPR_ADD BinOpr = iota
	OPR_SUB
	OPR_MUL
	OPR_MOD
	OPR_POW
	OPR_DIV
	OPR_IDIV
	OPR_BAND
	OPR_BOR
	OPR_BXOR
	OPR_SHL
	OPR_SHR
	OPR_CONCAT
	OPR_EQ
	OPR_LT
	OPR_LE
	OPR_NE
	OPR_GT
	OPR_GE
	OPR_AND
	OPR_OR
	OPR_NOBINOPR
)

type UnOpr int

const (
	OPR_MINUS UnOpr = iota
	OPR_BNOT
	OPR_NOT
	OPR_LEN
	OPR_NOUNOPR
)

func hasjumps(e *expdesc) bool { return e.t != e.f }

func getinstruction(fs *FuncState, e *expdesc) *Instruction {
	return &fs.f.code[e.info]
}

func tonumeral(e *expdesc, v *Value) bool {
	if hasjumps(e) {
		return false
	}
	switch e.k {
	case VKINT:
		if v != nil {
			setivalue(v, e.ival)
		}
		return true
	case VKFLT:
		if v != nil {
			setfltvalue(v, e.nval)
		}
		return true
	default:
		return false
	}
}

func luaK_nil(fs *FuncState, from, n int) {
	l := from + n - 1
	if fs.pc > fs.lasttarget {
		previous := &fs.f.code[fs.pc-1]
		if previous.Op == OP_LOADNIL {
			pfrom := previous.A
			pl := pfrom + previous.B
			if (pfrom <= from && from <= pl+1) || (from <= pfrom && pfrom <= l+1) {
				if pfrom < from {
					from = pfrom
				}
				if pl > l {
					l = pl
				}
				previous.A = from
				previous.B = l - from
				return
			}
		}
	}
	luaK_codeABC(fs, OP_LOADNIL, from, n-1, 0)
}

func getjump(fs *FuncState, pc int) int {
	offset := sBx(fs.f.code[pc].B)
	if offset == NO_JUMP { /* point to itself represents end of list */
		return NO_JUMP
	}
	return (pc + 1) + offset /* turn offset into absolute position */
}

func fixjump(fs *FuncState, pc, dest int) {
	jmp := &fs.f.code[pc]
	offset := dest - (pc + 1)
	if absInt(offset) > MAXARG_sBx {
		luaX_syntaxerror(fs.ls, "control structure too long")
	}
	jmp.B = offset + OFFSET_sBx
}

func absInt(x int) int {
	if x < 0 {
		return -x
	}
	return x
}

func luaK_concat(fs *FuncState, l1 *int, l2 int) {
	if l2 == NO_JUMP {
		return
	} else if *l1 == NO_JUMP {
		*l1 = l2
	} else {
		list := *l1
		for {
			next := getjump(fs, list)
			if next == NO_JUMP {
				break
			}
			list = next
		}
		fixjump(fs, list, l2)
	}
}

func luaK_jump(fs *FuncState) int {
	jpc := fs.jpc
	fs.jpc = NO_JUMP
	j := codeAsBx(fs, OP_JMP, 0, NO_JUMP)
	luaK_concat(fs, &j, jpc)
	return j
}

func luaK_ret(fs *FuncState, first, nret int) {
	luaK_codeABC(fs, OP_RETURN, first, nret+1, 0)
}

func condjump(fs *FuncState, op, A, B, C int) int {
	luaK_codeABC(fs, op, A, B, C)
	return luaK_jump(fs)
}

func luaK_getlabel(fs *FuncState) int {
	fs.lasttarget = fs.pc
	return fs.pc
}

func getjumpcontrol(fs *FuncState, pc int) *Instruction {
	pi := &fs.f.code[pc]
	if pc >= 1 && testTMode(fs.f.code[pc-1].Op) {
		return &fs.f.code[pc-1]
	}
	return pi
}

func patchtestreg(fs *FuncState, node, reg int) bool {
	i := getjumpcontrol(fs, node)
	if i.Op != OP_TESTSET {
		return false
	}
	if reg != NO_REG && reg != i.B {
		i.A = reg
	} else {
		*i = newABC(OP_TEST, i.B, 0, i.C)
	}
	return true
}

func removevalues(fs *FuncState, list int) {
	for list != NO_JUMP {
		patchtestreg(fs, list, NO_REG)
		list = getjump(fs, list)
	}
}

func patchlistaux(fs *FuncState, list, vtarget, reg, dtarget int) {
	for list != NO_JUMP {
		next := getjump(fs, list)
		if patchtestreg(fs, list, reg) {
			fixjump(fs, list, vtarget)
		} else {
			fixjump(fs, list, dtarget)
		}
		list = next
	}
}

func dischargejpc(fs *FuncState) {
	patchlistaux(fs, fs.jpc, fs.pc, NO_REG, fs.pc)
	fs.jpc = NO_JUMP
}

func luaK_patchtohere(fs *FuncState, list int) {
	luaK_getlabel(fs)
	luaK_concat(fs, &fs.jpc, list)
}

func luaK_patchlist(fs *FuncState, list, target int) {
	if target == fs.pc {
		luaK_patchtohere(fs, list)
	} else {
		patchlistaux(fs, list, target, NO_REG, target)
	}
}

func luaK_patchclose(fs *FuncState, list, level int) {
	level++ /* argument is +1 to reserve 0 as non-op */
	for list != NO_JUMP {
		next := getjump(fs, list)
		inst := &fs.f.code[list]
		// assert JMP with A==0 or A>=level
		inst.A = level
		list = next
	}
}

func luaK_jumpto(fs *FuncState, t int) {
	luaK_patchlist(fs, luaK_jump(fs), t)
}

func luaK_code(fs *FuncState, i Instruction) int {
	dischargejpc(fs) /* 'pc' will change */
	f := fs.f
	if len(f.code) <= fs.pc {
		f.code = append(f.code, Instruction{})
	}
	f.code[fs.pc] = i
	if len(f.lineinfo) <= fs.pc {
		f.lineinfo = append(f.lineinfo, 0)
	}
	f.lineinfo[fs.pc] = int32(fs.ls.lastline)
	fs.pc++
	f.sizecode = fs.pc
	f.sizelineinfo = fs.pc
	return fs.pc - 1
}

func luaK_codeABC(fs *FuncState, o, a, b, c int) int {
	return luaK_code(fs, newABC(o, a, b, c))
}

func luaK_codeABx(fs *FuncState, o, a, bc int) int {
	return luaK_code(fs, newABx(o, a, bc))
}

func codeAsBx(fs *FuncState, o, a, bc int) int {
	return luaK_codeABx(fs, o, a, bc+OFFSET_sBx)
}

func codeextraarg(fs *FuncState, a int) int {
	return luaK_code(fs, newAx(OP_EXTRAARG, a))
}

func luaK_codek(fs *FuncState, reg, k int) int {
	if k <= MAXARG_Bx {
		return luaK_codeABx(fs, OP_LOADK, reg, k)
	}
	p := luaK_codeABx(fs, OP_LOADKX, reg, 0)
	codeextraarg(fs, k)
	return p
}

func luaK_checkstack(fs *FuncState, n int) {
	newstack := fs.freereg + n
	if newstack > int(fs.f.maxstacksize) {
		if newstack >= MAXREGS {
			luaX_syntaxerror(fs.ls, "function or expression needs too many registers")
		}
		fs.f.maxstacksize = byte(newstack)
	}
}

func luaK_reserveregs(fs *FuncState, n int) {
	luaK_checkstack(fs, n)
	fs.freereg += n
}

func freereg(fs *FuncState, reg int) {
	if !ISK(reg) && reg >= fs.nactvar {
		fs.freereg--
		// assert reg == freereg
	}
}

func freeexp(fs *FuncState, e *expdesc) {
	if e.k == VNONRELOC {
		freereg(fs, e.info)
	}
}

/* freeexps frees registers of two expressions in descending order (lcode.c). */
func freeexps(fs *FuncState, e1, e2 *expdesc) {
	r1, r2 := -1, -1
	if e1.k == VNONRELOC {
		r1 = e1.info
	}
	if e2.k == VNONRELOC {
		r2 = e2.info
	}
	if r1 > r2 {
		freereg(fs, r1)
		freereg(fs, r2)
	} else {
		freereg(fs, r2)
		freereg(fs, r1)
	}
}

func addk(fs *FuncState, key, v *Value) int {
	L := fs.ls.L
	f := fs.f
	idx := luaH_set(L, fs.ls.h, key) /* index scanner table */
	if ttisinteger(idx) {            /* is there an index there? */
		k := int(ivalue(idx))
		/* correct value? (warning: must distinguish floats from integers!) */
		if k < fs.nk && ttypetag(&f.k[k]) == ttypetag(v) && luaV_rawequalobj(&f.k[k], v) {
			return k /* reuse index */
		}
	}
	/* constant not found; create a new entry */
	k := fs.nk
	/* numerical value does not need GC barrier;
	   table has no metatable, so it does not need to invalidate cache */
	setivalue(idx, int64(k))
	if len(f.k) <= k {
		f.k = append(f.k, Value{})
	}
	f.k[k] = *v
	fs.nk++
	f.sizek = fs.nk
	/* no luaC_barrier — Go GC (intentional) */
	return k
}

func luaK_stringK(fs *FuncState, s *TString) int {
	var o Value
	setsvalue(&o, s)
	return addk(fs, &o, &o) /* use string itself as key */
}

func luaK_intK(fs *FuncState, n int64) int {
	/* Integers use lightuserdata as keys to avoid collision with floats
	   of the same numeric value (lcode.c luaK_intK, Lua 5.3.4:
	   setpvalue(&k, cast(void*, cast(size_t, n)))). */
	var k, o Value
	setpvalue(&k, *(*unsafe.Pointer)(unsafe.Pointer(&n)))
	setivalue(&o, n)
	return addk(fs, &k, &o)
}

func luaK_numberK(fs *FuncState, r float64) int {
	var o Value
	setfltvalue(&o, r)
	return addk(fs, &o, &o)
}

func boolK(fs *FuncState, b bool) int {
	var o Value
	setbvalue(&o, b)
	return addk(fs, &o, &o) /* use boolean itself as key */
}

func nilK(fs *FuncState) int {
	var k, v Value
	setnilvalue(&v)
	/* cannot use nil as key; instead use table itself to represent nil */
	sethvalue(&k, fs.ls.h)
	return addk(fs, &k, &v)
}

func luaK_setreturns(fs *FuncState, e *expdesc, nresults int) {
	if e.k == VCALL {
		getinstruction(fs, e).C = nresults + 1
	} else if e.k == VVARARG {
		getinstruction(fs, e).B = nresults + 1
		getinstruction(fs, e).A = fs.freereg
		luaK_reserveregs(fs, 1)
	}
}

func luaK_setmultret(fs *FuncState, e *expdesc) {
	luaK_setreturns(fs, e, LUA_MULTRET)
}

func luaK_setoneret(fs *FuncState, e *expdesc) {
	if e.k == VCALL {
		e.k = VNONRELOC
		e.info = getinstruction(fs, e).A
	} else if e.k == VVARARG {
		getinstruction(fs, e).B = 2
		e.k = VRELOCABLE
	}
}

func luaK_dischargevars(fs *FuncState, e *expdesc) {
	switch e.k {
	case VLOCAL:
		e.k = VNONRELOC
	case VUPVAL:
		e.info = luaK_codeABC(fs, OP_GETUPVAL, 0, e.info, 0)
		e.k = VRELOCABLE
	case VINDEXED:
		var op int
		freereg(fs, e.indIdx)
		if e.indVt == VLOCAL {
			freereg(fs, e.indT)
			op = OP_GETTABLE
		} else {
			op = OP_GETTABUP
		}
		e.info = luaK_codeABC(fs, op, 0, e.indT, e.indIdx)
		e.k = VRELOCABLE
	case VVARARG, VCALL:
		luaK_setoneret(fs, e)
	}
}

func discharge2reg(fs *FuncState, e *expdesc, reg int) {
	luaK_dischargevars(fs, e)
	switch e.k {
	case VNIL:
		luaK_nil(fs, reg, 1)
	case VFALSE, VTRUE:
		luaK_codeABC(fs, OP_LOADBOOL, reg, bool2int(e.k == VTRUE), 0)
	case VK:
		luaK_codek(fs, reg, e.info)
	case VKFLT:
		luaK_codek(fs, reg, luaK_numberK(fs, e.nval))
	case VKINT:
		luaK_codek(fs, reg, luaK_intK(fs, e.ival))
	case VRELOCABLE:
		getinstruction(fs, e).A = reg
	case VNONRELOC:
		if reg != e.info {
			luaK_codeABC(fs, OP_MOVE, reg, e.info, 0)
		}
	default:
		return
	}
	e.info = reg
	e.k = VNONRELOC
}

func bool2int(b bool) int {
	if b {
		return 1
	}
	return 0
}

func discharge2anyreg(fs *FuncState, e *expdesc) {
	if e.k != VNONRELOC {
		luaK_reserveregs(fs, 1)
		discharge2reg(fs, e, fs.freereg-1)
	}
}

func code_loadbool(fs *FuncState, A, b, jump int) int {
	luaK_getlabel(fs)
	return luaK_codeABC(fs, OP_LOADBOOL, A, b, jump)
}

func need_value(fs *FuncState, list int) bool {
	for ; list != NO_JUMP; list = getjump(fs, list) {
		i := getjumpcontrol(fs, list)
		if i.Op != OP_TESTSET {
			return true
		}
	}
	return false
}

func exp2reg(fs *FuncState, e *expdesc, reg int) {
	discharge2reg(fs, e, reg)
	if e.k == VJMP {
		luaK_concat(fs, &e.t, e.info)
	}
	if hasjumps(e) {
		final := NO_JUMP
		p_f := NO_JUMP
		p_t := NO_JUMP
		if need_value(fs, e.t) || need_value(fs, e.f) {
			var fj int
			if e.k == VJMP {
				fj = NO_JUMP
			} else {
				fj = luaK_jump(fs)
			}
			p_f = code_loadbool(fs, reg, 0, 1)
			p_t = code_loadbool(fs, reg, 1, 0)
			luaK_patchtohere(fs, fj)
		}
		final = luaK_getlabel(fs)
		patchlistaux(fs, e.f, final, reg, p_f)
		patchlistaux(fs, e.t, final, reg, p_t)
	}
	e.f = NO_JUMP
	e.t = NO_JUMP
	e.info = reg
	e.k = VNONRELOC
}

func luaK_exp2nextreg(fs *FuncState, e *expdesc) {
	luaK_dischargevars(fs, e)
	freeexp(fs, e)
	luaK_reserveregs(fs, 1)
	exp2reg(fs, e, fs.freereg-1)
}

func luaK_exp2anyreg(fs *FuncState, e *expdesc) int {
	luaK_dischargevars(fs, e)
	if e.k == VNONRELOC {
		if !hasjumps(e) {
			return e.info
		}
		if e.info >= fs.nactvar {
			exp2reg(fs, e, e.info)
			return e.info
		}
	}
	luaK_exp2nextreg(fs, e)
	return e.info
}

func luaK_exp2anyregup(fs *FuncState, e *expdesc) {
	if e.k != VUPVAL || hasjumps(e) {
		luaK_exp2anyreg(fs, e)
	}
}

func luaK_exp2val(fs *FuncState, e *expdesc) {
	if hasjumps(e) {
		luaK_exp2anyreg(fs, e)
	} else {
		luaK_dischargevars(fs, e)
	}
}

func luaK_exp2RK(fs *FuncState, e *expdesc) int {
	luaK_exp2val(fs, e)
	switch e.k { /* move constants to 'k' */
	case VTRUE:
		e.info = boolK(fs, true)
		goto vk
	case VFALSE:
		e.info = boolK(fs, false)
		goto vk
	case VNIL:
		e.info = nilK(fs)
		goto vk
	case VKINT:
		e.info = luaK_intK(fs, e.ival)
		goto vk
	case VKFLT:
		e.info = luaK_numberK(fs, e.nval)
		goto vk
	case VK:
		goto vk
	default:
		break
	}
	/* not a constant in the right range: put it in a register */
	return luaK_exp2anyreg(fs, e)
vk:
	e.k = VK
	if e.info <= MAXINDEXRK { /* constant fits in 'argC'? */
		return RKASK(e.info)
	}
	return luaK_exp2anyreg(fs, e)
}

func luaK_storevar(fs *FuncState, var_, ex *expdesc) {
	switch var_.k {
	case VLOCAL:
		freeexp(fs, ex)
		exp2reg(fs, ex, var_.info)
		return
	case VUPVAL:
		e := luaK_exp2anyreg(fs, ex)
		luaK_codeABC(fs, OP_SETUPVAL, e, var_.info, 0)
	case VINDEXED:
		var op int
		if var_.indVt == VLOCAL {
			op = OP_SETTABLE
		} else {
			op = OP_SETTABUP
		}
		e := luaK_exp2RK(fs, ex)
		luaK_codeABC(fs, op, var_.indT, var_.indIdx, e)
	}
	freeexp(fs, ex)
}

func luaK_self(fs *FuncState, e, key *expdesc) {
	luaK_exp2anyreg(fs, e)
	ereg := e.info
	freeexp(fs, e)
	e.info = fs.freereg
	e.k = VNONRELOC
	luaK_reserveregs(fs, 2)
	luaK_codeABC(fs, OP_SELF, e.info, ereg, luaK_exp2RK(fs, key))
	freeexp(fs, key)
}

/* negatecondition: flip A of a comparison jump (lcode.c). */
func negatecondition(fs *FuncState, e *expdesc) {
	pc := getjumpcontrol(fs, e.info)
	pc.A = bool2int(!(pc.A != 0))
}

func jumponcond(fs *FuncState, e *expdesc, cond int) int {
	if e.k == VRELOCABLE {
		ie := getinstruction(fs, e)
		if ie.Op == OP_NOT {
			fs.pc--
			return condjump(fs, OP_TEST, ie.B, 0, bool2int(cond == 0))
		}
	}
	discharge2anyreg(fs, e)
	freeexp(fs, e)
	return condjump(fs, OP_TESTSET, NO_REG, e.info, cond)
}

func luaK_goiftrue(fs *FuncState, e *expdesc) {
	luaK_dischargevars(fs, e)
	var pc int
	switch e.k {
	case VJMP:
		negatecondition(fs, e)
		pc = e.info
	case VK, VKFLT, VKINT, VTRUE:
		pc = NO_JUMP
	default:
		pc = jumponcond(fs, e, 0)
	}
	luaK_concat(fs, &e.f, pc)
	luaK_patchtohere(fs, e.t)
	e.t = NO_JUMP
}

func luaK_goiffalse(fs *FuncState, e *expdesc) {
	luaK_dischargevars(fs, e)
	var pc int
	switch e.k {
	case VJMP:
		pc = e.info
	case VNIL, VFALSE:
		pc = NO_JUMP
	default:
		pc = jumponcond(fs, e, 1)
	}
	luaK_concat(fs, &e.t, pc)
	luaK_patchtohere(fs, e.f)
	e.f = NO_JUMP
}

func codenot(fs *FuncState, e *expdesc) {
	luaK_dischargevars(fs, e)
	switch e.k {
	case VNIL, VFALSE:
		e.k = VTRUE
	case VK, VKFLT, VKINT, VTRUE:
		e.k = VFALSE
	case VJMP:
		negatecondition(fs, e)
	case VRELOCABLE, VNONRELOC:
		discharge2anyreg(fs, e)
		freeexp(fs, e)
		e.info = luaK_codeABC(fs, OP_NOT, 0, e.info, 0)
		e.k = VRELOCABLE
	}
	e.t, e.f = e.f, e.t
	removevalues(fs, e.f)
	removevalues(fs, e.t)
}

func luaK_indexed(fs *FuncState, t, k *expdesc) {
	t.indT = t.info
	t.indIdx = luaK_exp2RK(fs, k)
	if t.k == VUPVAL {
		t.indVt = VUPVAL
	} else {
		t.indVt = VLOCAL
	}
	t.k = VINDEXED
}

func validop(op int, v1, v2 *Value) bool {
	switch op {
	case LUA_OPBAND, LUA_OPBOR, LUA_OPBXOR, LUA_OPSHL, LUA_OPSHR, LUA_OPBNOT: /* conversion errors */
		var i int64
		/* C tointeger; for tonumeral inputs ≡ tointegerns / F2Ieq (LUA_FLOORN2I) */
		return tointegerns(v1, &i) && tointegerns(v2, &i)
	case LUA_OPDIV, LUA_OPIDIV, LUA_OPMOD: /* division by 0 */
		return nvalue(v2) != 0
	default:
		return true /* everything else is valid */
	}
}

func constfolding(fs *FuncState, op int, e1, e2 *expdesc) bool {
	var v1, v2, r Value
	if !tonumeral(e1, &v1) || !tonumeral(e2, &v2) || !validop(op, &v1, &v2) {
		return false /* non-numeric operands or not safe to fold */
	}
	/* C: luaO_arith (TM on failure). Go luaO_arith writes a stack slot;
	   for numerals after validop, luaO_rawarith matches (no TM). */
	if !luaO_rawarith(fs.ls.L, op, &v1, &v2, &r) {
		return false
	}
	if ttisinteger(&r) {
		e1.k = VKINT
		e1.ival = ivalue(&r)
	} else {
		n := fltvalue(&r)
		if math.IsNaN(n) || n == 0 {
			return false
		}
		e1.k = VKFLT
		e1.nval = n
	}
	return true
}

func codeunexpval(fs *FuncState, op int, e *expdesc, line int) {
	r := luaK_exp2anyreg(fs, e)
	freeexp(fs, e)
	e.info = luaK_codeABC(fs, op, 0, r, 0)
	e.k = VRELOCABLE
	luaK_fixline(fs, line)
}

func codebinexpval(fs *FuncState, op int, e1, e2 *expdesc, line int) {
	rk2 := luaK_exp2RK(fs, e2) /* both operands are "RK"; e2 first (stack order) */
	rk1 := luaK_exp2RK(fs, e1)
	freeexps(fs, e1, e2)
	e1.info = luaK_codeABC(fs, op, 0, rk1, rk2)
	e1.k = VRELOCABLE
	luaK_fixline(fs, line)
}

/* codecomp emits comparison jumps (lcode.c). e1 is already R/K from luaK_infix. */
func codecomp(fs *FuncState, opr BinOpr, e1, e2 *expdesc) {
	var rk1 int
	if e1.k == VK {
		rk1 = RKASK(e1.info)
	} else {
		rk1 = e1.info /* VNONRELOC */
	}
	rk2 := luaK_exp2RK(fs, e2)
	freeexps(fs, e1, e2)
	switch opr {
	case OPR_NE: /* '(a ~= b)' ==> 'not (a == b)' */
		e1.info = condjump(fs, OP_EQ, 0, rk1, rk2)
	case OPR_GT, OPR_GE:
		/* '(a > b)' ==> '(b < a)'; '(a >= b)' ==> '(b <= a)' */
		op := int(opr-OPR_NE) + OP_EQ
		e1.info = condjump(fs, op, 1, rk2, rk1)
	default: /* '==', '<', '<=' */
		op := int(opr-OPR_EQ) + OP_EQ
		e1.info = condjump(fs, op, 1, rk1, rk2)
	}
	e1.k = VJMP
}

func luaK_prefix(fs *FuncState, op UnOpr, e *expdesc, line int) {
	var ef expdesc
	ef.k = VKINT
	ef.ival = 0
	ef.t = NO_JUMP
	ef.f = NO_JUMP
	switch op {
	case OPR_MINUS, OPR_BNOT:
		/* op + LUA_OPUNM: OPR_MINUS→LUA_OPUNM, OPR_BNOT→LUA_OPBNOT */
		if constfolding(fs, int(op)+LUA_OPUNM, e, &ef) {
			break
		}
		fallthrough
	case OPR_LEN:
		/* op + OP_UNM: OPR_MINUS→OP_UNM, OPR_BNOT→OP_BNOT, OPR_LEN→OP_LEN */
		codeunexpval(fs, int(op)+OP_UNM, e, line)
	case OPR_NOT:
		codenot(fs, e)
	}
}

func luaK_infix(fs *FuncState, op BinOpr, v *expdesc) {
	switch op {
	case OPR_AND:
		luaK_goiftrue(fs, v)
	case OPR_OR:
		luaK_goiffalse(fs, v)
	case OPR_CONCAT:
		luaK_exp2nextreg(fs, v)
	case OPR_ADD, OPR_SUB, OPR_MUL, OPR_DIV, OPR_IDIV, OPR_MOD, OPR_POW,
		OPR_BAND, OPR_BOR, OPR_BXOR, OPR_SHL, OPR_SHR:
		if !tonumeral(v, nil) {
			luaK_exp2RK(fs, v)
		}
	default:
		luaK_exp2RK(fs, v)
	}
}

func luaK_posfix(fs *FuncState, op BinOpr, e1, e2 *expdesc, line int) {
	switch op {
	case OPR_AND:
		luaK_dischargevars(fs, e2)
		luaK_concat(fs, &e2.f, e1.f)
		*e1 = *e2
	case OPR_OR:
		luaK_dischargevars(fs, e2)
		luaK_concat(fs, &e2.t, e1.t)
		*e1 = *e2
	case OPR_CONCAT:
		luaK_exp2val(fs, e2)
		if e2.k == VRELOCABLE && getinstruction(fs, e2).Op == OP_CONCAT {
			freeexp(fs, e1)
			getinstruction(fs, e2).B = e1.info
			e1.k = VRELOCABLE
			e1.info = e2.info
		} else {
			luaK_exp2nextreg(fs, e2)
			codebinexpval(fs, OP_CONCAT, e1, e2, line)
		}
	case OPR_ADD, OPR_SUB, OPR_MUL, OPR_DIV, OPR_IDIV, OPR_MOD, OPR_POW,
		OPR_BAND, OPR_BOR, OPR_BXOR, OPR_SHL, OPR_SHR:
		if !constfolding(fs, int(op)+LUA_OPADD, e1, e2) {
			codebinexpval(fs, int(op)+OP_ADD, e1, e2, line)
		}
	case OPR_EQ, OPR_LT, OPR_LE, OPR_NE, OPR_GT, OPR_GE:
		codecomp(fs, op, e1, e2)
	}
}

func luaK_fixline(fs *FuncState, line int) {
	fs.f.lineinfo[fs.pc-1] = int32(line)
}

func luaK_setlist(fs *FuncState, base, nelems, tostore int) {
	c := (nelems-1)/LFIELDS_PER_FLUSH + 1
	b := tostore
	if tostore == LUA_MULTRET {
		b = 0
	}
	if c <= MAXARG_C {
		luaK_codeABC(fs, OP_SETLIST, base, b, c)
	} else if c <= MAXARG_Ax {
		luaK_codeABC(fs, OP_SETLIST, base, b, 0)
		codeextraarg(fs, c)
	} else {
		luaX_syntaxerror(fs.ls, "constructor too long")
	}
	fs.freereg = base + 1
}
