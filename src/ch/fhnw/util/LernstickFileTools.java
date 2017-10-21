package ch.fhnw.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * some file tools
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class LernstickFileTools {

    private static final Logger LOGGER
            = Logger.getLogger(LernstickFileTools.class.getName());
    private static final ResourceBundle STRINGS
            = ResourceBundle.getBundle("ch/fhnw/util/Strings");
    private final static NumberFormat NUMBER_FORMAT
            = NumberFormat.getInstance();

    /**
     * reads a file line by line
     *
     * @param file the file to read
     * @return the list of lines in this file
     * @throws IOException if an I/O exception occurs
     */
    public static List<String> readFile(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line = reader.readLine(); line != null;) {
                lines.add(line);
                line = reader.readLine();
            }
        }
        return lines;
    }

    /**
     * replaces a text in a file
     *
     * @param fileName the path to the file
     * @param pattern the pattern to search for
     * @param replacement the replacemtent text to set
     * @throws IOException
     */
    public static void replaceText(String fileName, Pattern pattern,
            String replacement) throws IOException {
        File file = new File(fileName);
        if (file.exists()) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,
                        "replacing pattern \"{0}\" with \"{1}\" in file \"{2}\"",
                        new Object[]{pattern.pattern(), replacement, fileName});
            }
            List<String> lines = LernstickFileTools.readFile(file);
            boolean changed = false;
            for (int i = 0, size = lines.size(); i < size; i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    LOGGER.log(Level.INFO, "line \"{0}\" matches", line);
                    lines.set(i, matcher.replaceAll(replacement));
                    changed = true;
                } else {
                    LOGGER.log(Level.INFO, "line \"{0}\" does NOT match", line);
                }
            }
            if (changed) {
                writeFile(file, lines);
            }
        } else {
            LOGGER.log(Level.WARNING, "file \"{0}\" does not exist!", fileName);
        }
    }

    /**
     * writes lines of text into a file
     *
     * @param file the file to write into
     * @param lines the lines to write
     * @throws IOException
     */
    public static void writeFile(File file, List<String> lines)
            throws IOException {
        // delete old version of file
        if (file.exists()) {
            file.delete();
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            String lineSeparator = System.getProperty("line.separator");
            for (String line : lines) {
                outputStream.write((line + lineSeparator).getBytes());
            }
            outputStream.flush();
        }
    }

    /**
     * returns the string representation of a given data volume
     *
     * @param bytes the datavolume given in Byte
     * @param fractionDigits the number of fraction digits to display
     * @return the string representation of a given data volume
     */
    public static String getDataVolumeString(long bytes, int fractionDigits) {
        if (bytes >= 1024) {
            NUMBER_FORMAT.setMaximumFractionDigits(fractionDigits);
            float kbytes = (float) bytes / 1024;
            if (kbytes >= 1024) {
                float mbytes = (float) bytes / 1048576;
                if (mbytes >= 1024) {
                    float gbytes = (float) bytes / 1073741824;
                    if (gbytes >= 1024) {
                        float tbytes = (float) bytes / 1099511627776L;
                        return NUMBER_FORMAT.format(tbytes) + " TiB";
                    }
                    return NUMBER_FORMAT.format(gbytes) + " GiB";
                }

                return NUMBER_FORMAT.format(mbytes) + " MiB";
            }

            return NUMBER_FORMAT.format(kbytes) + " KiB";
        }

        return NUMBER_FORMAT.format(bytes) + " Byte";
    }

    /**
     * creates a temporary directory
     *
     * @param parentDir the parent directory
     * @param name the name of the temporary directory
     * @return the temporary directory
     */
    public static File createTempDirectory(File parentDir, String name) {
        File tempDir = new File(parentDir, name);
        if (tempDir.exists()) {
            // search for an alternative non-existing directory
            for (int i = 1;
                    (tempDir = new File(parentDir, name + i)).exists(); i++) {
            }
        }
        if (!tempDir.mkdirs()) {
            LOGGER.log(Level.WARNING, "can not create {0}", tempDir);
        }
        return tempDir;
    }

    /**
     * recusively deletes a file
     *
     * @param file the file to delete
     * @param removeFile if the file (directory) itself should be removed or
     * just its subfiles
     * @return <code>true</code> if and only if the file or directory is
     * successfully deleted; <code>false</code> otherwise
     * @throws IOException if an I/O exception occurs
     */
    public static boolean recursiveDelete(File file, boolean removeFile)
            throws IOException {
        // do NOT(!) follow symlinks when deleting files
        if (file.isDirectory() && !isSymlink(file)) {
            File[] subFiles = file.listFiles();
            if (subFiles != null) {
                for (File subFile : subFiles) {
                    recursiveDelete(subFile, true);
                }
            }
        }
        return removeFile ? file.delete() : true;
    }

    /**
     * checks if a file is a symlink
     *
     * @param file the file to check
     * @return <tt>true</tt>, if <tt>file</tt> is a symlink, <tt>false</tt>
     * otherwise
     * @throws IOException if an I/O exception occurs
     */
    public static boolean isSymlink(File file) throws IOException {
        File canon;
        if (file.getParent() == null) {
            canon = file;
        } else {
            File canonDir = file.getParentFile().getCanonicalFile();
            canon = new File(canonDir, file.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    /**
     * mounts all squashfs found in a given systemPath
     *
     * @param systemPath the given systemPath
     * @return a list of mount points
     * @throws IOException
     */
    public static List<String> mountAllSquashFS(String systemPath)
            throws IOException {
        // get a list of all available squashfs
        FilenameFilter squashFsFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".squashfs");
            }
        };
        File liveDir = new File(systemPath, "live");
        File[] squashFileSystems = liveDir.listFiles(squashFsFilter);

        // mount all squashfs read-only in temporary directories
        List<String> readOnlyMountPoints = new ArrayList<>();
        Path tmpDir = Files.createTempDirectory(null);
        ProcessExecutor processExecutor = new ProcessExecutor();
        for (int i = 0; i < squashFileSystems.length; i++) {
            Path roDir = tmpDir.resolve("ro" + (i + 1));
            Files.createDirectory(roDir);
            String roPath = roDir.toString();
            readOnlyMountPoints.add(roPath);
            String filePath = squashFileSystems[i].getPath();
            processExecutor.executeProcess(
                    "mount", "-o", "loop", filePath, roPath);
        }
        return readOnlyMountPoints;
    }

    /**
     * mounts an aufs file system with the given mountpoints
     *
     * @param readWriteMountPoint the read-write mountpoint
     * @param readOnlyMountPoints the read-only mountpoints
     * @return the cow mount point
     * @throws IOException
     */
    public static File mountAufs(String readWriteMountPoint,
            List<String> readOnlyMountPoints) throws IOException {

        // cowDir is placed in /run/ because it is one of
        // the few directories that are not aufs itself.
        // Nested aufs is not (yet) supported...
        File runDir = new File("/run/");

        // To create the file system union, we need a temporary and
        // writable xino file that must not reside in an aufs. Therefore
        // we use a file in the /run directory, which is a writable
        // tmpfs.
        File xinoTmpFile = File.createTempFile(".aufs.xino", "", runDir);
        xinoTmpFile.delete();

        // The additional option "=ro+wh" for the readWriteMountPoint is
        // absolutely neccessary! Otherwise the whiteouts (info about
        // deleted files) in the readWriteMountPoint are not applied!
        String branchDefinition = "br=" + separateWithColons(
                readWriteMountPoint + "=ro+wh", readOnlyMountPoints);

        File cowDir = createTempDirectory(runDir, "cow");

        ProcessExecutor processExecutor = new ProcessExecutor();
        processExecutor.executeProcess("mount", "-t", "aufs",
                "-o", "xino=" + xinoTmpFile.getPath(),
                "-o", branchDefinition, "none", cowDir.getPath());

        return cowDir;
    }

    /**
     * mounts an overlay file system with the given mount points
     *
     * @param readWriteMountPoint the read-write mountpoint
     * @param readOnlyMountPoints the read-only mountpoints
     * @return the read-write directory, containing the subdirectories "upper",
     * "work" and "cow"
     * @throws IOException
     */
    public static File mountOverlay(String readWriteMountPoint,
            List<String> readOnlyMountPoints) throws IOException {

        String lowerDir = separateWithColons(
                readWriteMountPoint, readOnlyMountPoints);

        File runDir = new File("/run/");
        File rwDir = createTempDirectory(runDir, "rw");
        File upperDir = new File(rwDir, "upper");
        upperDir.mkdirs();
        File workDir = new File(rwDir, "work");
        workDir.mkdirs();
        File cowDir = new File(rwDir, "cow");
        cowDir.mkdirs();

        ProcessExecutor processExecutor = new ProcessExecutor();
        processExecutor.executeProcess(true, true,
                "mount", "-t", "overlay", "overlay",
                "-olowerdir=" + lowerDir
                + ",upperdir=" + upperDir
                + ",workdir=" + workDir,
                cowDir.getPath());

        return rwDir;
    }

    static long getSize(Path path) throws IOException {
        final AtomicLong size = new AtomicLong(0);

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                size.addAndGet(attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });

        return size.get();
    }

    /**
     * unmounts a device or mountpoint
     *
     * @param deviceOrMountpoint the device or mountpoint to unmount
     * @throws IOException
     */
    public static void umount(String deviceOrMountpoint) throws IOException {
        ProcessExecutor processExecutor = new ProcessExecutor();
        int exitValue = processExecutor.executeProcess(
                "umount", deviceOrMountpoint);
        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Error_Umount");
            errorMessage = MessageFormat.format(
                    errorMessage, deviceOrMountpoint);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    private static String separateWithColons(
            String readWriteMountPoint, List<String> readOnlyMountPoints) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(readWriteMountPoint);
        for (String readOnlyMountPoint : readOnlyMountPoints) {
            stringBuilder.append(':');
            stringBuilder.append(readOnlyMountPoint);
        }
        return stringBuilder.toString();
    }
}
