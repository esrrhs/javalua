package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Mandatory when explicitly invoked by tools/test_c_extensions.sh, native-only.
 * The IT suffix deliberately keeps the ordinary native/nonative suite unchanged.
 * No assumptions, fallback modules, output normalization, or environment-based skips.
 */
final class RealExtensionCompatibilityIT {
    private static final Path FIXTURES = Path.of("src/test/resources/c-extensions/cases");
    private static final Path MODULES = Path.of("target/c-extensions").toAbsolutePath();
    private static final Path DATA = Path.of("target/ce-data").toAbsolutePath();
    private static final String SCRIPT = "target/ce-case.lua";

    @TestFactory
    List<DynamicTest> realModules() throws Exception {
        assertFalse(Boolean.getBoolean("javalua.nonative"), "This gate requires native mode");
        assertTrue(NativeBridge.AVAILABLE, () -> "native shim unavailable: " + NativeBridge.LOAD_ERROR);
        assertNotNull(ReferenceLua.find(), "stock Lua 5.3.4 is mandatory");
        Files.createDirectories(DATA);
        List<Path> cases;
        try (var paths = Files.list(FIXTURES)) {
            cases = paths.filter(p -> p.toString().endsWith(".lua")).sorted().toList();
        }
        assertEquals(15, cases.size(), "Missing or unregistered upstream fixture case");
        List<DynamicTest> tests = new ArrayList<>();
        for (String headers : List.of("stock", "shim")) {
            for (String module : List.of("lpeg", "cjson", "lfs")) {
                assertTrue(Files.isRegularFile(MODULES.resolve(headers).resolve(module + ".so")),
                        "Run python3 tools/prepare_c_extensions.py first");
            }
            for (Path fixture : cases) {
                String name = fixture.getFileName().toString().replace(".lua", "");
                String body = Files.readString(fixture).replace("$DATA", DATA.toString());
                for (boolean coroutine : List.of(false, true)) {
                    String testName = headers + "/" + name + (coroutine ? "/coroutine" : "/main");
                    String src = "package.path = ''\npackage.cpath = [==["
                            + MODULES.resolve(headers) + "/?.so]==]\n"
                            + (coroutine ? "coroutine.wrap(function()\n" : "")
                            + body
                            + (coroutine ? "end)()\n" : "")
                            + "print('OK " + name + "')\n";
                    tests.add(DynamicTest.dynamicTest(testName, () -> compare(src, name)));
                }
            }
        }
        assertEquals(60, tests.size());
        return tests;
    }

    private static void compare(String src, String name) throws Exception {
        ReferenceLua.Result reference = ReferenceLua.runBytes(
                src.getBytes(StandardCharsets.UTF_8), SCRIPT).result;
        assertEquals(0, reference.exitCode, () -> "stock module/script failed:\n" + reference.output);
        assertTrue(reference.output.endsWith("OK " + name + "\n"), "oracle did not complete");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // Use exactly the same chunk name/line numbers for luaL_argerror prefixes.
        LuaState state = new LuaState();
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            state.out = stream;
            state.err = stream;
            NativeBridge.cSetlocale(0, "C".getBytes(StandardCharsets.UTF_8));
            LuaLibs.openAll(state);
            int base = state.top;
            state.pushClosure(state.load(src.getBytes(StandardCharsets.UTF_8), "@" + SCRIPT));
            LuaValue err = state.pcall(base, 0);
            assertNull(err, () -> "Java module/script failed: " + err + "\nscript:\n" + src);
            // Latin-1 maps each byte 1:1, just like ReferenceLua; no address/traceback masking.
            assertEquals(reference.output, output.toString(StandardCharsets.ISO_8859_1),
                    () -> "real extension divergence:\n" + src);
        } finally {
            state.close();
        }
    }
}
