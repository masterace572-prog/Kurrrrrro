import com.xlox.utils.Elf;

import java.io.File;

/**
 * CI pre-build check: validates that a bundled shared library is a real ELF.
 * Usage: java -cp /tmp/elf ElfTest /path/to/library.so
 * Exits 0 if the file is a valid ELF, non-zero otherwise.
 */
public class ElfTest {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ElfTest <elf-file>");
            System.exit(2);
        }
        File file = new File(args[0]);
        try {
            if (!file.exists()) {
                System.err.println("not found: " + file);
                System.exit(1);
            }
            Elf elf = Elf.openWithMachine(file);
            if (!elf.isElf()) {
                System.err.println("not an ELF: " + file);
                System.exit(1);
            }
            if (!elf.is64Bit()) {
                System.err.println("not 64-bit: " + file);
                System.exit(1);
            }
            if (!elf.isAarch64()) {
                System.err.println("not aarch64: " + file);
                System.exit(1);
            }
            System.out.println("OK: " + file + " is a valid ELF");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }
}
