package org.telegram.api.engine.storage;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 5:24
 */
public class ServerSalt {
    private byte[] salt;
    private int validSince;
    private int validUntil;

    public ServerSalt(byte[] salt, int validSince, int validUntil) {
        this.salt = salt;
        this.validSince = validSince;
        this.validUntil = validUntil;
    }

    public byte[] getSalt() {
        return salt;
    }

    public int getValidSince() {
        return validSince;
    }

    public int getValidUntil() {
        return validUntil;
    }
}
