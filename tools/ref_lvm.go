// Translation of lvm.h / lvm.c (PUC-Lua 5.3.4) — line-faithful C→Go.
//
// Opcode dispatch matches 5.3.4's 47 ops (no lvm_fast*.go, no fastLeaf /
// fastfor / fasttail, no concat cache). Proto.cache / getcached /
// pushclosure are legitimate 5.3.4 (not a concat cache).
//
// Int↔float order: LTintfloat / LEintfloat / LTnum / LEnum use 5.3.4
// range tests + toward-zero cast (NOT 5.4 luaV_flttointeger ceil/floor).
//
// Intentional omissions / adaptations (see NOTES.md):
//   - GC barriers / checkGC / gcAfterAlloc / migrateNested (Go GC)
//   - luaV_finishOp absent (yield does not longjmp out of the interpreter)
//   - vmFastGeti purged (was 5.4-shaped; lapi uses luaH_getint directly)
package core

import (
	"math"
	"strings"
)

const MAXTAGLOOP = 2000

// upvalValue returns the current value of an upvalue (open upvalues live
// in the stack of their thread).
func upvalValue(uv *UpVal) *Value {
	if uv.open {
		return &uv.L.stack[uv.idx]
	}
	return &uv.v
}

// luaV_div is integer floor division 'm // n' (lvm.c 5.3.4).
// C division truncates toward zero; floor(q)==trunc(q) when q>=0 or
// integral, otherwise floor(q)==trunc(q)-1.
func luaV_div(L *State, m, n int64) int64 {
	if uint64(n)+1 <= 1 { /* special cases: -1 or 0 */
		if n == 0 {
			luaG_runerror(L, "attempt to divide by zero")
			return 0
		}
		return -m /* n == -1; avoid overflow with 0x8000... // -1 */
	}
	q := m / n                 /* perform C division */
	if (m^n) < 0 && m%n != 0 { /* 'm/n' would be negative non-integer? */
		q-- /* correct result for different rounding */
	}
	return q
}

// luaV_mod is integer modulus 'm % n' (lvm.c 5.3.4).
func luaV_mod(L *State, m, n int64) int64 {
	if uint64(n)+1 <= 1 { /* special cases: -1 or 0 */
		if n == 0 {
			luaG_runerror(L, "attempt to perform 'n%%0'")
			return 0
		}
		return 0 /* m % -1 == 0; avoid overflow with 0x8000... % -1 */
	}
	r := m % n
	if r != 0 && (m^n) < 0 { /* 'm/n' would be non-integer negative? */
		r += n /* correct result for different rounding */
	}
	return r
}

// luaV_modf is the float modulus (lvm.c). Unlike the integer version it
// never raises: 'luai_nummod' is plain 'fmod', so '1.0 % 0.0' is NaN.
func luaV_modf(L *State, m, n float64) float64 {
	r := math2Fmod(m, n)
	/* 5.3.4 luai_nummod (llimits.h): { (m) = l_mathop(fmod)(a,b); if ((m)*(b) < 0) (m) += (b); } */
	if r*n < 0 {
		r += n
	}
	return r
}

const NBITS = 64

// luaV_shiftl shifts x left by y (a negative y shifts right).
func luaV_shiftl(x, y int64) int64 {
	if y < 0 { /* shift right? */
		if y <= -NBITS {
			return 0
		}
		return int64(uint64(x) >> uint64(-y))
	}
	if y >= NBITS {
		return 0
	}
	return int64(uint64(x) << uint64(y))
}

// luaV_concat concatenates the 'total' values on top of the stack.
func luaV_concat(L *State, total int) {
	for {
		top := L.top
		n := 2
		o1 := &L.stack[top-2]
		o2 := &L.stack[top-1]
		if !(ttisstring(o1) || cvt2str(o1)) || !tostringInPlace(L, o2) {
			luaT_tryconcatTM(L)
		} else if isEmptyStr(o2) { /* second operand is empty? */
			tostringInPlace(L, o1) /* result is first operand */
		} else if isEmptyStr(o1) { /* first operand is empty string? */
			L.stack[top-2] = L.stack[top-1] /* result is second operand */
		} else {
			/* at least two non-empty string values; get as many as possible */
			parts := make([]string, 0, total)
			totalLen := 0
			parts = append(parts, tsvalue(o2).val)
			totalLen += len(tsvalue(o2).val)
			for n = 1; n < total && tostringInPlace(L, &L.stack[top-n-1]); n++ {
				s := tsvalue(&L.stack[top-n-1]).val
				parts = append(parts, s)
				totalLen += len(s)
			}
			var sb strings.Builder
			sb.Grow(totalLen)
			for i := len(parts) - 1; i >= 0; i-- {
				sb.WriteString(parts[i])
			}
			setsvalue(&L.stack[top-n], luaS_newlstr(L, sb.String()))
		}
		total -= n - 1 /* got 'n' strings to create one new */
		L.top -= n - 1 /* popped 'n' strings and pushed one */
		if total <= 1 {
			return
		}
	}
}

// isEmptyStr is C isemptystr: short empty string only (lvm.c).
// Long empty strings do not short-circuit concat the same way in PUC.
func isEmptyStr(v *Value) bool {
	return ttisshrstring(v) && len(tsvalue(v).val) == 0
}

// tostringInPlace converts a number to a string, in place.
func tostringInPlace(L *State, o *Value) bool {
	if ttisstring(o) {
		return true
	}
	if cvt2str(o) {
		luaO_tostring(L, o)
		return true
	}
	return false
}

