import com.xlox.utils.ObbAccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Plain-JVM checks for the decision that used to be made by a permission flag
 * alone. A wrong "no" here means the app never even tries to copy the OBB.
 *
 *   javac -d /tmp/obb \
 *     loader/app/src/main/java/com/xlox/utils/ObbAccess.java \
 *     loader/app/src/test/ObbAccessTest.java
 *   java -cp /tmp/obb ObbAccessTest
 */
public class ObbAccessTest {

    static int passed = 0;
    static int failed = 0;

    static final String PATH = "/storage/emulated/0/Android/obb/com.pubg.imobile";

    public static void main(String[] args) {
        // ---- the flag lies negatively: folder is readable, grant says no ----
        List<ObbAccess.Look> readable = Collections.singletonList(
                new ObbAccess.Look(PATH, true, true, 2));
        check("readable folder copies even when the flag says no",
                ObbAccess.decide(false, readable) == ObbAccess.READY);
        check("readable folder copies when the flag says yes",
                ObbAccess.decide(true, readable) == ObbAccess.READY);

        // ---- hidden folder + no grant: ask for the grant --------------------
        List<ObbAccess.Look> hidden = Collections.singletonList(
                new ObbAccess.Look(PATH, false, false, 0));
        check("hidden folder without grant asks for permission",
                ObbAccess.decide(false, hidden) == ObbAccess.NEEDS_PERMISSION);
        check("hidden folder with grant reports the folder is missing",
                ObbAccess.decide(true, hidden) == ObbAccess.NO_FOLDER);

        // ---- readable but empty: the game has not downloaded anything yet ---
        List<ObbAccess.Look> empty = Collections.singletonList(
                new ObbAccess.Look(PATH, true, true, 0));
        check("empty folder is reported as empty",
                ObbAccess.decide(false, empty) == ObbAccess.EMPTY_FOLDER);

        // ---- the folder is on a second volume -------------------------------
        List<ObbAccess.Look> second = Arrays.asList(
                new ObbAccess.Look("/storage/emulated/0/Android/obb/com.pubg.imobile", false, false, 0),
                new ObbAccess.Look("/storage/1A2B-3C4D/Android/obb/com.pubg.imobile", true, true, 1));
        check("an SD card copy is found", ObbAccess.decide(false, second) == ObbAccess.READY);
        check("the SD card path is the one used",
                ObbAccess.firstWithObbs(second).path.startsWith("/storage/1A2B-3C4D/"));

        // ---- nothing to probe at all ---------------------------------------
        check("no candidates and no grant asks for permission",
                ObbAccess.decide(false, new ArrayList<>()) == ObbAccess.NEEDS_PERMISSION);
        check("no candidates with grant reports a missing folder",
                ObbAccess.decide(true, new ArrayList<>()) == ObbAccess.NO_FOLDER);
        check("null list does not throw",
                ObbAccess.decide(true, null) == ObbAccess.NO_FOLDER);

        // ---- messages stay short and name the path --------------------------
        String needs = ObbAccess.describe(ObbAccess.NEEDS_PERMISSION, hidden);
        String none = ObbAccess.describe(ObbAccess.NO_FOLDER, hidden);
        String blank = ObbAccess.describe(ObbAccess.EMPTY_FOLDER, empty);
        check("permission message names the folder", needs.contains("Android/obb/com.pubg.imobile"));
        check("missing-folder message names the folder", none.contains("Android/obb/com.pubg.imobile"));
        check("the primary volume prefix is dropped", !needs.contains("/storage/emulated/0"));
        check("a second volume keeps its path", ObbAccess
                .describe(ObbAccess.READY, Collections.singletonList(
                        new ObbAccess.Look("/storage/1A2B-3C4D/Android/obb/com.pubg.imobile", true, true, 1)))
                .startsWith("/storage/1A2B-3C4D/"));
        check("empty message says what is wrong", blank.toLowerCase().contains("obb"));
        check("messages are one line", !needs.contains("\n") && !none.contains("\n"));
        check("describe survives an empty probe", !ObbAccess.describe(ObbAccess.READY, null).isEmpty());

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (ok) passed++; else failed++;
    }
}
