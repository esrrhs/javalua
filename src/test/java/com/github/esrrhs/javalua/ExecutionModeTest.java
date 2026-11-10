package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** CI must prove it is testing the requested backend, not an accidental fallback. */
class ExecutionModeTest {
    @Test
    void requestedBackendAndOracleAreActive() throws Exception {
        String mode = System.getProperty("javalua.test.mode");
        if (mode != null) {
            assertTrue(mode.equals("native") || mode.equals("java"));
            assertEquals(mode.equals("native"), NativeBridge.AVAILABLE, NativeBridge.LOAD_ERROR);
            if (mode.equals("java")) {
                assertEquals("true", System.getProperty("javalua.nonative"));
                assertEquals("disabled by -Djavalua.nonative", NativeBridge.LOAD_ERROR);
            } else {
                assertNull(System.getProperty("javalua.nonative"));
                assertNotNull(NativeBridge.cSetlocale(0, new byte[] {'C'}));
            }
            Path maps = Path.of("/proc/self/maps");
            if (Files.exists(maps)) {
                assertEquals(mode.equals("native"), Files.readString(maps).contains("libluashim"),
                        "actual mapped native libraries disagree with requested mode");
            }
        }
        if (Boolean.getBoolean("javalua.requireReference")) {
            assertNotNull(ReferenceLua.find(), "Lua 5.3.4 oracle required");
            assertNotNull(ReferenceLua.findLuac(), "luac 5.3.4 oracle required");
            assertNotNull(ReferenceLua.findFkdump(), "bytecode oracle required");
            assertNotNull(ReferenceLua.findTblstruct(), "table oracle required");
            assertTrue(Files.isRegularFile(Path.of(System.getenv("LUA53_TESTS"), "all.lua")),
                    "upstream tests required");
        }
    }
}
