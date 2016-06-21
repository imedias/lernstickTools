/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.fhnw.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Untility functions for managing encrypted partitions with LUKS
 * 
 */
public class LuksUtil {
    private static final String DUMMY_PASSWORD = "lernstick";
    private static final String CRYPTSETUP = "cryptsetup";
    // Hard-code iter-time to a really ow value so that the penalty will not
    // be excessive on slow machines
    private static final String ITER_TIME = "--iter-time=5";
    
    private final static Logger LOGGER
            = Logger.getLogger(LuksUtil.class.getName());
    
    /**
     * Is device an encrypted LUKS partition.
     * 
     * @param device partition raw device path (e.g. /dev/sdb2)
     * @return true if a LUKS partition
     */
    public static boolean isLuks(String device) {
        ProcessExecutor processExecutor = new ProcessExecutor();
        int returnValue = processExecutor.executeProcess(
                CRYPTSETUP, "isLuks", device);
        return returnValue == 0;
    }
    
    /**
     * Check if LUKS partition is currently open and mapped
     * 
     * @param device partition raw device path (e.g. /dev/sdb2)
     * @return true if mapping exists
     */
    public static boolean isOpen(String device) {
        File devlink = new File(deviceToLogical(device));
        return devlink.exists();
    }
    
    /**
     * Create LUKS encrypted partition with apropriate setup for 
     * Lernstick.
     * 
     * @param device partition raw device path (e.g. /dev/sdb2)
     * @param passkey file with master encryption passkey
     * @return true if succeeded
     */
    public static boolean create(String device, File passkey) {
        ProcessExecutor processExecutor = new ProcessExecutor();
        int returnValue = processExecutor.executeProcess(
            CRYPTSETUP, "--key-file", passkey.getAbsolutePath(),
            ITER_TIME, "luksFormat", device);
        if (returnValue != 0) {
            LOGGER.log(Level.INFO, "luksFormat failed");
            return false;
        }
        try {
            returnValue = processExecutor.executeProcess(
                CRYPTSETUP, "--key-file", passkey.getAbsolutePath(),
                ITER_TIME, "luksAddKey", device,
                makeKeyFile(DUMMY_PASSWORD).getAbsolutePath());
        } catch(IOException e) {
            LOGGER.log(Level.INFO, "error creating key file", e);
            return false;
        }
        return returnValue == 0;
    }
    /**
     * Open LUKS partition and map to logical device using
     * /dev/xxx -> dev/mapper/xxx convention.
     * 
     * @param device partition raw device path (e.g. /dev/sdb2)
     * @param key file with key to open partition
     * @return true if succeeded
     */
    public static boolean open(String device, File key) {
        ProcessExecutor processExecutor = new ProcessExecutor();
        int returnValue = processExecutor.executeProcess(
            CRYPTSETUP, "--key-file", key.getAbsolutePath(),
            "luksOpen", device, deviceToName(device));
        return returnValue == 0;
    }
    
    /**
     * Open all exisiting LUKDS partition on drive
     * 
     * @param device drive device path (e.g. /dev/sdb)
     * @param key file with key to open partition
     * @return true if succeeded
     */
    public static boolean openAll(String device, File key) {
        boolean result = true;
        LOGGER.log(Level.INFO, "opening all luks containers on {0}", device);
        List<String> partitionStings = DbusTools.getPartitions();
        Pattern pattern = Pattern.compile(deviceToName(device) + "(\\d+)");
        for (String partitionString : partitionStings) {
            Matcher matcher = pattern.matcher(partitionString);
            if (matcher.matches()) {
                String partition = device + matcher.group(1);
                if (isLuks(partition)) {
                    if (!open(partition, key)) {
                        try {
                            // if mgt key is not working, try dummy password
                            result &= open(partition, makeKeyFile(DUMMY_PASSWORD));
                        } catch (IOException ex) {
                            LOGGER.log(Level.SEVERE, "could not create key file", ex);
                            result = false;
                        }
                    } else {
                        result &= true;
                    }
                }
            }
        }
        return result;
    }
    /**
     * Close a luks partition
     * @param device raw luks partition device path (e.g. /dev/sdb2)
     * @return true if command succeeded
     */
    public static boolean close(String device) {
        ProcessExecutor processExecutor = new ProcessExecutor();
        int returnValue = processExecutor.executeProcess(
        CRYPTSETUP, "luksClose", deviceToName(device));
        return returnValue == 0;
    }
    
