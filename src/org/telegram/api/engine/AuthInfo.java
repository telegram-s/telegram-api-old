package org.telegram.api.engine;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 04.11.13
 * Time: 22:01
 */
public class AuthInfo {
    private byte[] authKey;
    private byte[] serverSalt;

    public AuthInfo(byte[] authKey, byte[] serverSalt) {
        this.authKey = authKey;
        this.serverSalt = serverSalt;
    }

    public byte[] getAuthKey() {
        return authKey;
    }

    public byte[] getServerSalt() {
        return serverSalt;
    }
}
