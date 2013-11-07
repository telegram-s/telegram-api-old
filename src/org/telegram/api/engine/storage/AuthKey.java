package org.telegram.api.engine.storage;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 5:20
 */
public class AuthKey {
    private boolean isLoggedIn;
    private byte[] authKey;
    private byte[] lastSession;
    private ServerSalt[] knownSalts;

    public AuthKey(boolean isLoggedIn, byte[] authKey, byte[] lastSession, ServerSalt[] knownSalts) {
        this.isLoggedIn = isLoggedIn;
        this.authKey = authKey;
        this.lastSession = lastSession;
        this.knownSalts = knownSalts;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public byte[] getLastSession() {
        return lastSession;
    }

    public byte[] getAuthKey() {
        return authKey;
    }

    public ServerSalt[] getKnownSalts() {
        return knownSalts;
    }
}
