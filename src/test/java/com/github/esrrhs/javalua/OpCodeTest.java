package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Lua 5.3.4 opcode layout / modes. */
public class OpCodeTest {

    @Test
    void sizesAndPositions() {
        assertEquals(6, OpCode.SIZE_OP);
        assertEquals(8, OpCode.SIZE_A);
        assertEquals(9, OpCode.SIZE_B);
        assertEquals(9, OpCode.SIZE_C);
        assertEquals(18, OpCode.SIZE_BX);
        assertEquals(26, OpCode.SIZE_AX);
        assertEquals(0, OpCode.POS_OP);
        assertEquals(6, OpCode.POS_A);
        assertEquals(14, OpCode.POS_C);
        assertEquals(23, OpCode.POS_B);
        assertEquals(256, OpCode.BITRK);
        assertEquals(255, OpCode.MAXINDEXRK);
        assertEquals(47, OpCode.NUM_OPCODES);
    }

    @Test
    void createAndDecodeAbc() {
        int i = OpCode.createABC(OpCode.OP_ADD, 1, 2, OpCode.rkAsk(3));
        assertEquals(OpCode.OP_ADD, OpCode.getOpCode(i));
        assertEquals(1, OpCode.getArgA(i));
        assertEquals(2, OpCode.getArgB(i));
        assertTrue(OpCode.isK(OpCode.getArgC(i)));
        assertEquals(3, OpCode.indexK(OpCode.getArgC(i)));
    }

    @Test
    void createAndDecodeAbxAsbx() {
        int i = OpCode.createABx(OpCode.OP_LOADK, 5, 100);
        assertEquals(OpCode.OP_LOADK, OpCode.getOpCode(i));
        assertEquals(5, OpCode.getArgA(i));
        assertEquals(100, OpCode.getArgBx(i));

        int j = OpCode.createAsBx(OpCode.OP_JMP, 0, -10);
        assertEquals(OpCode.OP_JMP, OpCode.getOpCode(j));
        assertEquals(-10, OpCode.getArgSBx(j));
    }

    @Test
    void namesAndModes() {
        assertEquals("MOVE", OpCode.name(OpCode.OP_MOVE));
        assertEquals("LOADBOOL", OpCode.name(OpCode.OP_LOADBOOL));
        assertEquals("EXTRAARG", OpCode.name(OpCode.OP_EXTRAARG));
        assertEquals(OpCode.IABC, OpCode.getOpMode(OpCode.OP_ADD));
        assertEquals(OpCode.IABX, OpCode.getOpMode(OpCode.OP_LOADK));
        assertEquals(OpCode.IASBX, OpCode.getOpMode(OpCode.OP_JMP));
        assertEquals(OpCode.IAX, OpCode.getOpMode(OpCode.OP_EXTRAARG));
        assertTrue(OpCode.testAMode(OpCode.OP_MOVE));
        assertTrue(OpCode.testTMode(OpCode.OP_EQ));
        assertFalse(OpCode.testTMode(OpCode.OP_ADD));
    }

    @Test
    void rkHelpers() {
        assertFalse(OpCode.isK(10));
        assertTrue(OpCode.isK(OpCode.rkAsk(10)));
        assertEquals(10, OpCode.indexK(OpCode.rkAsk(10)));
    }
}
