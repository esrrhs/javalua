// Translation of lparser.h / lparser.c (PUC-Lua 5.3.4).
// Re-review WIP: see NOTES.md. createlabel absent; C hard limits restored.
package core

import "fmt"

const MAXVARS = 200

type expkind int

const (
	VVOID expkind = iota
	VNIL
	VTRUE
	VFALSE
	VK
	VKFLT
	VKINT
	VNONRELOC
	VLOCAL
	VUPVAL
	VINDEXED
	VJMP
	VRELOCABLE
	VCALL
	VVARARG
)

func vkisvar(k expkind) bool { return VLOCAL <= k && k <= VINDEXED }

// vkisinreg is from lparser.h; used by lcode.c asserts / discharge paths.
func vkisinreg(k expkind) bool { return k == VNONRELOC || k == VLOCAL }

// SHRT_MAX matches C <limits.h> short max used by luaM_growvector limits
// for locvars and labels/gotos in lparser.c.
const SHRT_MAX = 32767

type expdesc struct {
	k      expkind
	ival   int64
	nval   float64
	info   int
	indIdx int
	indT   int
	indVt  expkind // VLOCAL or VUPVAL
	t      int
	f      int
}

type Vardesc struct {
	idx int // register index on stack
}

type Labeldesc struct {
	name    *TString
	pc      int
	line    int
	nactvar int
}

type Labellist struct {
	arr []Labeldesc
	n   int
}

type Dyndata struct {
	actvar struct {
		arr []Vardesc
		n   int
	}
	gt    Labellist
	label Labellist
}

type BlockCnt struct {
	previous   *BlockCnt
	firstlabel int
	firstgoto  int
	nactvar    byte
	upval      bool
	isloop     bool
}

type FuncState struct {
	f          *Proto
	prev       *FuncState
	ls         *LexState
	bl         *BlockCnt
	pc         int
	lasttarget int
	jpc        int
	nk         int
	np         int
	firstlocal int
	nlocvars   int
	nactvar    int
	nups       int
	freereg    int
}

type ConsControl struct {
	v       expdesc
	t       *expdesc
	nh      int
	na      int
	tostore int
}

type LHS_assign struct {
	prev *LHS_assign
	v    expdesc
}

func bool2byte(b bool) byte {
	if b {
		return 1
	}
	return 0
}

// enterlevel matches 5.3.4 lparser.c: bump nCcalls and report
// "too many C levels" (a syntax-limit error), not the runtime
// "C stack overflow" from luaE_incCstack.
func enterlevel(ls *LexState) {
	ls.L.nCcalls++
	checklimit(ls.fs, int(ls.L.nCcalls), LUAI_MAXCCALLS, "C levels")
}
func leavelevel(ls *LexState) { ls.L.nCcalls-- }

func error_expected(ls *LexState, token int) {
	luaX_syntaxerror(ls, fmt.Sprintf("%s expected", luaX_token2str(ls, token)))
}

func errorlimit(fs *FuncState, limit int, what string) {
	line := fs.f.linedefined
	where := "main function"
	if line != 0 {
		where = fmt.Sprintf("function at line %d", line)
	}
	luaX_syntaxerror(fs.ls, fmt.Sprintf("too many %s (limit is %d) in %s", what, limit, where))
}

func checklimit(fs *FuncState, v, l int, what string) {
	if v > l {
		errorlimit(fs, l, what)
	}
}

func testnext(ls *LexState, c int) bool {
	if ls.t.token == c {
		luaX_next(ls)
		return true
	}
	return false
}

func check(ls *LexState, c int) {
	if ls.t.token != c {
		error_expected(ls, c)
	}
}

func checknext(ls *LexState, c int) {
	check(ls, c)
	luaX_next(ls)
}

func check_condition(ls *LexState, cond bool, msg string) {
	if !cond {
		luaX_syntaxerror(ls, msg)
	}
}

func check_match(ls *LexState, what, who, where int) {
	if !testnext(ls, what) {
		if where == ls.linenumber {
			error_expected(ls, what)
		} else {
			luaX_syntaxerror(ls, fmt.Sprintf("%s expected (to close %s at line %d)",
				luaX_token2str(ls, what), luaX_token2str(ls, who), where))
		}
	}
}

func str_checkname(ls *LexState) *TString {
	check(ls, TK_NAME)
	ts := ls.t.seminfo.ts
	luaX_next(ls)
	return ts
}

func init_exp(e *expdesc, k expkind, i int) {
	e.f = NO_JUMP
	e.t = NO_JUMP
	e.k = k
	e.info = i
}

func codestring(ls *LexState, e *expdesc, s *TString) {
	init_exp(e, VK, luaK_stringK(ls.fs, s))
}

func checkname(ls *LexState, e *expdesc) {
	codestring(ls, e, str_checkname(ls))
}

func registerlocalvar(ls *LexState, varname *TString) int {
	fs := ls.fs
	f := fs.f
	/* C: luaM_growvector(..., SHRT_MAX, "local variables"); no barrier (Go GC) */
	checklimit(fs, fs.nlocvars+1, SHRT_MAX, "local variables")
	if len(f.locvars) <= fs.nlocvars {
		f.locvars = append(f.locvars, LocVar{})
	}
	f.locvars[fs.nlocvars].varname = varname
	fs.nlocvars++
	f.sizelocvars = fs.nlocvars
	return fs.nlocvars - 1
}

