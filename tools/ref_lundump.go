// Translation of lundump.c / lundump.h (PUC-Lua 5.3.4 v 2.44): load
// precompiled chunks.
//
// Format matches PUC-Lua 5.3.4 on little-endian hosts with fixed widths
// 4/8/4/8/8 (int / size_t / Instruction / lua_Integer / lua_Number):
// 5.3 string encoding, 5.3 constant tags (float=3 / int=19), absolute
// int lineinfo, upvalues without 'kind'.
//
// Intentional Go specializations (see NOTES.md): []byte+pos instead of
// ZIO; LE fixed widths; Instruction struct + pack/unpack via getOpMode;
// checkLoadMode + luaU_loadBinary colocated from ldo.c / lapi.c.
package core

import (
	"encoding/binary"
	"math"
	"strings"
)

// lundump.h LUAC_* + lua.h LUA_SIGNATURE — shared with ldump.go dumpHeader.
const (
	dmpSignature = "\x1bLua"            /* LUA_SIGNATURE */
	dmpData      = "\x19\x93\r\n\x1a\n" /* LUAC_DATA */
	dmpInt       = 0x5678               /* LUAC_INT */
	dmpNum       = 370.5                /* LUAC_NUM */
	dmpVersion   = 0x53                 /* LUAC_VERSION (5*16+3) */
	dmpFormat    = 0                    /* LUAC_FORMAT */
)

// LoadState is C LoadState {L, Z, name}; ZIO* → in-memory []byte+pos (FLAG).
type LoadState struct {
	L    *State
	data []byte
	pos  int
	name string
}

// loadError is C error(): "%s: %s precompiled chunk" + LUA_ERRSYNTAX.
// pushfString does not auto-push; Go pushes the message manually.
func (S *LoadState) loadError(why string) {
	ts := pushfString(S.L, "%s: %s precompiled chunk", S.name, why)
	S.L.checkStack(1)
	setsvalue(&S.L.stack[S.L.top], ts)
	S.L.top++
	luaD_throw(S.L, LUA_ERRSYNTAX)
}

// loadBlock is C LoadBlock / LoadVector: read size bytes or "truncated".
func (S *LoadState) loadBlock(n int) []byte {
	if n < 0 || S.pos+n > len(S.data) {
		S.loadError("truncated")
	}
	b := S.data[S.pos : S.pos+n]
	S.pos += n
	return b
}

func (S *LoadState) loadByte() byte { return S.loadBlock(1)[0] }

// loadInt is C LoadInt — host int via LoadVar; Go: fixed LE int32 (FLAG).
func (S *LoadState) loadInt() int {
	return int(int32(binary.LittleEndian.Uint32(S.loadBlock(4))))
}

// loadSizeT is C LoadVar(size_t) in LoadString; Go: fixed LE uint64 (FLAG).
func (S *LoadState) loadSizeT() uint64 {
	return binary.LittleEndian.Uint64(S.loadBlock(8))
}

func (S *LoadState) loadInteger() int64 {
	return int64(binary.LittleEndian.Uint64(S.loadBlock(8)))
}

func (S *LoadState) loadNumber() float64 {
	return math.Float64frombits(binary.LittleEndian.Uint64(S.loadBlock(8)))
}

// loadString is C LoadString: size byte, optional 0xFF+size_t, --size,
// short (≤LUAI_MAXSHORTLEN) via luaS_newlstr else luaS_createlngstrobj.
func (S *LoadState) loadString() *TString {
	size := uint64(S.loadByte())
	if size == 0xFF {
		size = S.loadSizeT()
	}
	if size == 0 {
		return nil
	}
	size-- /* remove trailing '\0' counted in size */
	/* Go hardening: reject absurd size (C trusts ZIO / host size_t). */
	if size > math.MaxInt32 {
		S.loadError("truncated")
	}
	b := S.loadBlock(int(size))
	if int(size) <= LUAI_MAXSHORTLEN { /* short string? */
		return luaS_newlstr(S.L, string(b))
	}
	/* long string — C loads directly into createlngstrobj buffer */
	return luaS_createlngstrobj(S.L, string(b))
}