// luaV_finishget finishes a table access 'val = t[key]' (lvm.c).
func luaV_finishget(L *State, t, key *Value, val int, slot *Value) {
	L.migrateNested()
	for loop := 0; loop < MAXTAGLOOP; loop++ {
		var tm *Value
		if slot == nil { /* 't' is not a table? */
			tm = luaT_gettmbyobj(L, t, TM_INDEX)
			if notm(tm) {
				luaG_typeerror(L, t, "index") /* no metamethod */
				return
			}
		} else { /* 't' is a table */
			tm = fasttm(L, hvalue(t).metatable, TM_INDEX)
			if tm == nil { /* no metamethod? */
				setnilvalue(&L.stack[val]) /* result is nil */
				return
			}
		}
		if ttisfunction(tm) { /* is metamethod a function? */
			luaT_callTMres(L, tm, t, key, val)
			return
		}
		t = tm /* else try to access 'tm[key]' */
		slot = vmFastGet(L, t, key)
		if slot != nil && !isempty(slot) {
			L.stack[val] = slotVal(slot)
			return
		}
	}
	luaG_runerror(L, "'__index' chain too long; possible loop")
}

// luaV_finishset finishes a table assignment 't[key] = val' (lvm.c).
func luaV_finishset(L *State, t, key, val *Value, slot *Value) {
	L.migrateNested()
	for loop := 0; loop < MAXTAGLOOP; loop++ {
		var tm *Value
		if slot != nil { /* is 't' a table? */
			h := hvalue(t)
			tm = fasttm(L, h.metatable, TM_NEWINDEX)
			if tm == nil { /* no metamethod? */
				luaH_setval(L, h, key, val) /* 5.3.4: nilobject→newkey then setobj2t */
				invalidateTMcache(h)
				return
			}
		} else { /* not a table; check metamethod */
			tm = luaT_gettmbyobj(L, t, TM_NEWINDEX)
			if notm(tm) {
				luaG_typeerror(L, t, "index")
				return
			}
		}
		if ttisfunction(tm) { /* is metamethod a function? */
			luaT_callTM(L, tm, t, key, val)
			return
		}
		t = tm /* else repeat assignment over 'tm' */
		slot = vmFastGet(L, t, key)
		if slot != nil && !isempty(slot) {
			luaH_setSlotVal(hvalue(t), slot, val)
			return
		}
	}
	luaG_runerror(L, "'__newindex' chain too long; possible loop")
}

// vmFastGet is luaV_fastget(L,t,k,slot,luaH_get): raw table access.
// Returns nil when 't' is not a table (C slot=NULL); otherwise luaH_get's
// slot (may be luaO_nilobject). No specialized key dispatch here — that
// lives inside luaH_get, matching C's macro + f=luaH_get.
func vmFastGet(L *State, t, key *Value) *Value {
	if t.tt != ctb(LUA_VTABLE) {
		return nil
	}
	return luaH_get(L, (*Table)(t.p), key)
}

// getcached returns p.cache if it has the same upvalues the new closure would.
func getcached(p *Proto, encup []*UpVal, L *State, base int) *LClosure {
	c := p.cache
	if c == nil {
		return nil
	}
	for i := 0; i < p.sizeupvalues; i++ {
		uv := c.upvals[i]
		if p.upvalues[i].instack != 0 {
			level := base + int(p.upvalues[i].idx)
			if uv == nil || !uv.open || uv.L != L || uv.idx != level {
				return nil
			}
		} else if uv != encup[p.upvalues[i].idx] {
			return nil
		}
	}
	return c
}

// pushclosure creates a new Lua closure, initializes upvalues, and caches it.
func pushclosure(L *State, p *Proto, encup []*UpVal, base, ra int) {
	nup := p.sizeupvalues
	uv := p.upvalues
	ncl := luaF_newLclosure(L, nup)
	ncl.p = p
	setclLvalue(&L.stack[ra], ncl)
	for i := 0; i < nup; i++ {
		if uv[i].instack != 0 { /* upvalue refers to local variable? */
			ncl.upvals[i] = luaF_findupval(L, base+int(uv[i].idx))
		} else { /* get upvalue from enclosing function */
			ncl.upvals[i] = encup[uv[i].idx]
		}
	}
	p.cache = ncl /* 5.3: reuse when upvalues match (no GC color check here) */
}

// savestate saves the current pc and puts the stack top at the end of
// the current frame, exactly like the 'savestate' macro of lvm.c. Every
// operation that may push temporaries on the stack (metamethod calls,
// finishing a table access, comparisons, ...) must run with L.top above
// the frame's registers, otherwise the temporary frame would clobber the
// registers that are also the operands of the operation.
func savestate(L *State, ci *CallInfo, pc int) {
	ci.savedpc = pc
	L.top = ci.top
}

// luaV_execute is the main interpreter loop (lvm.c).
func rkVal(L *State, base int, k []Value, x int) *Value {
	if ISK(x) {
		return &k[INDEXK(x)]
	}
	return &L.stack[base+x]
}

// forprep is OP_FORPREP (lvm.c 5.3.4).
func forprep(L *State, ra int) {
	init := &L.stack[ra]
	plimit := &L.stack[ra+1]
	pstep := &L.stack[ra+2]
	var ilimit int64
	var stopnow bool
	if ttisinteger(init) && ttisinteger(pstep) && forlimit(L, plimit, &ilimit, ivalue(pstep), &stopnow) {
		initv := int64(0)
		if !stopnow {
			initv = ivalue(init)
		}
		setivalue(plimit, ilimit)
		setivalue(init, initv-ivalue(pstep))
	} else {
		var ninit, nlimit, nstep float64
		/* 5.3: tonumber coerces strings (not tonumberns). */
		if !luaV_tonumber_(L, plimit, &nlimit) {
			luaG_runerror(L, "'for' limit must be a number")
		}
		setfltvalue(plimit, nlimit)
		if !luaV_tonumber_(L, pstep, &nstep) {
			luaG_runerror(L, "'for' step must be a number")
		}
		setfltvalue(pstep, nstep)
		if !luaV_tonumber_(L, init, &ninit) {
			luaG_runerror(L, "'for' initial value must be a number")
		}
		setfltvalue(init, ninit-nstep)
	}
}

// forlimit converts a for-loop limit (lvm.c 5.3.4).
func forlimit(L *State, obj *Value, p *int64, step int64, stopnow *bool) bool {
	*stopnow = false
	mode := F2Ifloor
	if step < 0 {
		mode = F2Iceil
	}
	if !luaV_tointeger(L, obj, p, mode) {
		var n float64
		if !luaV_tonumber_(L, obj, &n) {
			return false
		}
		if n > 0 {
			*p = LUA_MAXINTEGER
			if step < 0 {
				*stopnow = true
			}
		} else {
			*p = LUA_MININTEGER
			if step >= 0 {
				*stopnow = true
			}
		}
	}
	return true
}