func new_localvar(ls *LexState, name *TString) {
	fs := ls.fs
	dyd := ls.dyd
	reg := registerlocalvar(ls, name)
	checklimit(fs, dyd.actvar.n+1-fs.firstlocal, MAXVARS, "local variables")
	/* C: luaM_growvector(..., MAX_INT, "local variables") on dyd->actvar */
	checklimit(fs, dyd.actvar.n+1, maxInt, "local variables")
	if len(dyd.actvar.arr) <= dyd.actvar.n {
		dyd.actvar.arr = append(dyd.actvar.arr, Vardesc{})
	}
	dyd.actvar.arr[dyd.actvar.n].idx = reg
	dyd.actvar.n++
}

func new_localvarliteral(ls *LexState, name string) {
	new_localvar(ls, luaX_newstring(ls, name))
}

func getlocvar(fs *FuncState, i int) *LocVar {
	idx := fs.ls.dyd.actvar.arr[fs.firstlocal+i].idx
	return &fs.f.locvars[idx]
}

func adjustlocalvars(ls *LexState, nvars int) {
	fs := ls.fs
	fs.nactvar += nvars
	for i := 0; i < nvars; i++ {
		getlocvar(fs, fs.nactvar-1-i).startpc = fs.pc
	}
}

func removevars(fs *FuncState, tolevel int) {
	fs.ls.dyd.actvar.n -= fs.nactvar - tolevel
	for fs.nactvar > tolevel {
		fs.nactvar--
		getlocvar(fs, fs.nactvar).endpc = fs.pc
	}
}

func searchupvalue(fs *FuncState, name *TString) int {
	up := fs.f.upvalues
	for i := 0; i < fs.nups; i++ {
		if up[i].name != nil && up[i].name.val == name.val {
			return i
		}
	}
	return -1
}

func newupvalue(fs *FuncState, name *TString, v *expdesc) int {
	f := fs.f
	checklimit(fs, fs.nups+1, MAXUPVAL, "upvalues")
	if len(f.upvalues) <= fs.nups {
		f.upvalues = append(f.upvalues, Upvaldesc{})
	}
	f.upvalues[fs.nups].instack = bool2byte(v.k == VLOCAL)
	f.upvalues[fs.nups].idx = byte(v.info)
	f.upvalues[fs.nups].name = name
	fs.nups++
	f.sizeupvalues = fs.nups
	return fs.nups - 1
}

func searchvar(fs *FuncState, n *TString) int {
	for i := fs.nactvar - 1; i >= 0; i-- {
		if n.val == getlocvar(fs, i).varname.val {
			return i
		}
	}
	return -1
}

func markupval(fs *FuncState, level int) {
	bl := fs.bl
	for bl.nactvar > byte(level) {
		bl = bl.previous
	}
	bl.upval = true
}

func singlevaraux(fs *FuncState, n *TString, var_ *expdesc, base int) expkind {
	if fs == nil {
		init_exp(var_, VVOID, 0)
		return VVOID
	}
	v := searchvar(fs, n)
	if v >= 0 {
		init_exp(var_, VLOCAL, v)
		if base == 0 {
			markupval(fs, v)
		}
		return VLOCAL
	}
	idx := searchupvalue(fs, n)
	if idx < 0 {
		if singlevaraux(fs.prev, n, var_, 0) == VVOID {
			return VVOID
		}
		idx = newupvalue(fs, n, var_)
	}
	init_exp(var_, VUPVAL, idx)
	return VUPVAL
}

func singlevar(ls *LexState, var_ *expdesc) {
	varname := str_checkname(ls)
	fs := ls.fs
	singlevaraux(fs, varname, var_, 1)
	if var_.k == VVOID { /* global name? */
		var key expdesc
		singlevaraux(fs, ls.envn, var_, 1) /* get environment variable */
		codestring(ls, &key, varname)      /* key is variable name */
		luaK_indexed(fs, var_, &key)       /* env[varname] */
	}
}

func adjust_assign(ls *LexState, nvars, nexps int, e *expdesc) {
	fs := ls.fs
	extra := nvars - nexps
	if e.k == VCALL || e.k == VVARARG {
		extra++
		if extra < 0 {
			extra = 0
		}
		luaK_setreturns(fs, e, extra)
		if extra > 1 {
			luaK_reserveregs(fs, extra-1)
		}
	} else {
		if e.k != VVOID {
			luaK_exp2nextreg(fs, e)
		}
		if extra > 0 {
			reg := fs.freereg
			luaK_reserveregs(fs, extra)
			luaK_nil(fs, reg, extra)
		}
	}
	if nexps > nvars {
		ls.fs.freereg -= nexps - nvars
	}
}

func enterblock(fs *FuncState, bl *BlockCnt, isloop bool) {
	bl.isloop = isloop
	bl.nactvar = byte(fs.nactvar)
	bl.firstlabel = fs.ls.dyd.label.n
	bl.firstgoto = fs.ls.dyd.gt.n
	bl.upval = false
	bl.previous = fs.bl
	fs.bl = bl
}

func breaklabel(ls *LexState) {
	/* C: luaS_new(ls->L, "break"); newlabelentry; findgotos — no createlabel. */
	n := luaS_new(ls.L, "break")
	l := newlabelentry(ls, &ls.dyd.label, n, 0, ls.fs.pc)
	findgotos(ls, &ls.dyd.label.arr[l])
}

func undefgoto(ls *LexState, gt *Labeldesc) {
	/* C: isreserved(gt->name) — reserved goto label can only be "break".
	   Go: reservedTokens map (llex; TString.extra not used for reserved). */
	var msg string
	if _, ok := isreservedToken(gt.name); ok {
		msg = fmt.Sprintf("<%s> at line %d not inside a loop", gt.name.val, gt.line)
	} else {
		msg = fmt.Sprintf("no visible label '%s' for <goto> at line %d", gt.name.val, gt.line)
	}
	semerror(ls, msg)
}

