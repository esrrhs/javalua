package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 指令编解码测试。
 * <p>
 * 操作码的顺序、名字与 {@code luaP_opmodes} 属性表不是靠肉眼对齐的：
 * 用脚本直接从 {@code lua-5.4.7/src/lopcodes.h} 与 {@code lopcodes.c} 抽出来
 * 逐项比对过（83 项全等）。这里锁的是编解码本身——尤其是<b>有符号字段</b>与
 * 「指令字是负数」这两种最容易在本实现里出错的情形：C 的 {@code Instruction}
 * 是无符号 32 位，Java 的 {@code int} 不是。
 */
class OpCodeTest {

    @Test
    void fieldLayoutMatchesUpstream() {
        assertEquals(7, OpCode.SIZE_OP);
        assertEquals(8, OpCode.SIZE_A);
        assertEquals(8, OpCode.SIZE_B);
        assertEquals(8, OpCode.SIZE_C);
        assertEquals(17, OpCode.SIZE_BX);
        assertEquals(25, OpCode.SIZE_AX);
        assertEquals(25, OpCode.SIZE_SJ);
        assertEquals(0, OpCode.POS_OP);
        assertEquals(7, OpCode.POS_A);
        assertEquals(15, OpCode.POS_K);
        assertEquals(16, OpCode.POS_B);
        assertEquals(24, OpCode.POS_C);
        assertEquals(131071, OpCode.MAXARG_BX);
        assertEquals(65535, OpCode.OFFSET_SBX);
        assertEquals(33554431, OpCode.MAXARG_AX);
        assertEquals(16777215, OpCode.OFFSET_SJ);
        assertEquals(127, OpCode.OFFSET_SC);
        assertEquals(255, OpCode.NO_REG);
        assertEquals(50, OpCode.LFIELDS_PER_FLUSH);
    }

    @Test
    void abckRoundTrips() {
        int i = OpCode.createABCk(OpCode.OP_SETTABLE, 200, 100, 50, 1);
        assertEquals(OpCode.OP_SETTABLE, OpCode.getOpCode(i));
        assertEquals(200, OpCode.getArgA(i));
        assertEquals(100, OpCode.getArgB(i));
        assertEquals(50, OpCode.getArgC(i));
        assertEquals(1, OpCode.getArgK(i));

        int j = OpCode.createABCk(OpCode.OP_ADD, 0, 1, 2, 0);
        assertEquals(0, OpCode.getArgK(j));
        assertEquals(OpCode.IABC, OpCode.getOpMode(OpCode.getOpCode(j)));
    }

    @Test
    void abxRoundTrips() {
        int i = OpCode.createABx(OpCode.OP_LOADK, 3, OpCode.MAXARG_BX);
        assertEquals(OpCode.OP_LOADK, OpCode.getOpCode(i));
        assertEquals(3, OpCode.getArgA(i));
        assertEquals(OpCode.MAXARG_BX, OpCode.getArgBx(i));
        // Bx 取满时指令字最高位是 1（Java int 为负），取字段仍然必须正确
        assertTrue(i < 0, "Bx 取满时指令字在 Java 里是负数");
        assertEquals(OpCode.OP_LOADK, OpCode.getOpCode(i));
        assertEquals(3, OpCode.getArgA(i));
    }

    @Test
    void signedBxUsesOffset() {
        int i = OpCode.createABx(OpCode.OP_LOADI, 0, 0);
        assertEquals(-OpCode.OFFSET_SBX, OpCode.getArgSBx(i), "0 表示最小的 sBx");
        i = OpCode.setArgSBx(i, 0);
        assertEquals(0, OpCode.getArgSBx(i));
        assertEquals(OpCode.OFFSET_SBX, OpCode.getArgBx(i));
        i = OpCode.setArgSBx(i, -5);
        assertEquals(-5, OpCode.getArgSBx(i));
        i = OpCode.setArgSBx(i, OpCode.OFFSET_SBX);
        assertEquals(OpCode.OFFSET_SBX, OpCode.getArgSBx(i));
    }

    @Test
    void signedCUsesOffset() {
        assertEquals(127, OpCode.int2sC(0));
        assertEquals(0, OpCode.sC2int(127));
        assertEquals(255, OpCode.int2sC(128));
        assertEquals(128, OpCode.sC2int(255));
        int i = OpCode.createABCk(OpCode.OP_ADDI, 1, 2, OpCode.int2sC(-3), 0);
        assertEquals(-3, OpCode.getArgSC(i));
    }

    @Test
    void axAndSjRoundTrip() {
        int i = OpCode.createAx(OpCode.OP_EXTRAARG, OpCode.MAXARG_AX);
        assertTrue(i < 0);
        assertEquals(OpCode.OP_EXTRAARG, OpCode.getOpCode(i));
        assertEquals(OpCode.MAXARG_AX, OpCode.getArgAx(i));
        assertEquals(OpCode.IAX, OpCode.getOpMode(OpCode.OP_EXTRAARG));

        int j = OpCode.createsJ(OpCode.OP_JMP, 0, 0);
        assertEquals(-OpCode.OFFSET_SJ, OpCode.getArgSJ(j));
        j = OpCode.setArgSJ(j, 0);
        assertEquals(0, OpCode.getArgSJ(j));
        j = OpCode.setArgSJ(j, -1);
        assertEquals(-1, OpCode.getArgSJ(j));
        assertEquals(OpCode.ISJ, OpCode.getOpMode(OpCode.OP_JMP));
    }