func (S *LoadState) loadCode(f *Proto) {
	n := S.loadInt()
	/* Go hardening: n<0 / remaining-bytes (C LoadVector overruns/asserts). */
	if n < 0 || n > (len(S.data)-S.pos)/4 {
		S.loadError("truncated")
	}
	f.code = make([]Instruction, n)
	f.sizecode = n
	for i := 0; i < n; i++ {
		/* FLAG: unpack uint32 → Instruction struct (C: raw Instruction vector) */
		f.code[i] = unpackInstruction(binary.LittleEndian.Uint32(S.loadBlock(4)))
	}
}

func (S *LoadState) loadConstants(f *Proto) {
	n := S.loadInt()
	if n < 0 {
		S.loadError("truncated")
	}
	f.k = make([]Value, n) /* C: newvector + setnilvalue loop (GC); make is nil */
	f.sizek = n
	for i := 0; i < n; i++ {
		o := &f.k[i]
		/* C: int t = LoadByte(S); switch (t) with LUA_T* cases */
		switch S.loadByte() {
		case LUA_TNIL:
			setnilvalue(o)
		case LUA_TBOOLEAN:
			setbvalue(o, S.loadByte() != 0) /* C: setbvalue(o, LoadByte(S)) */
		case LUA_TNUMFLT:
			setfltvalue(o, S.loadNumber())
		case LUA_TNUMINT:
			setivalue(o, S.loadInteger())
		case LUA_TSHRSTR, LUA_TLNGSTR:
			setsvalue(o, S.loadString()) /* C: setsvalue2n (barrier; Go GC N/A) */
		default:
			/* C: lua_assert(0) (no-op in release → leaves nil). Go: reject. */
			S.loadError("corrupted")
		}
	}
}

func (S *LoadState) loadProtos(f *Proto) {
	n := S.loadInt()
	if n < 0 {
		S.loadError("truncated")
	}
	f.p = make([]*Proto, n) /* C null-inits then newproto; make is nil */
	f.sizep = n
	for i := 0; i < n; i++ {
		f.p[i] = luaF_newproto(S.L)
		S.loadFunction(f.p[i], f.source)
	}
}

func (S *LoadState) loadUpvalues(f *Proto) {
	n := S.loadInt()
	if n < 0 {
		S.loadError("truncated")
	}
	f.upvalues = make([]Upvaldesc, n) /* C nulls .name first (GC) */
	f.sizeupvalues = n
	for i := 0; i < n; i++ {
		f.upvalues[i].instack = S.loadByte()
		f.upvalues[i].idx = S.loadByte()
		/* 5.3 has no 'kind' byte (5.4) */
	}
}

func (S *LoadState) loadDebug(f *Proto) {
	n := S.loadInt()
	if n < 0 {
		S.loadError("truncated")
	}
	f.lineinfo = make([]int32, n)
	f.sizelineinfo = n
	for i := 0; i < n; i++ {
		f.lineinfo[i] = int32(S.loadInt()) /* C: LoadVector of int (absolute lines) */
	}
	n = S.loadInt()
	if n < 0 {
		S.loadError("truncated")
	}
	f.locvars = make([]LocVar, n)
	f.sizelocvars = n
	for i := 0; i < n; i++ {
		f.locvars[i].varname = S.loadString()
		f.locvars[i].startpc = S.loadInt()
		f.locvars[i].endpc = S.loadInt()
	}
	n = S.loadInt()
	if n < 0 {
		S.loadError("truncated")
	}
	/* C: for i<n: f->upvalues[i].name = LoadString (no check vs sizeupvalues).
	   Go hardening: reject n!=0 && n!=sizeupvalues (corrupt-only; see NOTES). */
	if n != 0 && n != f.sizeupvalues {
		S.loadError("corrupted")
	}
	for i := 0; i < n; i++ {
		f.upvalues[i].name = S.loadString()
	}
}