func semerror(ls *LexState, msg string) {
	ls.t.token = 0
	luaX_syntaxerror(ls, msg)
}

func movegotosout(fs *FuncState, bl *BlockCnt) {
	i := bl.firstgoto
	gt := &fs.ls.dyd.gt
	for i < gt.n {
		g := &gt.arr[i]
		if g.nactvar > int(bl.nactvar) {
			if bl.upval {
				luaK_patchclose(fs, g.pc, int(bl.nactvar))
			}
			g.nactvar = int(bl.nactvar)
		}
		if !findlabel(fs.ls, i) {
			i++
		}
	}
}

func leaveblock(fs *FuncState) {
	bl := fs.bl
	ls := fs.ls
	if bl.previous != nil && bl.upval {
		j := luaK_jump(fs)
		luaK_patchclose(fs, j, int(bl.nactvar))
		luaK_patchtohere(fs, j)
	}
	if bl.isloop {
		breaklabel(ls)
	}
	fs.bl = bl.previous
	removevars(fs, int(bl.nactvar))
	fs.freereg = fs.nactvar
	ls.dyd.label.n = bl.firstlabel
	if bl.previous != nil {
		movegotosout(fs, bl)
	} else if bl.firstgoto < ls.dyd.gt.n {
		undefgoto(ls, &ls.dyd.gt.arr[bl.firstgoto])
	}
}

func closegoto(ls *LexState, g int, label *Labeldesc) {
	fs := ls.fs
	gl := &ls.dyd.gt
	gt := &gl.arr[g]
	check_condition(ls, gt.name.val == label.name.val, "should be same name")
	if gt.nactvar < label.nactvar {
		vname := getlocvar(fs, gt.nactvar).varname
		semerror(ls, fmt.Sprintf("<goto %s> at line %d jumps into the scope of local '%s'",
			gt.name.val, gt.line, vname.val))
	}
	luaK_patchlist(fs, gt.pc, label.pc)
	copy(gl.arr[g:], gl.arr[g+1:gl.n])
	gl.n--
}

func findlabel(ls *LexState, g int) bool {
	bl := ls.fs.bl
	gt := &ls.dyd.gt.arr[g]
	for i := bl.firstlabel; i < ls.dyd.label.n; i++ {
		lb := &ls.dyd.label.arr[i]
		if lb.name.val == gt.name.val {
			if gt.nactvar > lb.nactvar && (bl.upval || ls.dyd.label.n > bl.firstlabel) {
				luaK_patchclose(ls.fs, gt.pc, lb.nactvar)
			}
			closegoto(ls, g, lb)
			return true
		}
	}
	return false
}

func newlabelentry(ls *LexState, l *Labellist, name *TString, line, pc int) int {
	n := l.n
	/* C: luaM_growvector(..., SHRT_MAX, "labels/gotos") */
	checklimit(ls.fs, n+1, SHRT_MAX, "labels/gotos")
	if len(l.arr) <= n {
		l.arr = append(l.arr, Labeldesc{})
	}
	l.arr[n].name = name
	l.arr[n].line = line
	l.arr[n].nactvar = ls.fs.nactvar
	l.arr[n].pc = pc
	l.n = n + 1
	return n
}

func findgotos(ls *LexState, lb *Labeldesc) {
	gl := &ls.dyd.gt
	i := ls.fs.bl.firstgoto
	for i < gl.n {
		if gl.arr[i].name.val == lb.name.val {
			closegoto(ls, i, lb)
		} else {
			i++
		}
	}
}

func checkrepeated(fs *FuncState, ll *Labellist, label *TString) {
	for i := fs.bl.firstlabel; i < ll.n; i++ {
		if label.val == ll.arr[i].name.val {
			semerror(fs.ls, fmt.Sprintf("label '%s' already defined on line %d",
				label.val, ll.arr[i].line))
		}
	}
}

func skipnoopstat(ls *LexState) {
	for ls.t.token == ';' || ls.t.token == TK_DBCOLON {
		statement(ls)
	}
}

func open_func(ls *LexState, fs *FuncState, bl *BlockCnt) {
	fs.prev = ls.fs
	fs.ls = ls
	ls.fs = fs
	fs.pc = 0
	fs.lasttarget = 0
	fs.jpc = NO_JUMP
	fs.freereg = 0
	fs.nk = 0
	fs.np = 0
	fs.nups = 0
	fs.nlocvars = 0
	fs.nactvar = 0
	fs.firstlocal = ls.dyd.actvar.n
	fs.bl = nil
	f := fs.f
	f.source = ls.source
	f.maxstacksize = 2
	enterblock(fs, bl, false)
}

func close_func(ls *LexState) {
	fs := ls.fs
	f := fs.f
	luaK_ret(fs, 0, 0) /* final return */
	leaveblock(fs)
	/* C: luaM_reallocvector shrink to final counts; Go truncates slices.
	   No luaC_checkGC — Go GC. */
	f.code = f.code[:fs.pc]
	f.lineinfo = f.lineinfo[:fs.pc]
	f.k = f.k[:fs.nk]
	f.p = f.p[:fs.np]
	f.locvars = f.locvars[:fs.nlocvars]
	f.upvalues = f.upvalues[:fs.nups]
	f.sizecode = fs.pc
	f.sizelineinfo = fs.pc
	f.sizek = fs.nk
	f.sizep = fs.np
	f.sizelocvars = fs.nlocvars
	f.sizeupvalues = fs.nups
	ls.fs = fs.prev
}

