import com.xlox.utils.Elf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Checks the ELF reader against a real arm64 library and against things that are
 * not libraries at all. In CI the fixture is extracted from the bundled sandbox
 * AAR:
 *
 *   unzip -p loader/app/libs/mundo-release.aar jni/arm64-v8a/libmmkv.so > /tmp/libmmkv.so
 *   javac -d /tmp/elf loader/app/src/main/java/com/xlox/utils/Elf.java \
 *         loader/app/src/test/ElfTest.java
 *   java -cp /tmp/elf ElfTest /tmp/libmmkv.so
 */
public class ElfTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        // ---- a plain text file is not an ELF ---------------------------------
        File text = File.createTempFile("not-a-lib", ".so");
        try (FileOutputStream out = new FileOutputStream(text)) {
            out.write("PK\u0003\u0004 definitely a zip".getBytes(StandardCharsets.UTF_8));
        }
        Elf junk = Elf.read(text);
        check("a non-ELF file is reported as such", !junk.elf);
        check("its arch is named honestly", junk.arch().contains("not an ELF"));
        check("it claims no dependencies", junk.needed.isEmpty());

        // ---- a missing file does not throw ----------------------------------
        Elf missing = Elf.read(new File("/nonexistent/libbgmi.so"));
        check("a missing file is not an ELF", !missing.elf);

        // ---- a real arm64 library -------------------------------------------
        if (args.length > 0) {
            File lib = new File(args[0]);
            Elf elf = Elf.read(lib);
            System.out.println("  fixture: " + lib.getName() + " -> " + elf.arch()
                    + ", needs " + elf.needed);
            check("the fixture is an ELF", elf.elf);
            check("the fixture is 64-bit", elf.is64Bit);
            check("the fixture is arm64", "arm64-v8a".equals(elf.arch()));
            check("android libs link against libc or libm",
                    elf.needed.contains("libc.so") || elf.needed.contains("libm.so")
                            || elf.needed.contains("libdl.so") || elf.needed.contains("liblog.so"));
            check("names look like library names",
                    elf.needed.stream().allMatch(n -> n.endsWith(".so")));
        } else {
            System.out.println("  (no fixture given — run with a real .so to check the parser)");
        }

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (ok) passed++; else failed++;
    }
}
