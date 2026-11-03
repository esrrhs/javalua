// Translation of ldump.c (PUC-Lua 5.3.4 v 2.37): save precompiled chunks.
//
// Format matches PUC-Lua 5.3.4 byte-for-byte on little-endian hosts with
// fixed widths 4/8/4/8/8 (int / size_t / Instruction / lua_Integer /
// lua_Number): 5.3 string encoding, 5.3 constant tags (float=3 / int=19),
// upvalues without 'kind', absolute int lineinfo.
package core

import (
	"encoding/binary"
	"math"
)

// dumpWriter is C lua_Writer with ud folded into the closure (FLAG).
type dumpWriter func(L *State, b []byte) int

// DumpState is C DumpState {L, writer, data, strip, status}; data folded
// into writer (FLAG).
type DumpState struct {
	L      *State
	writer dumpWriter
	strip  bool /* C: int strip */
	status int
}

/*
** All high-level dumps go through dumpBlock of a prepared buffer; LE
** fixed widths replace C DumpVector/DumpVar host endianness (FLAG).
 */

func (D *DumpState) dumpBlock(b []byte) {
	if D.status == 0 && len(b) > 0 {
		/* C: lua_unlock(D->L); ...; lua_lock(D->L); locks N/A (FLAG) */
		D.status = D.writer(D.L, b)
	}
}

func (D *DumpState) dumpByte(y int) {
	x := byte(y) /* C: lu_byte x = (lu_byte)y; DumpVar(x, D) */
	D.dumpBlock([]byte{x})
}

// dumpInt writes a fixed-size int (4 bytes LE) — C DumpInt / DumpVar(int).
func (D *DumpState) dumpInt(x int) {
	var b [4]byte
	binary.LittleEndian.PutUint32(b[:], uint32(int32(x)))
	D.dumpBlock(b[:])
}

// dumpSizeT writes a size_t (8 bytes LE) — C DumpVar(size) in DumpString.
func (D *DumpState) dumpSizeT(x uint64) {
	var b [8]byte
	binary.LittleEndian.PutUint64(b[:], x)
	D.dumpBlock(b[:])
}

func (D *DumpState) dumpInteger(x int64) {
	var b [8]byte
	binary.LittleEndian.PutUint64(b[:], uint64(x))
	D.dumpBlock(b[:])
}

func (D *DumpState) dumpNumber(x float64) {
	var b [8]byte
	binary.LittleEndian.PutUint64(b[:], math.Float64bits(x))
	D.dumpBlock(b[:])
}

// dumpString is C DumpString: size byte, optional 0xFF+size_t; size
// includes trailing NUL that is not written.
func (D *DumpState) dumpString(s *TString) {
	if s == nil {
		D.dumpByte(0)
		return
	}
	size := uint64(len(s.val)) + 1 /* include trailing '\0' */
	str := s.val                   /* C: getstr(s) */
	if size < 0xFF {
		D.dumpByte(int(size)) /* C: DumpByte(cast_int(size), D) */
	} else {
		D.dumpByte(0xFF)
		D.dumpSizeT(size) /* C: DumpVar(size, D) */
	}
	D.dumpBlock([]byte(str)) /* no need to save '\0' — DumpVector(str, size-1) */
}

func (D *DumpState) dumpCode(f *Proto) {
	D.dumpInt(f.sizecode) /* C: DumpInt(f->sizecode, D) */
	n := f.sizecode
	/* C: DumpVector(f->code, f->sizecode, D); size 0 is DumpBlock no-op */
	if n <= 0 {
		return
	}
	b := make([]byte, 4*n)
	for i := 0; i < n; i++ {
		/* FLAG: pack Instruction struct → uint32 (C: raw Instruction vector) */
		binary.LittleEndian.PutUint32(b[4*i:], packInstruction(f.code[i]))
	}
	D.dumpBlock(b)
}

// On-disk constant tags (lobject.h) — aliases shared with lundump.go.
const (
	dumpTagNil     = LUA_TNIL
	dumpTagBoolean = LUA_TBOOLEAN
	dumpTagNumFlt  = LUA_TNUMFLT /* 3 */
	dumpTagNumInt  = LUA_TNUMINT /* 19 */
	dumpTagShrStr  = LUA_TSHRSTR
	dumpTagLngStr  = LUA_TLNGSTR
)

func (D *DumpState) dumpConstants(f *Proto) {
	n := f.sizek /* C: f->sizek */
	D.dumpInt(n)
	for i := 0; i < n; i++ {
		o := &f.k[i]
		/* C: DumpByte(ttype(o), D); switch (ttype(o)) —
		   ttypetag is C 5.3.4 ttype (bits 0-5). */
		D.dumpByte(ttypetag(o))
		switch ttypetag(o) {
		case LUA_TNIL:
			/* nothing */
		case LUA_TBOOLEAN:
			if bvalue(o) {
				D.dumpByte(1) /* C: DumpByte(bvalue(o), D) */
			} else {
				D.dumpByte(0)
			}
		case LUA_TNUMFLT:
			D.dumpNumber(fltvalue(o))
		case LUA_TNUMINT:
			D.dumpInteger(ivalue(o))
		case LUA_TSHRSTR, LUA_TLNGSTR:
			D.dumpString(tsvalue(o))
		default:
			/* C: lua_assert(0). Do not emit a fake nil tag (prior Go did). */
			panic("dumpConstants: unexpected constant type")
		}
	}
}