// --- grammar rules (subset faithfully matching 5.3.4) ---

func yindex(ls *LexState, v *expdesc) {
	luaX_next(ls)
	expr(ls, v)
	luaK_exp2val(ls.fs, v)
	checknext(ls, ']')
}

func recfield(ls *LexState, cc *ConsControl) {
	fs := ls.fs
	reg := ls.fs.freereg
	var key, val expdesc
	if ls.t.token == TK_NAME {
		checklimit(fs, cc.nh, maxInt, "items in a constructor") /* C: MAX_INT */
		checkname(ls, &key)
	} else {
		yindex(ls, &key)
	}
	cc.nh++
	checknext(ls, '=')
	rkkey := luaK_exp2RK(fs, &key)
	expr(ls, &val)
	luaK_codeABC(fs, OP_SETTABLE, cc.t.info, rkkey, luaK_exp2RK(fs, &val))
	fs.freereg = reg
}

func closelistfield(fs *FuncState, cc *ConsControl) {
	if cc.v.k == VVOID {
		return
	}
	luaK_exp2nextreg(fs, &cc.v)
	cc.v.k = VVOID
	if cc.tostore == LFIELDS_PER_FLUSH {
		luaK_setlist(fs, cc.t.info, cc.na, cc.tostore)
		cc.tostore = 0
	}
}

func lastlistfield(fs *FuncState, cc *ConsControl) {
	if cc.tostore == 0 {
		return
	}
	if cc.v.k == VCALL || cc.v.k == VVARARG {
		luaK_setmultret(fs, &cc.v)
		luaK_setlist(fs, cc.t.info, cc.na, LUA_MULTRET)
		cc.na--
	} else {
		if cc.v.k != VVOID {
			luaK_exp2nextreg(fs, &cc.v)
		}
		luaK_setlist(fs, cc.t.info, cc.na, cc.tostore)
	}
}

func listfield(ls *LexState, cc *ConsControl) {
	expr(ls, &cc.v)
	checklimit(ls.fs, cc.na, maxInt, "items in a constructor") /* C: MAX_INT */
	cc.na++
	cc.tostore++
}

func field(ls *LexState, cc *ConsControl) {
	switch ls.t.token {
	case TK_NAME:
		luaX_lookahead(ls)
		if ls.lookahead.token != '=' {
			listfield(ls, cc)
		} else {
			recfield(ls, cc)
		}
	case '[':
		recfield(ls, cc)
	default:
		listfield(ls, cc)
	}
}

func constructor(ls *LexState, t *expdesc) {
	fs := ls.fs
	line := ls.linenumber
	pc := luaK_codeABC(fs, OP_NEWTABLE, 0, 0, 0)
	var cc ConsControl
	cc.tostore = 0
	cc.nh = 0
	cc.na = 0
	cc.t = t
	init_exp(t, VRELOCABLE, pc)
	init_exp(&cc.v, VVOID, 0)
	luaK_exp2nextreg(ls.fs, t)
	checknext(ls, '{')
	for {
		if ls.t.token == '}' {
			break
		}
		closelistfield(fs, &cc)
		field(ls, &cc)
		if !(testnext(ls, ',') || testnext(ls, ';')) {
			break
		}
	}
	check_match(ls, '}', '{', line)
	lastlistfield(fs, &cc)
	fs.f.code[pc].B = luaO_int2fb(uint32(cc.na))
	fs.f.code[pc].C = luaO_int2fb(uint32(cc.nh))
}

func parlist(ls *LexState) {
	/* parlist -> [ param { ',' param } ] */
	fs := ls.fs
	f := fs.f
	nparams := 0
	f.isVararg = 0
	if ls.t.token != ')' { /* is 'parlist' not empty? */
		for {
			switch ls.t.token {
			case TK_NAME: /* param -> NAME */
				new_localvar(ls, str_checkname(ls))
				nparams++
			case TK_DOTS: /* param -> '...' */
				luaX_next(ls)
				f.isVararg = 1 /* declared vararg */
			default:
				luaX_syntaxerror(ls, "<name> or '...' expected")
			}
			if f.isVararg != 0 || !testnext(ls, ',') {
				break
			}
		}
	}
	adjustlocalvars(ls, nparams)
	f.numparams = byte(fs.nactvar)
	luaK_reserveregs(fs, fs.nactvar) /* reserve register for parameters */
}

/* addprototype adds a new prototype into the parent's list (lparser.c). */
func addprototype(ls *LexState) *Proto {
	fs := ls.fs
	f := fs.f
	/* C: luaM_growvector(..., MAXARG_Bx, "functions"); nil-fill omitted (append) */
	checklimit(fs, fs.np+1, MAXARG_Bx, "functions")
	if fs.np >= len(f.p) {
		f.p = append(f.p, nil)
	}
	clp := luaF_newproto(ls.L)
	f.p[fs.np] = clp
	fs.np++
	f.sizep = fs.np
	/* no luaC_objbarrier — Go GC */
	return clp
}

func body(ls *LexState, e *expdesc, ismethod bool, line int) {
	var new_fs FuncState
	var bl BlockCnt
	new_fs.f = addprototype(ls)
	new_fs.f.linedefined = line
	open_func(ls, &new_fs, &bl)
	checknext(ls, '(')
	if ismethod {
		new_localvarliteral(ls, "self")
		adjustlocalvars(ls, 1)
	}
	parlist(ls)
	checknext(ls, ')')
	statlist(ls)
	new_fs.f.lastlinedefined = ls.linenumber
	check_match(ls, TK_END, TK_FUNCTION, line)
	codeclosure(ls, e)
	close_func(ls)
}