func (S *LoadState) loadFunction(f *Proto, psource *TString) {
	f.source = S.loadString()
	if f.source == nil { /* no source in dump? */
		f.source = psource /* reuse parent's source */
	}
	f.linedefined = S.loadInt()
	f.lastlinedefined = S.loadInt()
	f.numparams = S.loadByte()
	f.isVararg = S.loadByte() /* C: is_vararg */
	f.maxstacksize = S.loadByte()
	S.loadCode(f)
	S.loadConstants(f)
	S.loadUpvalues(f)
	S.loadProtos(f)
	S.loadDebug(f)
}

func (S *LoadState) checkLiteral(s, msg string) {
	if string(S.loadBlock(len(s))) != s {
		S.loadError(msg)
	}
}

func (S *LoadState) checkSize(size int, tname string) {
	if int(S.loadByte()) != size {
		/* C: error(S, luaO_pushfstring(...)) — extra stack slot; message same */
		S.loadError(pushfString(S.L, "%s size mismatch in", tname).val)
	}
}

func (S *LoadState) checkHeader() {
	S.checkLiteral(dmpSignature[1:], "not a") /* 1st char already checked */
	if S.loadByte() != dmpVersion {
		S.loadError("version mismatch in")
	}
	if S.loadByte() != dmpFormat {
		S.loadError("format mismatch in")
	}
	S.checkLiteral(dmpData, "corrupted")
	S.checkSize(4, "int")         /* sizeof(int) */
	S.checkSize(8, "size_t")      /* sizeof(size_t) */
	S.checkSize(4, "Instruction") /* sizeof(Instruction) */
	S.checkSize(8, "lua_Integer")
	S.checkSize(8, "lua_Number")
	if S.loadInteger() != dmpInt {
		S.loadError("endianness mismatch in")
	}
	if S.loadNumber() != dmpNum {
		S.loadError("float format mismatch in")
	}
}

// luaU_undump is C luaU_undump(L, Z, name).
// Differences vs C body alone (FLAG / composition — see NOTES):
//   - ZIO → []byte; re-checks signature[0] (C: caller peeked it via zgetc)
//   - no lua_assert(nupvalues==sizeupvalues) / luai_verifycode (C default empty)
//
// Stack: matches C — setclLvalue + inctop before LoadFunction.
func luaU_undump(L *State, data []byte, name string) *LClosure {
	var S LoadState
	switch {
	case len(name) > 0 && (name[0] == '@' || name[0] == '='):
		S.name = name[1:]
	case len(name) > 0 && name[0] == dmpSignature[0]:
		S.name = "binary string"
	default:
		S.name = name
	}
	S.L = L
	S.data = data
	if len(data) == 0 || data[0] != dmpSignature[0] {
		S.loadError("not a")
	}
	S.pos = 1
	S.checkHeader()
	cl := luaF_newLclosure(L, int(S.loadByte()))
	L.checkStack(1)
	setclLvalue(&L.stack[L.top], cl) /* C: setclLvalue(L, L->top, cl) */
	L.top++                          /* C: luaD_inctop(L) */
	cl.p = luaF_newproto(L)
	S.loadFunction(cl.p, nil)
	/* C: lua_assert(cl->nupvalues == cl->p->sizeupvalues); luai_verifycode(...) */
	return cl
}

// checkLoadMode is C checkmode (ldo.c), colocated here (FLAG placement).
// C skips when mode==NULL; Go has no nil string — empty mode rejects (call
// sites pass "bt" / "b" / "t").
func checkLoadMode(L *State, mode, x string) {
	if !strings.ContainsRune(mode, rune(x[0])) {
		ts := pushfString(L, "attempt to load a %s chunk (mode is '%s')", x, mode)
		L.checkStack(1)
		setsvalue(&L.stack[L.top], ts)
		L.top++
		luaD_throw(L, LUA_ERRSYNTAX)
	}
}