func luaV_execute(L *State, ci *CallInfo) {
	/* C sets CIST_FRESH here; Go sets it in luaD_call instead.
	   FLAG goroutine/sync-yield: resume re-enters execute on the yielded
	   frame without C's unroll. FRESH on that frame would make OP_RETURN
	   exit the VM and skip parent Lua frames (breaks coroutine.wrap
	   generators). luaD_call still marks true fresh invocations. */
	prevVM := L.inVM
	L.inVM = true
	L.vmNest++
	defer func() {
		L.vmNest--
		L.inVM = prevVM
	}()
	var (
		cl   *LClosure
		code []Instruction
		k    []Value
		pc   int
	)
	var trap bool
	restart := true
	for restart {
		restart = false
		cl = clLvalue(&L.stack[ci.funcIdx])
		code = cl.p.code
		k = cl.p.k
		pc = ci.savedpc
		trap = L.hookmask != 0
		if trap {
			trap = luaG_tracecall(L, ci)
		}
	loop:
		for {
			/* 5.3 has no trap bit: vmfetch checks hookmask every
			   instruction. We keep the 5.4 local 'trap' but must
			   honour ci.trap that setTraps (lua_sethook) sets, or a
			   mid-chunk debug.sethook never fires (db.lua hooks). */
			if ci.trap {
				trap = true
			}
			if trap {
				trap = luaG_traceexec(L, ci, pc)
			}
			i := code[pc]
			pc++
			/* C vmfetch: i = *(ci->u.l.savedpc++); keep savedpc past
			   the current insn so luaG_runerror/currentline see it
			   even when the op errors before the loop-end update. */
			ci.savedpc = pc
			base := ci.base
			ra := base + i.A

			switch i.Op {
			case OP_MOVE:
				L.stack[ra] = L.stack[base+i.B]
			case OP_LOADK:
				L.stack[ra] = k[i.B]
			case OP_LOADKX:
				L.stack[ra] = k[code[pc].A]
				pc++
			case OP_LOADBOOL:
				if i.B != 0 {
					setbtvalue(&L.stack[ra])
				} else {
					setbfvalue(&L.stack[ra])
				}
				if i.C != 0 {
					pc++
				}
			case OP_LOADNIL:
				for b := i.B; b >= 0; b-- {
					setnilvalue(&L.stack[ra])
					ra++
				}
			case OP_GETUPVAL:
				L.stack[ra] = *upvalValue(cl.upvals[i.B])
			case OP_GETTABUP:
				upval := upvalValue(cl.upvals[i.B])
				rc := rkVal(L, base, k, i.C)
				slot := vmFastGet(L, upval, rc)
				if slot != nil && !isempty(slot) {
					L.stack[ra] = slotVal(slot)
				} else {
					savestate(L, ci, pc)
					luaV_finishget(L, upval, rc, ra, slot)
					base = ci.base
				}
			case OP_GETTABLE:
				rb := &L.stack[base+i.B]
				rc := rkVal(L, base, k, i.C)
				slot := vmFastGet(L, rb, rc)
				if slot != nil && !isempty(slot) {
					L.stack[ra] = slotVal(slot)
				} else {
					savestate(L, ci, pc)
					luaV_finishget(L, rb, rc, ra, slot)
					base = ci.base
				}
			case OP_SETTABUP:
				upval := upvalValue(cl.upvals[i.A])
				rb := rkVal(L, base, k, i.B)
				rc := rkVal(L, base, k, i.C)
				slot := vmFastGet(L, upval, rb)
				if slot != nil && !isempty(slot) {
					luaH_setSlotVal(hvalue(upval), slot, rc)
				} else {
					savestate(L, ci, pc)
					luaV_finishset(L, upval, rb, rc, slot)
					base = ci.base
				}
			case OP_SETUPVAL:
				*upvalValue(cl.upvals[i.B]) = L.stack[ra]
			case OP_SETTABLE:
				rb := rkVal(L, base, k, i.B)
				rc := rkVal(L, base, k, i.C)
				slot := vmFastGet(L, &L.stack[ra], rb)
				if slot != nil && !isempty(slot) {
					luaH_setSlotVal(hvalue(&L.stack[ra]), slot, rc)
				} else {
					savestate(L, ci, pc)
					luaV_finishset(L, &L.stack[ra], rb, rc, slot)
					base = ci.base
				}
			case OP_NEWTABLE:
				b, c := i.B, i.C
				t := luaH_new(L)
				sethvalue(&L.stack[ra], t)
				if b != 0 || c != 0 {
					luaH_resize(L, t, uint32(luaO_fb2int(b)), uint32(luaO_fb2int(c)))
				}
				/* PUC luaC_checkGC after allocation; metamethod may run Lua. */
				ci.savedpc = pc
				if L.gcAfterAlloc() {
					continue
				}
			case OP_SELF:
				/* C: luaV_fastget(..., luaH_getstr); key must be a string. */
				rb := &L.stack[base+i.B]
				L.stack[ra+1] = *rb
				rc := rkVal(L, base, k, i.C)
				var slot *Value
				if ttistable(rb) {
					slot = luaH_getstr(L, hvalue(rb), tsvalue(rc))
					if !ttisnil(slot) {
						L.stack[ra] = slotVal(slot)
					} else {
						savestate(L, ci, pc)
						luaV_finishget(L, rb, rc, ra, slot)
						base = ci.base
					}
				} else {
					savestate(L, ci, pc)
					luaV_finishget(L, rb, rc, ra, nil)
					base = ci.base
				}
			case OP_ADD, OP_SUB, OP_MUL, OP_MOD, OP_POW, OP_DIV, OP_IDIV,
				OP_BAND, OP_BOR, OP_BXOR, OP_SHL, OP_SHR:
				rb := rkVal(L, base, k, i.B)
				rc := rkVal(L, base, k, i.C)
				if !vmBinOp(L, i.Op, &L.stack[ra], rb, rc) {
					savestate(L, ci, pc)
					tm := binOpTM(i.Op)
					luaT_trybinTM(L, rb, rc, ra, tm)
					base = ci.base
				}
			case OP_UNM:
				rb := &L.stack[base+i.B]
				if ttisinteger(rb) {
					setivalue(&L.stack[ra], -ivalue(rb))
				} else {
					var nb float64
					if luaV_tonumber_(L, rb, &nb) {
						setfltvalue(&L.stack[ra], -nb)
					} else {
						savestate(L, ci, pc)
						luaT_trybinTM(L, rb, rb, ra, TM_UNM)
						base = ci.base
					}
				}
			case OP_BNOT:
				rb := &L.stack[base+i.B]
				var ib int64
				if tointeger(L, rb, &ib) {
					setivalue(&L.stack[ra], ^ib)
				} else {
					savestate(L, ci, pc)
					luaT_trybinTM(L, rb, rb, ra, TM_BNOT)
					base = ci.base
				}
			case OP_NOT:
				rb := &L.stack[base+i.B]
				if l_isfalse(rb) {
					setbtvalue(&L.stack[ra])
				} else {
					setbfvalue(&L.stack[ra])
				}
			case OP_LEN:
				savestate(L, ci, pc)
				luaV_objlen(L, ra, &L.stack[base+i.B])
				base = ci.base
			case OP_CONCAT:
				/* Like PUC 5.3 Protect / 5.4 ProtectNT: mark operand end in
				   L.top, save pc, but do NOT reset top to ci.top (savestate
				   would) before luaV_concat sees the operands. */
				b, c := i.B, i.C
				L.top = base + c + 1
				ci.savedpc = pc
				luaV_concat(L, c-b+1)
				base = ci.base
				ra = base + i.A
				L.stack[ra] = L.stack[base+b]
				L.top = ci.top
				/* concatenation allocates; programs may wait on the collector */
				if L.gcAfterAlloc() {
					continue
				}
			case OP_JMP:
				if i.A != 0 {
					luaF_close(L, base+i.A-1)
				}
				pc += sBx(i.B)
			case OP_EQ:
				rb := rkVal(L, base, k, i.B)
				rc := rkVal(L, base, k, i.C)
				savestate(L, ci, pc)
				eq := luaV_equalobj(L, rb, rc)
				base = ci.base
				if (eq && i.A != 0) || (!eq && i.A == 0) {
					ji := code[pc]
					pc++
					if ji.A != 0 {
						luaF_close(L, base+ji.A-1)
					}
					pc += sBx(ji.B)
				} else {
					pc++
				}
			case OP_LT, OP_LE:
				/* 5.3.4 OP_LT / OP_LE: Protect + donextjump (same shape as EQ). */
				rb := rkVal(L, base, k, i.B)
				rc := rkVal(L, base, k, i.C)
				savestate(L, ci, pc)
				var res bool
				if i.Op == OP_LT {
					res = luaV_lessthan(L, rb, rc)
				} else {
					res = luaV_lessequal(L, rb, rc)
				}
				base = ci.base
				if (res && i.A != 0) || (!res && i.A == 0) {
					ji := code[pc]
					pc++
					if ji.A != 0 {
						luaF_close(L, base+ji.A-1)
					}
					pc += sBx(ji.B)
				} else {
					pc++
				}
			case OP_TEST:
				cond := !l_isfalse(&L.stack[ra])
				if (i.C != 0 && !cond) || (i.C == 0 && cond) {
					pc++
				} else {
					ji := code[pc]
					pc++
					if ji.A != 0 {
						luaF_close(L, base+ji.A-1)
					}
					pc += sBx(ji.B)
				}
			case OP_TESTSET:
				rb := &L.stack[base+i.B]
				cond := !l_isfalse(rb)
				if (i.C != 0 && !cond) || (i.C == 0 && cond) {
					pc++
				} else {
					L.stack[ra] = *rb
					ji := code[pc]
					pc++
					if ji.A != 0 {
						luaF_close(L, base+ji.A-1)
					}
					pc += sBx(ji.B)
				}
			case OP_CALL:
				b := i.B
				nresults := i.C - 1
				if b != 0 {
					L.top = ra + b /* else previous instruction set top */
				}
				ci.savedpc = pc
				if luaD_precall(L, ra, nresults) != 0 { /* C function? */
					if L.fastYield { /* FLAG: Go-only yield early-return; not in 5.3.4 lvm.c */
						/* yield already set top; do not apply ci.top */
						return
					}
					if nresults >= 0 {
						L.top = ci.top /* adjust results */
					}
					trap = ci.trap || L.hookmask != 0
				} else { /* Lua function */
					ci = L.ci
					restart = true
					break loop /* restart luaV_execute over new Lua function */
				}
			case OP_TAILCALL:
				/* 5.3.4 lvm.c: precall; if Lua, merge called frame into caller. */
				b := i.B
				if b != 0 {
					L.top = ra + b /* else previous instruction set top */
				}
				ci.savedpc = pc
				if luaD_precall(L, ra, LUA_MULTRET) != 0 { /* C function? */
					if L.fastYield { /* FLAG: Go-only; not in 5.3.4 */
						return
					}
					trap = ci.trap || L.hookmask != 0
					/* fall through to next opcode (RETURN) like C */
				} else {
					/* tail call: put called frame (n) in place of caller one (o) */
					nci := L.ci     /* called frame */
					oci := nci.prev /* caller frame */
					nfunc := nci.funcIdx
					ofunc := oci.funcIdx
					/* last stack slot filled by 'precall' */
					lim := nci.base + int(clLvalue(&L.stack[nfunc]).p.numparams)
					/* close all upvalues from previous call */
					if cl.p.sizep > 0 {
						luaF_close(L, oci.base)
					}
					/* move new frame into old one */
					for aux := 0; nfunc+aux < lim; aux++ {
						L.stack[ofunc+aux] = L.stack[nfunc+aux]
					}
					oci.base = ofunc + (nci.base - nfunc) /* correct base */
					L.top = ofunc + (L.top - nfunc)
					oci.top = L.top /* correct top */
					oci.savedpc = nci.savedpc
					oci.callstatus |= CIST_TAIL     /* function was tail called */
					oci.nExtraArgs = nci.nExtraArgs /* FLAG: Go CI field; not in 5.3.4 CallInfo */
					L.ci = oci                      /* remove new frame */
					ci = oci
					restart = true
					break loop /* restart luaV_execute over new Lua function */
				}
			case OP_RETURN:
				b := i.B
				ci.savedpc = pc
				if cl.p.sizep > 0 {
					luaF_close(L, base)
				}
				nres := b - 1
				if b == 0 {
					nres = L.top - ra
				}
				bpos := luaD_poscall(L, ci, ra, nres)
				if ci.callstatus&CIST_FRESH != 0 { /* local 'ci' still from callee */
					return /* external invocation: return */
				}
				/* invocation via reentry: continue execution */
				ci = L.ci
				if bpos != 0 {
					L.top = ci.top
				}
				restart = true
				break loop
			case OP_FORLOOP:
				if ttisinteger(&L.stack[ra]) {
					step := ivalue(&L.stack[ra+2])
					idx := ivalue(&L.stack[ra]) + step
					limit := ivalue(&L.stack[ra+1])
					goon := false
					if step > 0 {
						goon = idx <= limit
					} else {
						goon = limit <= idx
					}
					if goon {
						pc += sBx(i.B)
						setivalue(&L.stack[ra], idx)
						setivalue(&L.stack[ra+3], idx)
					}
				} else {
					step := fltvalue(&L.stack[ra+2])
					idx := fltvalue(&L.stack[ra]) + step
					limit := fltvalue(&L.stack[ra+1])
					goon := false
					if step > 0 {
						goon = idx <= limit
					} else {
						goon = limit <= idx
					}
					if goon {
						pc += sBx(i.B)
						setfltvalue(&L.stack[ra], idx)
						setfltvalue(&L.stack[ra+3], idx)
					}
				}
			case OP_FORPREP:
				forprep(L, ra)
				pc += sBx(i.B)
			case OP_TFORCALL:
				cb := ra + 3
				L.stack[cb+2] = L.stack[ra+2]
				L.stack[cb+1] = L.stack[ra+1]
				L.stack[cb] = L.stack[ra]
				L.top = cb + 3
				savestate(L, ci, pc)
				luaD_call(L, cb, i.C)
				base = ci.base
				L.top = ci.top
				i = code[pc]
				pc++
				ra = base + i.A
				fallthrough
			case OP_TFORLOOP:
				if !ttisnil(&L.stack[ra+1]) {
					L.stack[ra] = L.stack[ra+1]
					pc += sBx(i.B)
				}
			case OP_SETLIST:
				n := i.B
				c := i.C
				if n == 0 {
					n = L.top - ra - 1
				}
				if c == 0 {
					c = code[pc].A
					pc++
				}
				h := hvalue(&L.stack[ra])
				last := ((c - 1) * LFIELDS_PER_FLUSH) + n
				if uint32(last) > h.sizearray {
					luaH_resizearray(L, h, uint32(last))
				}
				for ; n > 0; n-- {
					val := &L.stack[ra+n]
					luaH_setint(L, h, int64(last), val)
					last--
				}
				L.top = ci.top
			case OP_CLOSURE:
				p := cl.p.p[i.B]
				if ncl := getcached(p, cl.upvals, L, base); ncl != nil {
					setclLvalue(&L.stack[ra], ncl)
				} else {
					pushclosure(L, p, cl.upvals, base, ra)
				}
				ci.savedpc = pc
				if L.gcAfterAlloc() {
					continue
				}
			case OP_VARARG:
				b := i.B - 1
				n := (base - ci.funcIdx) - int(cl.p.numparams) - 1
				if n < 0 {
					n = 0
				}
				if b < 0 {
					b = n
					L.checkStack(n)
					base = ci.base
					ra = base + i.A
					L.top = ra + n
				}
				j := 0
				for ; j < b && j < n; j++ {
					L.stack[ra+j] = L.stack[base-n+j]
				}
				for ; j < b; j++ {
					setnilvalue(&L.stack[ra+j])
				}
			case OP_EXTRAARG:
				// should not happen
			default:
				luaG_runerror(L, "unknown opcode %d", i.Op)
			}
			ci.savedpc = pc
		}
	}
}

