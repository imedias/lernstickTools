package ch.fhnw.util;

/**
 * properties when mounting a parititon
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class MountInfo {

    private final String mountPath;
    private final boolean alreadyMounted;

    /**
     * creates a new MountInfo
     *
     * @param mountPath the path where the partition is mounted
     * @param alreadyMounted if <tt>true</tt>, the partition was already
     * mounted, <tt>false</tt> otherwise
     */
    public MountInfo(String mountPath, boolean alreadyMounted) {
        this.mountPath = mountPath;
        this.alreadyMounted = alreadyMounted;
    }

    /**
     * returns the mount path of the partition
     *
     * @return the mount path of the partition
     */
    public String getMountPath() {
        return mountPath;
    }

    /**
     * returns <tt>true</tt>, the partition was already mounted,
     * <tt>false</tt> otherwise
     *
     * @return <tt>true</tt>, the partition was already mounted,
     * <tt>false</tt> otherwise
     */
    public boolean alreadyMounted() {
        return alreadyMounted;
    }
}