/* codeclosure emits OP_CLOSURE in the parent (lparser.c). */
func codeclosure(ls *LexState, v *expdesc) {
	fs := ls.fs.prev
	init_exp(v, VRELOCABLE, luaK_codeABx(fs, OP_CLOSURE, 0, fs.np-1))
	luaK_exp2nextreg(fs, v) /* fix it at the last register */
}

func explist(ls *LexState, v *expdesc) int {
	n := 1
	expr(ls, v)
	for testnext(ls, ',') {
		luaK_exp2nextreg(ls.fs, v)
		expr(ls, v)
		n++
	}
	return n
}

func funcargs(ls *LexState, f *expdesc, line int) {
	fs := ls.fs
	var args expdesc
	switch ls.t.token {
	case '(':
		luaX_next(ls)
		if ls.t.token == ')' {
			args.k = VVOID
		} else {
			explist(ls, &args)
			luaK_setmultret(fs, &args)
		}
		check_match(ls, ')', '(', line)
	case '{':
		constructor(ls, &args)
	case TK_STRING:
		codestring(ls, &args, ls.t.seminfo.ts)
		luaX_next(ls)
	default:
		luaX_syntaxerror(ls, "function arguments expected")
	}
	base := f.info
	var nparams int
	if args.k == VCALL || args.k == VVARARG {
		nparams = LUA_MULTRET
	} else {
		if args.k != VVOID {
			luaK_exp2nextreg(fs, &args)
		}
		nparams = fs.freereg - (base + 1)
	}
	init_exp(f, VCALL, luaK_codeABC(fs, OP_CALL, base, nparams+1, 2))
	luaK_fixline(fs, line)
	fs.freereg = base + 1
}

func primaryexp(ls *LexState, v *expdesc) {
	switch ls.t.token {
	case '(':
		line := ls.linenumber
		luaX_next(ls)
		expr(ls, v)
		check_match(ls, ')', '(', line)
		luaK_dischargevars(ls.fs, v)
	case TK_NAME:
		singlevar(ls, v)
	default:
		luaX_syntaxerror(ls, "unexpected symbol")
	}
}

func fieldsel(ls *LexState, v *expdesc) {
	/* fieldsel -> ['.' | ':'] NAME */
	fs := ls.fs
	var key expdesc
	luaK_exp2anyregup(fs, v)
	luaX_next(ls) /* skip the dot or colon */
	checkname(ls, &key)
	luaK_indexed(fs, v, &key)
}

func suffixedexp(ls *LexState, v *expdesc) {
	fs := ls.fs
	line := ls.linenumber
	primaryexp(ls, v)
	for {
		switch ls.t.token {
		case '.': /* fieldsel */
			fieldsel(ls, v)
		case '[':
			var key expdesc
			luaK_exp2anyregup(fs, v)
			yindex(ls, &key)
			luaK_indexed(fs, v, &key)
		case ':':
			var key expdesc
			luaX_next(ls)
			checkname(ls, &key)
			luaK_self(fs, v, &key)
			funcargs(ls, v, line)
		case '(', TK_STRING, '{':
			luaK_exp2nextreg(fs, v)
			funcargs(ls, v, line)
		default:
			return
		}
	}
}

func simpleexp(ls *LexState, v *expdesc) {
	switch ls.t.token {
	case TK_FLT:
		init_exp(v, VKFLT, 0)
		v.nval = ls.t.seminfo.r
	case TK_INT:
		init_exp(v, VKINT, 0)
		v.ival = ls.t.seminfo.i
	case TK_STRING:
		codestring(ls, v, ls.t.seminfo.ts)
	case TK_NIL:
		init_exp(v, VNIL, 0)
	case TK_TRUE:
		init_exp(v, VTRUE, 0)
	case TK_FALSE:
		init_exp(v, VFALSE, 0)
	case TK_DOTS:
		check_condition(ls, ls.fs.f.isVararg != 0, "cannot use '...' outside a vararg function")
		init_exp(v, VVARARG, luaK_codeABC(ls.fs, OP_VARARG, 0, 1, 0))
	case '{':
		constructor(ls, v)
		return
	case TK_FUNCTION:
		luaX_next(ls)
		body(ls, v, false, ls.linenumber)
		return
	default:
		suffixedexp(ls, v)
		return
	}
	luaX_next(ls)
}

func getunopr(op int) UnOpr {
	switch op {
	case TK_NOT:
		return OPR_NOT
	case '-':
		return OPR_MINUS
	case '~':
		return OPR_BNOT
	case '#':
		return OPR_LEN
	}
	return OPR_NOUNOPR
}

func getbinopr(op int) BinOpr {
	switch op {
	case '+':
		return OPR_ADD
	case '-':
		return OPR_SUB
	case '*':
		return OPR_MUL
	case '%':
		return OPR_MOD
	case '^':
		return OPR_POW
	case '/':
		return OPR_DIV
	case TK_IDIV:
		return OPR_IDIV
	case '&':
		return OPR_BAND
	case '|':
		return OPR_BOR
	case '~':
		return OPR_BXOR
	case TK_SHL:
		return OPR_SHL
	case TK_SHR:
		return OPR_SHR
	case TK_CONCAT:
		return OPR_CONCAT
	case TK_NE:
		return OPR_NE
	case TK_EQ:
		return OPR_EQ
	case '<':
		return OPR_LT
	case TK_LE:
		return OPR_LE
	case '>':
		return OPR_GT
	case TK_GE:
		return OPR_GE
	case TK_AND:
		return OPR_AND
	case TK_OR:
		return OPR_OR
	}
	return OPR_NOBINOPR
}

