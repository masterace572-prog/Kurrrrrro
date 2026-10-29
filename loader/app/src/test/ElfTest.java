import com.xlox.utils.Elf;
import com.xlox.utils.LibUpdate;
import com.xlox.utils.LibZip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the ELF reader and the "which libbgmi.so do I keep" choice against a
 * real arm64 library and against an archive that carries several ABIs. In CI
 * the fixture is extracted from the bundled sandbox AAR:
 *
 *   unzip -p loader/app/libs/mundo-release.aar jni/arm64-v8a/libmmkv.so > /tmp/libmmkv.so
 *   javac -d /tmp/elf loader/app/src/main/java/com/xlox/utils/Elf.java \
 *         loader/app/src/main/java/com/xlox/utils/LibZip.java \
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

        // ---- a 32-bit ARM header --------------------------------------------
        File arm32 = File.createTempFile("armeabi", ".so");
        writeElf32Arm(arm32);
        Elf a32 = Elf.read(arm32);
        check("a 32-bit ARM library is recognised", a32.elf && !a32.is64Bit);
        check("and named armeabi-v7a", "armeabi-v7a".equals(a32.arch()));

        // ---- a real arm64 library -------------------------------------------
        File real = args.length > 0 ? new File(args[0]) : null;
        if (real != null) {
            Elf elf = Elf.read(real);
            System.out.println("  fixture: " + real.getName() + " -> " + elf.arch()
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

        // ---- an archive with several ABIs -----------------------------------
        File extract = Files.createTempDirectory("loader-extract").toFile();
        File arm64Dir = new File(extract, "lib/arm64-v8a");
        File arm32Dir = new File(extract, "lib/armeabi-v7a");
        check("multi-abi layout created",
                arm64Dir.mkdirs() && arm32Dir.mkdirs());
        File arm64Lib = new File(arm64Dir, "libbgmi.so");
        File arm32Lib = new File(arm32Dir, "libbgmi.so");
        writeElf32Arm(arm32Lib);
        if (real != null) {
            Files.copy(real.toPath(), arm64Lib.toPath());
        } else {
            writeElf32Arm(arm64Lib);   // still a file, just not the right arch
        }

        List<File> hits = LibZip.findAll(extract, "libbgmi.so");
        check("both copies are found", hits.size() == 2);
        File chosen = LibZip.pickArm64(hits, extract);
        check("the arm64 copy is the one kept",
                chosen != null && chosen.getAbsolutePath().equals(arm64Lib.getAbsolutePath()));
        check("the 32-bit copy scores lower",
                LibZip.score(arm32Lib, extract) < LibZip.score(arm64Lib, extract));

        // a zip with only the 32-bit build must still be reported, not crash
        File only32 = Files.createTempDirectory("loader-32only").toFile();
        File nested32 = new File(only32, "lib/armeabi-v7a");
        nested32.mkdirs();
        File lone32 = new File(nested32, "libbgmi.so");
        writeElf32Arm(lone32);
        File picked = LibZip.pickArm64(LibZip.findAll(only32, "libbgmi.so"), only32);
        check("a 32-bit-only zip picks that file",
                picked != null && picked.getAbsolutePath().equals(lone32.getAbsolutePath()));
        check("and the caller can see it is the wrong arch",
                !Elf.read(picked).is64Bit && Elf.read(picked).machine == 0x28);

        // ---- when is a lib an update? ---------------------------------------
        LibUpdate.Source panelOld = new LibUpdate.Source("panel", "1.0", "/api/download");
        LibUpdate.Source mirrorOld = new LibUpdate.Source("mirror", "1.0", "libbbb.zip");
        LibUpdate.Source mirrorNew = new LibUpdate.Source("mirror", "1.1.0", "libbbb.zip");
        LibUpdate.Source panelNew = new LibUpdate.Source("panel", "1.2", "/api/download");

        List<LibUpdate.Source> bothOld = new ArrayList<>();
        bothOld.add(panelOld);
        bothOld.add(mirrorOld);
        check("up to date: nothing to download",
                LibUpdate.choose(bothOld, "1.0", true) == null);
        check("up to date: the check says so",
                !LibUpdate.isUpdateResult("No Update Available"));

        // The reported bug: a newer version on the mirror, an unchanged one in
        // the panel — the mirror has to win.
        List<LibUpdate.Source> panelStale = new ArrayList<>();
        panelStale.add(panelOld);
        panelStale.add(mirrorNew);
        LibUpdate.Source chosenSource = LibUpdate.choose(panelStale, "1.0", true);
        check("newer mirror version beats a stale panel release",
                chosenSource != null && "mirror".equals(chosenSource.name)
                        && "1.1.0".equals(chosenSource.version));
        check("and it counts as an update", LibUpdate.isUpdateResult("Update Available"));
        check("the installed version is not downloaded again",
                !LibUpdate.worthDownloading(panelOld, "1.0", true));

        // Same version from the other channel is the same file, not an update.
        check("switching channel does not re-download the same version",
                LibUpdate.choose(bothOld, "1.0", true) == null);

        // A lib that is missing from disk is always an update.
        check("missing lib forces a download even at the same version",
                LibUpdate.choose(bothOld, "1.0", false) != null);
        check("...and the download is allowed",
                LibUpdate.worthDownloading(panelOld, "1.0", false));

        // Bumped panel version wins over the older mirror.
        List<LibUpdate.Source> panelNewer = new ArrayList<>();
        panelNewer.add(panelNew);
        panelNewer.add(mirrorOld);
        chosenSource = LibUpdate.choose(panelNewer, "1.0", true);
        check("a newer panel release wins",
                chosenSource != null && "1.2".equals(chosenSource.version));

        // The other reported bug: the loader asked for an update on every
        // launch because the two channels advertised different strings, and the
        // one it had just installed was not the one the other channel offered.
        LibUpdate.Source panelAhead = new LibUpdate.Source("panel", "1.3", "/api/download");
        LibUpdate.Source mirrorBehind = new LibUpdate.Source("mirror", "1.2", "libbbb.zip");
        List<LibUpdate.Source> disagree = new ArrayList<>();
        disagree.add(panelAhead);
        disagree.add(mirrorBehind);
        check("installed 1.3 with the panel on 1.3 is up to date",
                LibUpdate.choose(disagree, "1.3", true) == null);
        check("a version older than the installed one is not an update",
                !LibUpdate.worthDownloading(mirrorBehind, "1.3", true));
        check("1.10 is newer than 1.9", LibUpdate.isNewer("1.10", "1.9"));
        check("1.2.0 is not newer than 1.2", !LibUpdate.isNewer("1.2.0", "1.2"));
        check("a v-prefix does not order differently", !LibUpdate.isNewer("v1.4", "1.4"));
        check("1.01 is not newer than 1.1", !LibUpdate.isNewer("1.01", "1.1"));
        check("an empty installed version is always an update", LibUpdate.isNewer("1.2", ""));
        check("an empty offered version never is", !LibUpdate.isNewer("", "1.2"));
        check("date-style versions order lexically by segment",
                LibUpdate.isNewer("2026.10.29", "2026.10.28"));
        check("the newest of two newer sources wins",
                "1.3".equals(LibUpdate.choose(disagree, "1.0", true).version));

        // Nothing reachable at all must not look like an update.
        check("no sources, lib on disk: no update",
                LibUpdate.choose(new ArrayList<>(), "1.0", true) == null);
        check("no sources, no lib: nothing to choose",
                LibUpdate.choose(new ArrayList<>(), "1.0", false) == null);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** 64-byte ELF header, class 32, little endian, machine = ARM (0x28). */
    static void writeElf32Arm(File file) throws IOException {
        byte[] b = new byte[64];
        b[0] = 0x7F;
        b[1] = 'E';
        b[2] = 'L';
        b[3] = 'F';
        b[4] = 1;                       // ELFCLASS32
        b[5] = 1;                       // little endian
        b[6] = 1;                       // version
        b[18] = 0x28;                   // e_machine = EM_ARM
        // e_phoff = 0 and e_phnum = 0: no program headers, which Elf tolerates
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(b);
        }
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (ok) passed++; else failed++;
    }
}
