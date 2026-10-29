import com.xlox.utils.ObbSelection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Plain-JVM checks for the OBB copy decisions — the part that silently reported
 * "ready" while the game still had no data.
 *
 * No Android, no Gradle, no emulator:
 *   javac -d /tmp/obb \
 *     loader/app/src/main/java/com/xlox/utils/ObbSelection.java \
 *     loader/app/src/test/ObbSelectionTest.java
 *   java -cp /tmp/obb ObbSelectionTest
 */
public class ObbSelectionTest {

    static final String PKG = "com.pubg.imobile";
    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("obb-test").toFile();

        // ---- only this game's OBBs count -----------------------------------
        File src = new File(root, "src");
        mkdirs(src);
        write(new File(src, "main.3." + PKG + ".obb"), 4096);
        write(new File(src, "patch.3." + PKG + ".obb"), 1024);
        write(new File(src, "main.3.com.other.game.obb"), 999);      // other game
        write(new File(src, "notes.txt"), 10);                        // junk
        write(new File(src, "main.3." + PKG + ".obb.tmp"), 10);       // partial

        List<File> found = ObbSelection.list(src, PKG);
        check("finds both main and patch", found.size() == 2);
        check("ignores other packages and junk",
                found.stream().noneMatch(f -> f.getName().contains("other") || f.getName().endsWith(".txt")
                        || f.getName().endsWith(".tmp")));
        check("smallest first", found.get(0).getName().startsWith("patch."));

        // ---- an empty destination is not ready -----------------------------
        File dest = new File(root, "dest");
        mkdirs(dest);
        check("empty dest is not ready", !ObbSelection.matches(found, dest, PKG));
        check("both files are pending", ObbSelection.pending(found, dest, PKG).size() == 2);

        // ---- half-copied destination is not ready (the old bug) ------------
        copyTo(found.get(0), new File(dest, found.get(0).getName()));
        check("half copy is not ready", !ObbSelection.matches(found, dest, PKG));
        check("only the missing one is pending", ObbSelection.pending(found, dest, PKG).size() == 1);

        // ---- wrong size counts as not copied -------------------------------
        write(new File(dest, found.get(0).getName()), 10);
        check("same name, wrong size is pending",
                ObbSelection.pending(found, dest, PKG).size() == 2);

        // ---- a complete copy is ready --------------------------------------
        for (File f : found) copyTo(f, new File(dest, f.getName()));
        check("complete copy is ready", ObbSelection.matches(found, dest, PKG));
        check("nothing left to copy", ObbSelection.pending(found, dest, PKG).isEmpty());

        // ---- a patch file alone is not enough -------------------------------
        File patchOnly = new File(root, "patchonly");
        mkdirs(patchOnly);
        copyTo(new File(src, "patch.3." + PKG + ".obb"), new File(patchOnly, "patch.3." + PKG + ".obb"));
        check("patch only -> hasMainObb false", !ObbSelection.hasMainObb(patchOnly, PKG));
        copyTo(new File(src, "main.3." + PKG + ".obb"), new File(patchOnly, "main.3." + PKG + ".obb"));
        check("main obb present -> hasMainObb true", ObbSelection.hasMainObb(patchOnly, PKG));

        // ---- missing source is never "ready" -------------------------------
        check("no source files -> not ready", !ObbSelection.matches(ObbSelection.list(src, "com.none.here"), dest, PKG));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (ok) passed++; else failed++;
    }

    static void mkdirs(File f) throws IOException {
        if (!f.isDirectory() && !f.mkdirs()) throw new IOException("cannot create " + f);
    }

    static void write(File f, int bytes) throws IOException {
        byte[] data = new byte[bytes];
        java.util.Arrays.fill(data, (byte) 'x');
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }

    static void copyTo(File from, File to) throws IOException {
        Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