type priority_ struct{ left, right byte }

var priority = [OPR_NOBINOPR]priority_{
	{10, 10}, {10, 10}, {11, 11}, {11, 11}, {14, 13},
	{11, 11}, {11, 11},
	{6, 6}, {4, 4}, {5, 5},
	{7, 7}, {7, 7},
	{9, 8},
	{3, 3}, {3, 3}, {3, 3},
	{3, 3}, {3, 3}, {3, 3},
	{2, 2}, {1, 1},
}

const UNARY_PRIORITY = 12

func subexpr(ls *LexState, v *expdesc, limit int) BinOpr {
	enterlevel(ls)
	uop := getunopr(ls.t.token)
	if uop != OPR_NOUNOPR {
		line := ls.linenumber
		luaX_next(ls)
		subexpr(ls, v, UNARY_PRIORITY)
		luaK_prefix(ls.fs, uop, v, line)
	} else {
		simpleexp(ls, v)
	}
	op := getbinopr(ls.t.token)
	for op != OPR_NOBINOPR && int(priority[op].left) > limit {
		var v2 expdesc
		line := ls.linenumber
		luaX_next(ls)
		luaK_infix(ls.fs, op, v)
		nextop := subexpr(ls, &v2, int(priority[op].right))
		luaK_posfix(ls.fs, op, v, &v2, line)
		op = nextop
	}
	leavelevel(ls)
	return op
}

func expr(ls *LexState, v *expdesc) { subexpr(ls, v, 0) }

func block(ls *LexState) {
	fs := ls.fs
	var bl BlockCnt
	enterblock(fs, &bl, false)
	statlist(ls)
	leaveblock(fs)
}

func check_conflict(ls *LexState, lh *LHS_assign, v *expdesc) {
	fs := ls.fs
	extra := fs.freereg
	conflict := false
	for lh != nil {
		if lh.v.k == VINDEXED {
			if lh.v.indVt == v.k && lh.v.indT == v.info {
				conflict = true
				lh.v.indVt = VLOCAL
				lh.v.indT = extra
			}
			if v.k == VLOCAL && lh.v.indIdx == v.info {
				conflict = true
				lh.v.indIdx = extra
			}
		}
		lh = lh.prev
	}
	if conflict {
		if v.k == VLOCAL {
			luaK_codeABC(fs, OP_MOVE, extra, v.info, 0)
		} else {
			luaK_codeABC(fs, OP_GETUPVAL, extra, v.info, 0)
		}
		luaK_reserveregs(fs, 1)
	}
}

func assignment(ls *LexState, lh *LHS_assign, nvars int) {
	var e expdesc
	check_condition(ls, vkisvar(lh.v.k), "syntax error")
	if testnext(ls, ',') {
		var nv LHS_assign
		nv.prev = lh
		suffixedexp(ls, &nv.v)
		if nv.v.k != VINDEXED {
			check_conflict(ls, lh, &nv.v)
		}
		checklimit(ls.fs, nvars+int(ls.L.nCcalls), LUAI_MAXCCALLS, "C levels")
		assignment(ls, &nv, nvars+1)
	} else {
		checknext(ls, '=')
		nexps := explist(ls, &e)
		if nexps != nvars {
			adjust_assign(ls, nvars, nexps, &e)
		} else {
			luaK_setoneret(ls.fs, &e)
			luaK_storevar(ls.fs, &lh.v, &e)
			return
		}
	}
	init_exp(&e, VNONRELOC, ls.fs.freereg-1)
	luaK_storevar(ls.fs, &lh.v, &e)
}

func cond(ls *LexState) int {
	var v expdesc
	expr(ls, &v)
	if v.k == VNIL {
		v.k = VFALSE
	}
	luaK_goiftrue(ls.fs, &v)
	return v.f
}

func gotostat(ls *LexState, pc int) {
	var name *TString
	line := ls.linenumber
	if testnext(ls, TK_GOTO) {
		name = str_checkname(ls)
	} else {
		luaX_next(ls) /* skip break */
		name = luaS_new(ls.L, "break")
	}
	g := newlabelentry(ls, &ls.dyd.gt, name, line, pc)
	findlabel(ls, g)
}

func labelstat(ls *LexState, name *TString, line int) {
	fs := ls.fs
	ll := &ls.dyd.label
	checkrepeated(fs, ll, name)
	checknext(ls, TK_DBCOLON)
	l := newlabelentry(ls, ll, name, line, luaK_getlabel(fs))
	skipnoopstat(ls)
	if block_follow(ls, false) {
		ll.arr[l].nactvar = int(fs.bl.nactvar)
	}
	findgotos(ls, &ll.arr[l])
}

func whilestat(ls *LexState, line int) {
	fs := ls.fs
	var bl BlockCnt
	luaX_next(ls)
	whileinit := luaK_getlabel(fs)
	condexit := cond(ls)
	enterblock(fs, &bl, true)
	checknext(ls, TK_DO)
	block(ls)
	luaK_jumpto(fs, whileinit)
	check_match(ls, TK_END, TK_WHILE, line)
	leaveblock(fs)
	luaK_patchtohere(fs, condexit)
}