// luaU_loadBinary folds C f_parser binary path + lua_load _ENV setup
// (ldo.c / lapi.c) around luaU_undump (FLAG composition; not in lundump.c).
// Matches C luaD_protectedparser: incnny around the protected body.
func luaU_loadBinary(L *State, data []byte, chunkname, mode string) error {
	oldtop := L.top
	incnny(L) /* cannot yield during parsing — C luaD_protectedparser */
	status := L.rawRunProtected(func() {
		checkLoadMode(L, mode, "binary")
		cl := luaU_undump(L, data, chunkname) /* pushes cl (C undump) */
		luaF_initupvals(L, cl)                /* C f_parser after undump */
		if cl.nupvalues >= 1 {
			/* C lua_load: set registry globals as 1st upvalue (_ENV) */
			var gt Value
			sethvalue(&gt, L.rawGetRegistryGlobals())
			setobj(upvalValue(cl.upvals[0]), &gt)
		}
		/* cl already on stack from undump — do not push again */
	})
	decnny(L)
	if status != LUA_OK {
		msg := ""
		if L.top > oldtop {
			msg = valueToStr(&L.stack[L.top-1])
		}
		L.top = oldtop
		return &LuaError{Status: status, Message: msg}
	}
	return nil
}

// 5.3 bit layout (lopcodes.h): OP:6 A:8 C:9 B:9 — pack/unpack for
// Instruction struct ↔ on-disk uint32 (C loads a raw Instruction vector).
const (
	instrPosOp  = 0
	instrPosA   = 6
	instrPosC   = 14
	instrPosB   = 23
	instrPosBx  = 14
	instrPosAx  = 6
	instrSizeOp = 6
	instrSizeA  = 8
	instrSizeB  = 9
	instrSizeC  = 9
	instrSizeBx = 18
	instrSizeAx = 26
)

func instrMask(size int) uint32 { return uint32(1)<<uint(size) - 1 }

// packInstruction encodes Instruction as a 32-bit word (5.3 CREATE_*).
// For iAsBx, B already holds the biased Bx (sbx + OFFSET_sBx).
// Uses getOpMode from lopcodes.go (C luaP_opmodes & 3) — no duplicate table.
func packInstruction(i Instruction) uint32 {
	if i.Op < 0 || i.Op >= NUM_OPCODES {
		return uint32(i.Op) << instrPosOp
	}
	var w uint32
	switch getOpMode(i.Op) {
	case iABC:
		w = uint32(i.Op)<<instrPosOp |
			uint32(i.A)<<instrPosA |
			uint32(i.B)<<instrPosB |
			uint32(i.C)<<instrPosC
	case iABx, iAsBx:
		w = uint32(i.Op)<<instrPosOp |
			uint32(i.A)<<instrPosA |
			uint32(i.B)<<instrPosBx
	case iAx:
		w = uint32(i.Op)<<instrPosOp |
			uint32(i.A)<<instrPosAx
	}
	return w
}

// unpackInstruction decodes a 32-bit word (5.3 GET_OPCODE / GETARG_*).
func unpackInstruction(w uint32) Instruction {
	op := int(w>>instrPosOp) & int(instrMask(instrSizeOp))
	if op < 0 || op >= NUM_OPCODES {
		return Instruction{Op: op}
	}
	switch getOpMode(op) {
	case iABC:
		return Instruction{
			Op: op,
			A:  int(w>>instrPosA) & int(instrMask(instrSizeA)),
			B:  int(w>>instrPosB) & int(instrMask(instrSizeB)),
			C:  int(w>>instrPosC) & int(instrMask(instrSizeC)),
		}
	case iABx, iAsBx:
		return Instruction{
			Op: op,
			A:  int(w>>instrPosA) & int(instrMask(instrSizeA)),
			B:  int(w>>instrPosBx) & int(instrMask(instrSizeBx)),
		}
	default: /* iAx */
		return Instruction{
			Op: op,
			A:  int(w>>instrPosAx) & int(instrMask(instrSizeAx)),
		}
	}
}