func binOpTM(op int) TMS {
	switch op {
	case OP_ADD:
		return TM_ADD
	case OP_SUB:
		return TM_SUB
	case OP_MUL:
		return TM_MUL
	case OP_MOD:
		return TM_MOD
	case OP_POW:
		return TM_POW
	case OP_DIV:
		return TM_DIV
	case OP_IDIV:
		return TM_IDIV
	case OP_BAND:
		return TM_BAND
	case OP_BOR:
		return TM_BOR
	case OP_BXOR:
		return TM_BXOR
	case OP_SHL:
		return TM_SHL
	case OP_SHR:
		return TM_SHR
	}
	return TM_ADD
}

func vmBinOp(L *State, op int, ra, rb, rc *Value) bool {
	/* Lua 5.3: tonumber/tointeger coerce strings (unlike 5.4's *ns helpers). */
	switch op {
	case OP_ADD:
		if ttisinteger(rb) && ttisinteger(rc) {
			setivalue(ra, ivalue(rb)+ivalue(rc))
			return true
		}
		var nb, nc float64
		if luaV_tonumber_(L, rb, &nb) && luaV_tonumber_(L, rc, &nc) {
			setfltvalue(ra, nb+nc)
			return true
		}
	case OP_SUB:
		if ttisinteger(rb) && ttisinteger(rc) {
			setivalue(ra, ivalue(rb)-ivalue(rc))
			return true
		}
		var nb, nc float64
		if luaV_tonumber_(L, rb, &nb) && luaV_tonumber_(L, rc, &nc) {
			setfltvalue(ra, nb-nc)
			return true
		}
	case OP_MUL:
		if ttisinteger(rb) && ttisinteger(rc) {
			setivalue(ra, ivalue(rb)*ivalue(rc))
			return true
		}
		var nb, nc float64
		if luaV_tonumber_(L, rb, &nb) && luaV_tonumber_(L, rc, &nc) {
			setfltvalue(ra, nb*nc)
			return true
		}
	case OP_DIV:
		var nb, nc float64
		if luaV_tonumber_(L, rb, &nb) && luaV_tonumber_(L, rc, &nc) {
			setfltvalue(ra, nb/nc)
			return true
		}
	case OP_IDIV:
		if ttisinteger(rb) && ttisinteger(rc) {
			setivalue(ra, luaV_div(L, ivalue(rb), ivalue(rc)))
			return true
		}
		var nb, nc float64
		if luaV_tonumber_(L, rb, &nb) && luaV_tonumber_(L, rc, &nc) {
			setfltvalue(ra, math.Floor(nb/nc))
			return true
		}
	case OP_MOD:
		if ttisinteger(rb) && ttisinteger(rc) {
			setivalue(ra, luaV_mod(L, ivalue(rb), ivalue(rc)))
			return true
		}
		var nb, nc float64
		if luaV_tonumber_(L, rb, &nb) && luaV_tonumber_(L, rc, &nc) {
			setfltvalue(ra, luaV_modf(L, nb, nc))
			return true
		}
	case OP_POW:
		var nb, nc float64
		if luaV_tonumber_(L, rb, &nb) && luaV_tonumber_(L, rc, &nc) {
			setfltvalue(ra, math.Pow(nb, nc))
			return true
		}
	case OP_BAND, OP_BOR, OP_BXOR, OP_SHL, OP_SHR:
		var ib, ic int64
		if tointeger(L, rb, &ib) && tointeger(L, rc, &ic) {
			switch op {
			case OP_BAND:
				setivalue(ra, ib&ic)
			case OP_BOR:
				setivalue(ra, ib|ic)
			case OP_BXOR:
				setivalue(ra, ib^ic)
			case OP_SHL:
				setivalue(ra, luaV_shiftl(ib, ic))
			case OP_SHR:
				setivalue(ra, luaV_shiftl(ib, -ic))
			}
			return true
		}
	}
	return false
}