func repeatstat(ls *LexState, line int) {
	fs := ls.fs
	repeat_init := luaK_getlabel(fs)
	var bl1, bl2 BlockCnt
	enterblock(fs, &bl1, true)
	enterblock(fs, &bl2, false)
	luaX_next(ls)
	statlist(ls)
	check_match(ls, TK_UNTIL, TK_REPEAT, line)
	condexit := cond(ls)
	if bl2.upval {
		luaK_patchclose(fs, condexit, int(bl2.nactvar))
	}
	leaveblock(fs)
	luaK_patchlist(fs, condexit, repeat_init)
	leaveblock(fs)
}

func exp1(ls *LexState) int {
	var e expdesc
	expr(ls, &e)
	luaK_exp2nextreg(ls.fs, &e)
	return e.info
}

func forbody(ls *LexState, base, line, nvars int, isnum bool) {
	var bl BlockCnt
	fs := ls.fs
	adjustlocalvars(ls, 3)
	checknext(ls, TK_DO)
	var prep int
	if isnum {
		prep = codeAsBx(fs, OP_FORPREP, base, NO_JUMP)
	} else {
		prep = luaK_jump(fs)
	}
	enterblock(fs, &bl, false)
	adjustlocalvars(ls, nvars)
	luaK_reserveregs(fs, nvars)
	block(ls)
	leaveblock(fs)
	luaK_patchtohere(fs, prep)
	var endfor int
	if isnum {
		endfor = codeAsBx(fs, OP_FORLOOP, base, NO_JUMP)
	} else {
		luaK_codeABC(fs, OP_TFORCALL, base, 0, nvars)
		luaK_fixline(fs, line)
		endfor = codeAsBx(fs, OP_TFORLOOP, base+2, NO_JUMP)
	}
	luaK_patchlist(fs, endfor, prep+1)
	luaK_fixline(fs, line)
}

func fornum(ls *LexState, varname *TString, line int) {
	fs := ls.fs
	base := fs.freereg
	new_localvarliteral(ls, "(for index)")
	new_localvarliteral(ls, "(for limit)")
	new_localvarliteral(ls, "(for step)")
	new_localvar(ls, varname)
	checknext(ls, '=')
	exp1(ls)
	checknext(ls, ',')
	exp1(ls)
	if testnext(ls, ',') {
		exp1(ls)
	} else {
		luaK_codek(fs, fs.freereg, luaK_intK(fs, 1))
		luaK_reserveregs(fs, 1)
	}
	forbody(ls, base, line, 1, true)
}

func forlist(ls *LexState, indexname *TString) {
	fs := ls.fs
	var e expdesc
	nvars := 4
	base := fs.freereg
	new_localvarliteral(ls, "(for generator)")
	new_localvarliteral(ls, "(for state)")
	new_localvarliteral(ls, "(for control)")
	new_localvar(ls, indexname)
	for testnext(ls, ',') {
		new_localvar(ls, str_checkname(ls))
		nvars++
	}
	checknext(ls, TK_IN)
	line := ls.linenumber
	adjust_assign(ls, 3, explist(ls, &e), &e)
	luaK_checkstack(fs, 3)
	forbody(ls, base, line, nvars-3, false)
}

func forstat(ls *LexState, line int) {
	fs := ls.fs
	var bl BlockCnt
	enterblock(fs, &bl, true)
	luaX_next(ls)
	varname := str_checkname(ls)
	switch ls.t.token {
	case '=':
		fornum(ls, varname, line)
	case ',', TK_IN:
		forlist(ls, varname)
	default:
		luaX_syntaxerror(ls, "'=' or 'in' expected")
	}
	check_match(ls, TK_END, TK_FOR, line)
	leaveblock(fs)
}

func test_then_block(ls *LexState, escapelist *int) {
	/* test_then_block -> [IF | ELSEIF] cond THEN block (PUC-Lua 5.3.4) */
	var bl BlockCnt
	fs := ls.fs
	var v expdesc
	var jf int    /* instruction to skip 'then' code (if condition is false) */
	luaX_next(ls) /* skip IF or ELSEIF */
	expr(ls, &v)  /* read condition */
	checknext(ls, TK_THEN)
	if ls.t.token == TK_GOTO || ls.t.token == TK_BREAK {
		luaK_goiffalse(ls.fs, &v)    /* will jump to label if condition is true */
		enterblock(fs, &bl, false)   /* must enter block before 'goto' */
		gotostat(ls, v.t)            /* handle goto/break */
		skipnoopstat(ls)             /* skip other no-op statements */
		if block_follow(ls, false) { /* 'goto' is the entire block? */
			leaveblock(fs)
			return /* and that is it */
		}
		/* must skip over 'then' part if condition is false */
		jf = luaK_jump(fs)
	} else { /* regular case (not goto/break) */
		luaK_goiftrue(ls.fs, &v) /* skip over block if condition is false */
		enterblock(fs, &bl, false)
		jf = v.f
	}
	statlist(ls) /* 'then' part */
	leaveblock(fs)
	if ls.t.token == TK_ELSE || ls.t.token == TK_ELSEIF { /* followed by 'else'/'elseif'? */
		luaK_concat(fs, escapelist, luaK_jump(fs)) /* must jump over it */
	}
	luaK_patchtohere(fs, jf)
}

func ifstat(ls *LexState, line int) {
	escapelist := NO_JUMP
	test_then_block(ls, &escapelist)
	for ls.t.token == TK_ELSEIF {
		test_then_block(ls, &escapelist)
	}
	if testnext(ls, TK_ELSE) {
		block(ls)
	}
	check_match(ls, TK_END, TK_IF, line)
	luaK_patchtohere(ls.fs, escapelist)
}

