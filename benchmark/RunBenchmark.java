import com.github.esrrhs.fakescript.lua.LuaState;
import com.github.esrrhs.fakescript.lua.LuaLibs;
import com.github.esrrhs.fakescript.lua.LuaValue;
import com.github.esrrhs.fakescript.lua.LuaString;
import com.github.esrrhs.fakescript.lua.LuaError;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class RunBenchmark {
    public static void main(String[] args) throws Exception {
        System.err.println("Starting RunBenchmark...");
        if (args.length < 1) {
            System.err.println("Usage: java RunBenchmark <script.lua>");
            System.exit(1);
        }
        System.err.println("Loading script: " + args[0]);
        
        String script = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        System.err.println("Script loaded, " + script.length() + " chars");
        
        LuaState L = new LuaState();
        System.err.println("LuaState created");
        
        // 先设置输出流，再开库
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos, true, StandardCharsets.UTF_8);
        L.out = ps;
        L.err = ps;
        System.err.println("Output streams set");
        
        LuaLibs.openAll(L);
        System.err.println("Libs opened");
        
        try {
            // 用 dostring 的方式跑
            System.err.println("Loading script into Lua...");
            byte[] scriptBytes = script.getBytes(StandardCharsets.UTF_8);
            L.load(scriptBytes, args[0]);
            System.err.println("Script loaded, calling pcall...");
            L.pcall(0, 0);
            System.err.println("pcall done");
            
            // 输出结果
            ps.flush();
            String output = bos.toString(StandardCharsets.UTF_8);
            System.err.println("Output length: " + output.length());
            System.out.print(output);
            System.err.println("Done");
        } catch (LuaError e) {
            System.err.println("Runtime error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            L.close();
        }
    }
}