// ---------------------------------------------------------------------
// Conversions & comparisons (lvm.c) — moved from ldebug.go for fidelity
// ---------------------------------------------------------------------

// luaV_tonumber_ converts a value to a float, with string coercion.
func luaV_tonumber_(L *State, o *Value, n *float64) bool {
	switch {
	case ttisinteger(o):
		*n = float64(ivalue(o))
		return true
	case ttisfloat(o):
		*n = fltvalue(o)
		return true
	case ttisstring(o): // string coercion
		var v Value
		return luaO_str2num(tsvalue(o).val, &v) && numberToFloat(&v, n)
	}
	return false
}

func numberToFloat(v *Value, n *float64) bool {
	if ttisinteger(v) {
		*n = float64(ivalue(v))
		return true
	}
	if ttisfloat(v) {
		*n = fltvalue(v)
		return true
	}
	return false
}

// luaV_flttointeger converts a float to an integer with a rounding mode.
func luaV_flttointeger(n float64, p *int64, mode F2Imod) bool {
	var f float64
	switch mode {
	case F2Ifloor:
		f = mathFloor(n)
	case F2Iceil:
		f = mathCeil(n)
	default:
		f = n
	}
	return floatToInt(f, p)
}

func mathFloor(n float64) float64 {
	if n == math2Trunc(n) {
		return n
	}
	return math2Floor(n)
}