    /**
     * Close any luks partitions on a drive
     * 
     * @param device drive device path (e.g. /dev/sdb)
     */
    public static void closeAll(String device) {
        LOGGER.log(Level.INFO, "closing all luks containers on {0}", device);
        List<String> partitionStings = DbusTools.getPartitions();
        Pattern pattern = Pattern.compile(deviceToName(device) + "(\\d+)");
        for (String partitionString : partitionStings) {
            Matcher matcher = pattern.matcher(partitionString);
            if (matcher.matches()) {
                String partition = device + matcher.group(1);
                if (isLuks(partition)) {
                    close(partition);
                }
            }
        }
    }
    
    /**
     * Change end-user password (slot 1) on Luks partition
     * 
     * @param device raw partition path (e.g. /dev/sdb2)
     * @param authPassword exsiting password
     * @param userPassword new user password
     * @return true if command succeeded
     */
    public static boolean changeUserPassword(String device,
            String authPassword, String userPassword) {
        try {
            return changeUserPassword(device,
                    makeKeyFile(authPassword), makeKeyFile(userPassword));
        } catch (IOException e) {
        LOGGER.log(Level.INFO, "Error in keyfile creation", e);
        return false;
        }
    }
    
    /**
     * Change the end-user password (slot 1) on Luks partition
     * 
     * @param device raw partition path (e.g. /dev/sdb2)
     * @param authKey file with existing passphrase to authorize operation
     * @param userKey file with new user passphrase
     * @return true if command succeeded
     */
    public static boolean changeUserPassword(String device,
            File authKey, File userKey) {
        ProcessExecutor processExecutor = new ProcessExecutor();
        int returnValue = processExecutor.executeProcess(
            CRYPTSETUP, "--key-file", authKey.getAbsolutePath(),
            "--key-slot", "1", ITER_TIME, "luksChangeKey", device,
            userKey.getAbsolutePath());
        return returnValue == 0;
    }
    
    /**
     * Map physical to DM logical device path
     * 
     * @param device raw partition device path (e.g. /dev/sdb2)
     * @return DM logical device path using
     */
    public static String deviceToLogical(String device) {
        return "/dev/mapper/" + deviceToName(device);
    }
    
    /**
     * Get udisk block device object-id from raw partition device path.
     * 
     * @param device raw device partition (e.g. /dev/sdb2)
     * @return 
     */
    public static String deviceToObjid(String device) {
        File devlink = new File(deviceToLogical(device));
        try {
            return "dm_2d" + devlink.getCanonicalPath().split("-")[1];
        } catch (IOException ex) {
            LOGGER.log(Level.INFO, "IO exception", ex);
            return null;
        }
    }
    
    /**
     * Generate a large random string as the master passkey.
     * 
     * @param keyfile file object to store key in
     * @throws IOException 
     */
    public static void generateKey(File keyfile) throws IOException {
        SecureRandom rnd = new SecureRandom();
        FileOutputStream outputStream = new FileOutputStream(keyfile);
        outputStream.write(new BigInteger(1024, rnd).toString(16).getBytes());
        outputStream.close();
    }
    
    /**
     * Create a temporary keyfile for a password string.
     * 
     * @param password string
     * @return temporary file with password string.
     * @throws IOException 
     */
    private static File makeKeyFile(String password) throws IOException {
        File tmpFile = File.createTempFile("dlcoypy", "txt");
        FileOutputStream outputStream = new FileOutputStream(tmpFile);
        outputStream.write(password.getBytes());
        outputStream.close();
        return tmpFile;
    }
    
    /**
     * Extract device nameAndNumber from device path.
     * 
     * @param device Device name of raw partition (e.g. /dev/sdb2)
     * @return 
     */
    private static String deviceToName(String device) {
        if (device.startsWith("/dev/")) {
          return device.substring(5); 
        }
        return device;
    }
}
