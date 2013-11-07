package org.telegram.api.engine.storage;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 2:03
 */
public class DcInfo {
    private boolean isAuthenticated;
    private String ip;
    private int port;

    public DcInfo(boolean authenticated, String ip, int port) {
        isAuthenticated = authenticated;
        this.ip = ip;
        this.port = port;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