func mathCeil(n float64) float64 {
	if n == math2Trunc(n) {
		return n
	}
	return math2Ceil(n)
}

func floatToInt(n float64, p *int64) bool {
	if n == math2Trunc(n) && !math2IsNaN(n) && n >= -9.223372036854775808e18 && n < 9.223372036854775808e18 {
		*p = int64(n)
		return true
	}
	return false
}

// luaV_tointeger converts a value to an integer (with string coercion).
//
// C converts the string to a number first and *then* applies the rounding
// mode, so '"10.8"' becomes 10 under F2Ifloor and 11 under F2Iceil.
// Dropping the mode (as a plain conversion would) makes 'for i = 1,"10.8"'
// fail to convert and fall into the wrong, unbounded loop.
func luaV_tointeger(L *State, o *Value, p *int64, mode F2Imod) bool {
	if ttisstring(o) {
		var v Value
		if luaO_str2num(tsvalue(o).val, &v) {
			o = &v /* change it to point to its corresponding number */
		}
	}
	return luaV_tointegerns(o, p, mode)
}

// luaV_tointegerns converts a number to an integer without string coercion.
func luaV_tointegerns(o *Value, p *int64, mode F2Imod) bool {
	switch {
	case ttisinteger(o):
		*p = ivalue(o)
		return true
	case ttisfloat(o):
		return luaV_flttointeger(fltvalue(o), p, mode)
	}
	return false
}

// F2Imod is the rounding mode for float -> integer conversion (lvm.h).
type F2Imod int

const (
	F2Ieq F2Imod = iota
	F2Ifloor
	F2Iceil
)

// LUA_FLOORN2I is the rounding mode used to convert floats to integers
// (lvm.h). It is F2Ieq unless overridden.
const LUA_FLOORN2I = F2Ieq

// tointegerns converts to integer without string coercion.
// (5.4 name; 5.3 uses the tointeger macro after the value is known numeric.)
func tointegerns(o *Value, i *int64) bool {
	if ttisinteger(o) {
		*i = ivalue(o)
		return true
	}
	return luaV_tointegerns(o, i, F2Ieq)
}

// tointeger converts to integer with string coercion (lvm.h).
func tointeger(L *State, o *Value, i *int64) bool {
	if ttisinteger(o) {
		*i = ivalue(o)
		return true
	}
	return luaV_tointeger(L, o, i, F2Ieq)
}

