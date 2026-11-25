package com.devhub.batch.core.model;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Immutable identity of one scheduler worker process.
 *
 * Rewrite note (fix): the old version called api.ipify.org / ifconfig.me at
 * startup to detect the public IP. That external call is removed — the public
 * IP is only ever taken from explicit configuration.
 */
public final class WorkerInfo {

    private final String workerId;
    private final String ownerId;
    private final String ownerName;
    private final String ownerEmail;
    private final String machineName;
    private final String privateIp;
    private final String publicIp;
    private final String macAddress;
    private final String osName;
    private final String osVersion;
    private final String osArch;
    private final String javaVersion;
    private final String javaVendor;
    private final String appVersion;
    private final String environment;
    private final String location;
    private final String pid;
    private final String runtimeId;
    private final String containerId;

    public WorkerInfo(String workerId, String ownerId, String ownerName, String ownerEmail,
                      String machineName, String privateIp, String publicIp, String macAddress,
                      String osName, String osVersion, String osArch, String javaVersion,
                      String javaVendor, String appVersion, String environment, String location,
                      String pid, String runtimeId, String containerId) {
        this.machineName = value(machineName, detectMachineName());
        this.workerId = value(workerId, "worker-" + this.machineName + "-" + value(pid, defaultPid()));
        this.ownerId = value(ownerId, "unknown");
        this.ownerName = value(ownerName, "unknown");
        this.ownerEmail = value(ownerEmail, "unknown");
        this.privateIp = value(privateIp, detectPrivateIp());
        this.publicIp = value(publicIp, "unknown");
        this.macAddress = value(macAddress, detectMacAddress());
        this.osName = value(osName, System.getProperty("os.name", "unknown"));
        this.osVersion = value(osVersion, System.getProperty("os.version", "unknown"));
        this.osArch = value(osArch, System.getProperty("os.arch", "unknown"));
        this.javaVersion = value(javaVersion, System.getProperty("java.version", "unknown"));
        this.javaVendor = value(javaVendor, System.getProperty("java.vendor", "unknown"));
        this.appVersion = value(appVersion, "devhub-batch-core");
        this.environment = value(environment, "default");
        this.location = value(location, "unknown");
        this.pid = value(pid, defaultPid());
        this.runtimeId = value(runtimeId, UUID.randomUUID().toString());
        this.containerId = value(containerId, "unknown");
    }

    /**
     * Local-only detection. No network calls: public IP must come from config.
     */
    public static WorkerInfo detect(String workerId, String ownerId, String ownerName, String ownerEmail,
                                    String machineName, String privateIp, String publicIp, String macAddress,
                                    String appVersion, String environment, String location, String containerId) {
        return new WorkerInfo(workerId, ownerId, ownerName, ownerEmail, machineName,
                privateIp, publicIp, macAddress,
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                System.getProperty("java.version"), System.getProperty("java.vendor"), appVersion,
                environment, location, null, null, containerId);
    }

    private static String defaultPid() { return Long.toString(ProcessHandle.current().pid()); }

    private static String detectMachineName() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { return "unknown-host"; }
    }

    private static String detectPrivateIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return "unknown";
    }

    private static String detectMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                byte[] mac = network.getHardwareAddress();
                if (mac == null || mac.length == 0) continue;
                StringBuilder value = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    if (i > 0) value.append(':');
                    value.append(String.format("%02X", mac[i]));
                }
                return value.toString();
            }
        } catch (Exception ignored) { }
        return "unknown";
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String value(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    public String getWorkerId() { return workerId; }
    public String getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getMachineName() { return machineName; }
    public String getPrivateIp() { return privateIp; }
    public String getPublicIp() { return publicIp; }
    public String getMacAddress() { return macAddress; }
    public String getOsName() { return osName; }
    public String getOsVersion() { return osVersion; }
    public String getOsArch() { return osArch; }
    public String getJavaVersion() { return javaVersion; }
    public String getJavaVendor() { return javaVendor; }
    public String getAppVersion() { return appVersion; }
    public String getEnvironment() { return environment; }
    public String getLocation() { return location; }
    public String getPid() { return pid; }
    public String getRuntimeId() { return runtimeId; }
    public String getContainerId() { return containerId; }
}