    @Test
    void settingOneFieldDoesNotDisturbOthers() {
        int i = OpCode.createABCk(OpCode.OP_GETTABLE, 1, 2, 3, 0);
        i = OpCode.setArgA(i, 250);
        assertEquals(2, OpCode.getArgB(i));
        assertEquals(3, OpCode.getArgC(i));
        assertEquals(0, OpCode.getArgK(i));
        assertEquals(OpCode.OP_GETTABLE, OpCode.getOpCode(i));

        i = OpCode.setArgB(i, 254);
        assertEquals(250, OpCode.getArgA(i));
        assertEquals(3, OpCode.getArgC(i));

        i = OpCode.setArgC(i, 253);
        assertEquals(254, OpCode.getArgB(i));

        i = OpCode.setArgK(i, 1);
        assertEquals(253, OpCode.getArgC(i));
        assertEquals(250, OpCode.getArgA(i));

        i = OpCode.setOpCode(i, OpCode.OP_GETFIELD);
        assertEquals(1, OpCode.getArgK(i), "改操作码不能动 k");
        assertEquals(253, OpCode.getArgC(i));
    }

    @Test
    void opModesMatchUpstream() {
        assertEquals(OpCode.IABC, OpCode.getOpMode(OpCode.OP_MOVE));
        assertEquals(OpCode.IASBX, OpCode.getOpMode(OpCode.OP_LOADI));
        assertEquals(OpCode.IASBX, OpCode.getOpMode(OpCode.OP_LOADF));
        assertEquals(OpCode.IABX, OpCode.getOpMode(OpCode.OP_LOADK));
        assertEquals(OpCode.ISJ, OpCode.getOpMode(OpCode.OP_JMP));
        assertEquals(OpCode.IAX, OpCode.getOpMode(OpCode.OP_EXTRAARG));

        // 写 A 的指令 vs 不写的
        assertTrue(OpCode.testAMode(OpCode.OP_MOVE));
        assertFalse(OpCode.testAMode(OpCode.OP_SETUPVAL), "SETUPVAL 不写寄存器 A");
        assertFalse(OpCode.testAMode(OpCode.OP_SETTABLE));
        assertFalse(OpCode.testAMode(OpCode.OP_JMP));

        // 测试指令：下一条必须是跳转
        assertTrue(OpCode.testTMode(OpCode.OP_EQ));
        assertTrue(OpCode.testTMode(OpCode.OP_TEST));
        assertTrue(OpCode.testTMode(OpCode.OP_TESTSET));
        assertTrue(OpCode.testTMode(OpCode.OP_LEI));
        assertFalse(OpCode.testTMode(OpCode.OP_ADD));

        // 元方法指令
        assertTrue(OpCode.testMMMode(OpCode.OP_MMBIN));
        assertTrue(OpCode.testMMMode(OpCode.OP_MMBINI));
        assertTrue(OpCode.testMMMode(OpCode.OP_MMBINK));
        assertFalse(OpCode.testMMMode(OpCode.OP_ADD));

        // 只有 CALL 同时 OT 与 IT
        assertTrue(OpCode.testOTMode(OpCode.OP_CALL));
        assertTrue(OpCode.testITMode(OpCode.OP_CALL));
        assertFalse(OpCode.testOTMode(OpCode.OP_RETURN));
        assertTrue(OpCode.testITMode(OpCode.OP_RETURN));
    }

    @Test
    void topFlagsFollowCAndB() {
        // isOT：OT 类指令且 C == 0；TAILCALL 恒为 OT
        assertTrue(OpCode.isOT(OpCode.createABCk(OpCode.OP_CALL, 1, 2, 0, 0)));
        assertFalse(OpCode.isOT(OpCode.createABCk(OpCode.OP_CALL, 1, 2, 3, 0)));
        assertTrue(OpCode.isOT(OpCode.createABCk(OpCode.OP_TAILCALL, 1, 2, 3, 0)));

        // isIT：IT 类指令且 B == 0
        assertTrue(OpCode.isIT(OpCode.createABCk(OpCode.OP_CALL, 1, 0, 3, 0)));
        assertFalse(OpCode.isIT(OpCode.createABCk(OpCode.OP_CALL, 1, 2, 3, 0)));
        assertTrue(OpCode.isIT(OpCode.createABCk(OpCode.OP_SETLIST, 1, 0, 3, 0)));
    }

    @Test
    void opcodeCountAndNames() {
        assertEquals(83, OpCode.NUM_OPCODES);
        assertEquals(OpCode.OP_EXTRAARG + 1, OpCode.NUM_OPCODES);
        assertEquals("MOVE", OpCode.name(OpCode.OP_MOVE));
        assertEquals("EXTRAARG", OpCode.name(OpCode.OP_EXTRAARG));
        assertEquals("LFALSESKIP", OpCode.name(OpCode.OP_LFALSESKIP));
        assertEquals("VARARGPREP", OpCode.name(OpCode.OP_VARARGPREP));
    }
}