// luaV_equalobj / luaV_lessthan / luaV_lessequal implement comparisons
// with metamethods (lvm.c).
func luaV_equalobj(L *State, t1, t2 *Value) bool {
	var tm *Value
	if ttypetag(t1) != ttypetag(t2) { /* not the same variant? */
		if ttype(t1) != ttype(t2) || ttype(t1) != LUA_TNUMBER {
			return false /* only numbers can be equal with different variants */
		}
		/* two numbers with different variants: one of them is an
		   integer; if the other has no exact integer value they
		   cannot be equal, otherwise compare the integer values */
		var i1, i2 int64
		return tointegerns(t1, &i1) && tointegerns(t2, &i2) && i1 == i2
	}
	/* values have the same type and the same variant */
	switch ttypetag(t1) {
	case LUA_VNIL:
		return true
	case LUA_TBOOLEAN:
		return bvalue(t1) == bvalue(t2)
	case LUA_VNUMINT:
		return ivalue(t1) == ivalue(t2)
	case LUA_VNUMFLT:
		return fltvalue(t1) == fltvalue(t2)
	case LUA_VLIGHTUSERDATA:
		return pvalue(t1) == pvalue(t2)
	case LUA_VLCF: /* light C functions: identity */
		return gcvalue(t1) == gcvalue(t2)
	case LUA_VSHRSTR:
		return luaS_eqshrstr(tsvalue(t1), tsvalue(t2))
	case LUA_VLNGSTR:
		return luaS_eqlngstr(tsvalue(t1), tsvalue(t2))
	case LUA_VUSERDATA:
		if uvalue(t1) == uvalue(t2) {
			return true
		} else if L == nil {
			return false
		}
		tm = fasttm(L, uvalue(t1).metatable, TM_EQ)
		if tm == nil {
			tm = fasttm(L, uvalue(t2).metatable, TM_EQ)
		}
	case LUA_VTABLE:
		if hvalue(t1) == hvalue(t2) {
			return true
		} else if L == nil {
			return false
		}
		tm = fasttm(L, hvalue(t1).metatable, TM_EQ)
		if tm == nil {
			tm = fasttm(L, hvalue(t2).metatable, TM_EQ)
		}
	default:
		return gcvalue(t1) == gcvalue(t2)
	}
	if tm == nil { /* no TM? */
		return false /* objects are different */
	}
	luaT_callTMres(L, tm, t1, t2, L.top) /* call TM */
	return !isFalse(&L.stack[L.top])
}

// maxIntFitsF is MAXINTFITSF: 1 << NBM, with NBM the number of bits in
// the mantissa of a float (53 for an IEEE-754 double).
const maxIntFitsF = uint64(1) << 53

// lIntfitsf reports whether 'i' has an exact representation as a float
// (l_intfitsf in lvm.c). The addition is unsigned and wraps, exactly as
// in C, which is what turns the test into "is i in [-2^53, 2^53]".
func lIntfitsf(i int64) bool {
	return maxIntFitsF+uint64(i) <= 2*maxIntFitsF
}

// ltIntFloat is LTintfloat (lvm.c:281-293, PUC 5.3.4).
// When 'i' has an exact float representation, compare as floats.
// Otherwise range-test vs ±LUA_MININTEGER and cast(lua_Integer, f)
// (toward-zero). NOT the 5.4 LTintfloat that uses luaV_flttointeger.
func ltIntFloat(i int64, f float64) bool {
	if !lIntfitsf(i) {
		if f >= -float64(LUA_MININTEGER) { /* -minint == maxint + 1 */
			return true /* f >= maxint + 1 > i */
		} else if f > float64(LUA_MININTEGER) { /* minint < f <= maxint ? */
			return i < int64(f) /* compare them as integers */
		}
		/* f <= minint <= i (or 'f' is NaN) --> not(i < f) */
		return false
	}
	return float64(i) < f /* compare them as floats */
}

// leIntFloat is LEintfloat (lvm.c:300-312, PUC 5.3.4).
func leIntFloat(i int64, f float64) bool {
	if !lIntfitsf(i) {
		if f >= -float64(LUA_MININTEGER) { /* -minint == maxint + 1 */
			return true /* f >= maxint + 1 > i */
		} else if f >= float64(LUA_MININTEGER) { /* minint <= f <= maxint ? */
			return i <= int64(f) /* compare them as integers */
		}
		/* f < minint <= i (or 'f' is NaN) --> not(i <= f) */
		return false
	}
	return float64(i) <= f /* compare them as floats */
}

// ltFloatInt is the float<int side of LTnum (lvm.c:330-333): NaN→false,
// else !LEintfloat(i,f). 5.3.4 has no named LTfloatint (that is 5.4).
func ltFloatInt(f float64, i int64) bool {
	if numIsNaN(f) {
		return false
	}
	return !leIntFloat(i, f)
}

// leFloatInt is the float<=int side of LEnum (lvm.c:353-356): NaN→false,
// else !LTintfloat(i,f). 5.3.4 has no named LEfloatint (that is 5.4).
func leFloatInt(f float64, i int64) bool {
	if numIsNaN(f) {
		return false
	}
	return !ltIntFloat(i, f)
}

// ltNum is LTnum (lvm.c:318-335, PUC 5.3.4).
func ltNum(l, r *Value) bool {
	if ttisinteger(l) {
		li := ivalue(l)
		if ttisinteger(r) {
			return li < ivalue(r) /* both are integers */
		}
		return ltIntFloat(li, fltvalue(r)) /* l is int and r is float */
	}
	lf := fltvalue(l) /* l must be float */
	if ttisfloat(r) {
		return lf < fltvalue(r) /* both are float */
	} else if numIsNaN(lf) { /* r is int and l is float */
		return false /* NaN < i is always false */
	}
	/* without NaN, (l < r) <--> not(r <= l) */
	return !leIntFloat(ivalue(r), lf)
}