func localfunc(ls *LexState) {
	var b expdesc
	fs := ls.fs
	new_localvar(ls, str_checkname(ls))
	adjustlocalvars(ls, 1)
	body(ls, &b, false, ls.linenumber)
	getlocvar(fs, b.info).startpc = fs.pc
}

func localstat(ls *LexState) {
	nvars := 0
	nexps := 0
	var e expdesc
	for {
		new_localvar(ls, str_checkname(ls))
		nvars++
		if !testnext(ls, ',') {
			break
		}
	}
	if testnext(ls, '=') {
		nexps = explist(ls, &e)
	} else {
		e.k = VVOID
		nexps = 0
	}
	adjust_assign(ls, nvars, nexps, &e)
	adjustlocalvars(ls, nvars)
}

func funcname(ls *LexState, v *expdesc) bool {
	/* funcname -> NAME {fieldsel} [':' NAME] */
	ismethod := false
	singlevar(ls, v)
	for ls.t.token == '.' {
		fieldsel(ls, v)
	}
	if ls.t.token == ':' {
		ismethod = true
		fieldsel(ls, v)
	}
	return ismethod
}

func funcstat(ls *LexState, line int) {
	var v, b expdesc
	luaX_next(ls)
	ismethod := funcname(ls, &v)
	body(ls, &b, ismethod, line)
	luaK_storevar(ls.fs, &v, &b)
	luaK_fixline(ls.fs, line)
}

func exprstat(ls *LexState) {
	fs := ls.fs
	var v LHS_assign
	suffixedexp(ls, &v.v)
	if ls.t.token == '=' || ls.t.token == ',' {
		v.prev = nil
		assignment(ls, &v, 1)
	} else {
		check_condition(ls, v.v.k == VCALL, "syntax error")
		getinstruction(fs, &v.v).C = 1
	}
}

func retstat(ls *LexState) {
	fs := ls.fs
	var e expdesc
	first, nret := 0, 0
	if block_follow(ls, true) || ls.t.token == ';' {
		first, nret = 0, 0
	} else {
		nret = explist(ls, &e)
		if e.k == VCALL || e.k == VVARARG {
			luaK_setmultret(fs, &e)
			if e.k == VCALL && nret == 1 {
				getinstruction(fs, &e).Op = OP_TAILCALL
			}
			first = fs.nactvar
			nret = LUA_MULTRET
		} else {
			if nret == 1 {
				first = luaK_exp2anyreg(fs, &e)
			} else {
				luaK_exp2nextreg(fs, &e)
				first = fs.nactvar
			}
		}
	}
	luaK_ret(fs, first, nret)
	testnext(ls, ';')
}

func statement(ls *LexState) {
	line := ls.linenumber
	enterlevel(ls)
	switch ls.t.token {
	case ';':
		luaX_next(ls)
	case TK_IF:
		ifstat(ls, line)
	case TK_WHILE:
		whilestat(ls, line)
	case TK_DO:
		luaX_next(ls)
		block(ls)
		check_match(ls, TK_END, TK_DO, line)
	case TK_FOR:
		forstat(ls, line)
	case TK_REPEAT:
		repeatstat(ls, line)
	case TK_FUNCTION:
		funcstat(ls, line)
	case TK_LOCAL:
		luaX_next(ls)
		if testnext(ls, TK_FUNCTION) {
			localfunc(ls)
		} else {
			localstat(ls)
		}
	case TK_DBCOLON:
		luaX_next(ls)
		labelstat(ls, str_checkname(ls), line)
	case TK_RETURN:
		luaX_next(ls)
		retstat(ls)
	case TK_BREAK, TK_GOTO:
		gotostat(ls, luaK_jump(ls.fs))
	default:
		exprstat(ls)
	}
	ls.fs.freereg = ls.fs.nactvar
	leavelevel(ls)
}

func block_follow(ls *LexState, withuntil bool) bool {
	switch ls.t.token {
	case TK_ELSE, TK_ELSEIF, TK_END, TK_EOS:
		return true
	case TK_UNTIL:
		return withuntil
	}
	return false
}

func statlist(ls *LexState) {
	for !block_follow(ls, true) {
		if ls.t.token == TK_RETURN {
			statement(ls)
			return
		}
		statement(ls)
	}
}

func mainfunc(ls *LexState, fs *FuncState) {
	var bl BlockCnt
	open_func(ls, fs, &bl)
	fs.f.isVararg = 1
	var v expdesc
	init_exp(&v, VLOCAL, 0)
	newupvalue(fs, ls.envn, &v)
	luaX_next(ls)
	statlist(ls)
	check(ls, TK_EOS)
	close_func(ls)
}

func luaY_parser(L *State, src []byte, dyd *Dyndata, name string, fill func() []byte) *LClosure {
	var lexstate LexState
	var funcstate FuncState
	cl := luaF_newLclosure(L, 1)
	lexstate.h = luaH_new(L)
	funcstate.f = luaF_newproto(L)
	cl.p = funcstate.f
	funcstate.f.source = luaS_newCStr(L, name)
	lexstate.dyd = dyd
	dyd.actvar.n = 0
	dyd.gt.n = 0
	dyd.label.n = 0
	firstchar := EOZ
	if len(src) > 0 {
		firstchar = int(src[0])
		src = src[1:]
	}
	luaX_setinput(L, &lexstate, src, funcstate.f.source, firstchar, fill)
	mainfunc(&lexstate, &funcstate)
	luaF_initupvals(L, cl)
	return cl
}