func (D *DumpState) dumpProtos(f *Proto) {
	n := f.sizep /* C: f->sizep */
	D.dumpInt(n)
	for i := 0; i < n; i++ {
		D.dumpFunction(f.p[i], f.source) /* C: DumpFunction(f->p[i], f->source, D) */
	}
}

func (D *DumpState) dumpUpvalues(f *Proto) {
	n := f.sizeupvalues /* C: f->sizeupvalues */
	D.dumpInt(n)
	for i := 0; i < n; i++ {
		D.dumpByte(int(f.upvalues[i].instack))
		D.dumpByte(int(f.upvalues[i].idx))
		/* 5.3 has no 'kind' byte (5.4) */
	}
}

func (D *DumpState) dumpDebug(f *Proto) {
	n := 0
	if !D.strip {
		n = f.sizelineinfo /* C: n = (D->strip) ? 0 : f->sizelineinfo */
	}
	D.dumpInt(n)
	/* C: DumpVector(f->lineinfo, n, D); n==0 is no-op */
	if n > 0 {
		b := make([]byte, 4*n)
		for i := 0; i < n; i++ {
			binary.LittleEndian.PutUint32(b[4*i:], uint32(f.lineinfo[i]))
		}
		D.dumpBlock(b)
	}
	n = 0
	if !D.strip {
		n = f.sizelocvars /* C: n = (D->strip) ? 0 : f->sizelocvars */
	}
	D.dumpInt(n)
	for i := 0; i < n; i++ {
		D.dumpString(f.locvars[i].varname)
		D.dumpInt(f.locvars[i].startpc)
		D.dumpInt(f.locvars[i].endpc)
	}
	n = 0
	if !D.strip {
		n = f.sizeupvalues /* C: n = (D->strip) ? 0 : f->sizeupvalues */
	}
	D.dumpInt(n)
	for i := 0; i < n; i++ {
		D.dumpString(f.upvalues[i].name)
	}
}

func (D *DumpState) dumpFunction(f *Proto, psource *TString) {
	if D.strip || f.source == psource {
		D.dumpString(nil) /* no debug info or same source as its parent */
	} else {
		D.dumpString(f.source)
	}
	D.dumpInt(f.linedefined)
	D.dumpInt(f.lastlinedefined)
	D.dumpByte(int(f.numparams))
	D.dumpByte(int(f.isVararg)) /* C: f->is_vararg */
	D.dumpByte(int(f.maxstacksize))
	D.dumpCode(f)
	D.dumpConstants(f)
	D.dumpUpvalues(f)
	D.dumpProtos(f)
	D.dumpDebug(f)
}

func (D *DumpState) dumpHeader() {
	D.dumpBlock([]byte(dmpSignature)) /* C: DumpLiteral(LUA_SIGNATURE, D) */
	D.dumpByte(dmpVersion)            /* LUAC_VERSION */
	D.dumpByte(dmpFormat)             /* LUAC_FORMAT */
	D.dumpBlock([]byte(dmpData))      /* DumpLiteral(LUAC_DATA, D) */
	/* C: sizeof(int/size_t/Instruction/lua_Integer/lua_Number) — FLAG fixed LE */
	D.dumpByte(4)         /* sizeof(int) */
	D.dumpByte(8)         /* sizeof(size_t) */
	D.dumpByte(4)         /* sizeof(Instruction) */
	D.dumpByte(8)         /* sizeof(lua_Integer) */
	D.dumpByte(8)         /* sizeof(lua_Number) */
	D.dumpInteger(dmpInt) /* LUAC_INT */
	D.dumpNumber(dmpNum)  /* LUAC_NUM */
}

// luaU_dump is C luaU_dump — dump Lua function as precompiled chunk.
// writer closes over C's void *data (FLAG).
func luaU_dump(L *State, f *Proto, w dumpWriter, strip bool) int {
	var D DumpState
	D.L = L
	D.writer = w
	D.strip = strip
	D.status = 0
	D.dumpHeader()
	D.dumpByte(f.sizeupvalues) /* C: DumpByte(f->sizeupvalues, &D) */
	D.dumpFunction(f, nil)
	return D.status
}

/* lua_dump (lapi.c) colocated here — thin API wrapper. */
func luaDump(L *State, w dumpWriter, strip bool) int {
	/* C: lua_lock(L); api_checknelems(L, 1); ...; lua_unlock(L).
	   Locks / api_checknelems soft (FLAG — no lua_lock; empty stack panics). */
	o := &L.stack[L.top-1] /* C: L->top - 1 */
	if !ttisLclosure(o) {  /* C: isLfunction(o) */
		return 1
	}
	return luaU_dump(L, clLvalue(o).p, w, strip) /* C: getproto(o) */
}