// leNum is LEnum (lvm.c:341-358, PUC 5.3.4).
func leNum(l, r *Value) bool {
	if ttisinteger(l) {
		li := ivalue(l)
		if ttisinteger(r) {
			return li <= ivalue(r) /* both are integers */
		}
		return leIntFloat(li, fltvalue(r)) /* l is int and r is float */
	}
	lf := fltvalue(l) /* l must be float */
	if ttisfloat(r) {
		return lf <= fltvalue(r) /* both are float */
	} else if numIsNaN(lf) { /* r is int and l is float */
		return false /* NaN <= i is always false */
	}
	/* without NaN, (l <= r) <--> not(r < l) */
	return !ltIntFloat(ivalue(r), lf)
}

// luaV_lessthan is the main operation 'l < r' (lvm.c 5.3.4).
func luaV_lessthan(L *State, l, r *Value) bool {
	if ttisnumber(l) && ttisnumber(r) {
		return ltNum(l, r)
	}
	if ttisstring(l) && ttisstring(r) {
		return luaV_strcmp(tsvalue(l).val, tsvalue(r).val) < 0
	}
	res := luaT_callorderTM(L, l, r, TM_LT)
	if res < 0 {
		luaG_ordererror(L, l, r)
	}
	return res > 0
}

// luaV_lessequal is the main operation 'l <= r' (lvm.c 5.3.4, LUA_COMPAT_LT_LE).
func luaV_lessequal(L *State, l, r *Value) bool {
	if ttisnumber(l) && ttisnumber(r) {
		return leNum(l, r)
	}
	if ttisstring(l) && ttisstring(r) {
		return luaV_strcmp(tsvalue(l).val, tsvalue(r).val) <= 0
	}
	res := luaT_callorderTM(L, l, r, TM_LE) /* try 'le' */
	if res >= 0 {
		return res > 0
	}
	/* try 'lt': !(r < l) for (l <= r) */
	L.ci.callstatus |= CIST_LEQ /* mark it is doing 'lt' for 'le' */
	res = luaT_callorderTM(L, r, l, TM_LT)
	L.ci.callstatus ^= CIST_LEQ /* clear mark */
	if res < 0 {
		luaG_ordererror(L, l, r)
	}
	return res == 0 /* result is negated */
}

// luaV_objlen is the main operation 'ra = #rb' (lvm.c).
func luaV_objlen(L *State, ra int, rb *Value) {
	var tm *Value
	switch ttypetag(rb) {
	case LUA_VTABLE:
		h := hvalue(rb)
		tm = fasttm(L, h.metatable, TM_LEN)
		if tm == nil {
			setivalue(&L.stack[ra], int64(luaH_getn(h)))
			return
		}
	case LUA_VSHRSTR, LUA_VLNGSTR:
		setivalue(&L.stack[ra], int64(len(tsvalue(rb).val)))
		return
	default:
		tm = luaT_gettmbyobj(L, rb, TM_LEN)
		if notm(tm) {
			luaG_typeerror(L, rb, "get length of")
			return
		}
	}
	luaT_callTMres(L, tm, rb, rb, ra)
}

func luaG_tracecall(L *State, ci *CallInfo) bool {
	/* 5.3.4: call hooks run from callhook inside luaD_precall, not here.
	   Keep trap on so line/count hooks still fire via luaG_traceexec. */
	ci.trap = true
	_ = L
	return true
}

// getFuncline returns the source line of an instruction (ldebug.c).
//
// deviation: Proto.lineinfo already holds absolute lines, so there is no
// baseline to walk back from.
func getFuncline(f *Proto, pc int) int {
	if f.lineinfo == nil || pc < 0 || pc >= len(f.lineinfo) {
		return -1 /* no debug information */
	}
	return int(f.lineinfo[pc])
}

// luaG_traceexec checks (and runs) the line and count hooks before an
// instruction (ldebug.c). It reports whether 'trap' must stay on.
func luaG_traceexec(L *State, ci *CallInfo, pc int) bool {
	mask := L.hookmask
	p := clLvalue(&L.stack[ci.funcIdx]).p
	if mask&(dbMaskLine|dbMaskCount) == 0 { /* no hooks? */
		ci.trap = false /* don't need to stop again */
		return false    /* turn off 'trap' */
	}
	pc++            /* reference is always next instruction */
	ci.savedpc = pc /* save 'pc' */
	counthook := false
	if mask&dbMaskCount != 0 {
		L.hookcount--
		counthook = L.hookcount == 0
	}
	if counthook {
		L.hookcount = L.basehookcount /* resethookcount(L) */
	} else if mask&dbMaskLine == 0 {
		return true /* no line hook and count != 0; nothing to be done */
	}
	if ci.callstatus&CIST_HOOKYIELD != 0 { /* hook yielded last time? */
		ci.callstatus &= ^uint16(CIST_HOOKYIELD) /* erase mark */
		return true
	}
	/* 5.3.4 has no IT column; open ops use top when B==0 (lopcodes.h notes). */
	prev := p.code[pc-1]
	usesTop := false
	switch prev.Op {
	case OP_CALL, OP_TAILCALL, OP_RETURN, OP_SETLIST, OP_VARARG:
		usesTop = prev.B == 0
	}
	if !usesTop { /* top not being used? */
		L.top = ci.top /* correct top */
	}
	if counthook {
		luaD_hook(L, hookCount, -1) /* call count hook */
	}
	if mask&dbMaskLine != 0 {
		/* 5.3.4 luaG_traceexec: C's oldpc is Instruction*; Go stores
		   pcRel(oldpc) (index of last traced insn). Conditions match C:
		   npc==0, jump back (npci<=oldpc), or line change. */
		npci := pc - 1 /* pcRel(ci->u.l.savedpc, p) */
		newline := getFuncline(p, npci)
		if npci == 0 || /* enter a new function */
			npci <= L.oldpc || /* jump back (loop) */
			newline != getFuncline(p, L.oldpc) { /* enter a new line */
			luaD_hook(L, hookLine, newline)
		}
		L.oldpc = npci
	}
	/* deviation: a hook cannot yield here, so the CIST_HOOKYIELD dance
	   that ends luaG_traceexec is not needed. */
	return true /* keep 'trap' on */
}

func numIsNaN(n float64) bool { return math.IsNaN(n) }
