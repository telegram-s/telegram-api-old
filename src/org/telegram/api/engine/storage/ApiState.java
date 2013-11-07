package org.telegram.api.engine.storage;

import static org.telegram.mtproto.secure.CryptoUtils.arrayEq;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 1:48
 */
public class ApiState {
    private static final String SETTING_AUTHENTICATED = "auth.is_authenticated";
    private static final String SETTING_AUTH_DC = "auth.dc";
    private static final String SETTING_AUTH_UID = "auth.uid";

    private static final String SETTING_DC_SYNC_TIME = "dc.sync_time";
    private static final String SETTING_DC_BOOTSTRAPPED = "dc.bootstrapped";

    private static final String SETTING_DC_AVAILABLE = "dc.{n}.available";
    private static final String SETTING_DC_ADDRESS = "dc.{n}.ip";
    private static final String SETTING_DC_PORT = "dc.{n}.port";

    private static final String SETTING_KEY_AUTH = "key.{n}.auth";
    private static final String SETTING_KEY_ENABLED = "key.{n}.enabled";
    private static final String SETTING_KEY_AUTH_ID = "key.{n}.auth_id";
    private static final String SETTING_KEY_SESSION = "key.{n}.last_session";

    private static final String SETTING_KEY_SALTS = "key.{n}.salts.count";
    private static final String SETTING_KEY_SALTS_VALUE = "key.{n}.salts.{d}";
    private static final String SETTING_KEY_SALTS_SINCE = "key.{n}.salts.{d}.since";
    private static final String SETTING_KEY_SALTS_UNTIL = "key.{n}.salts.{d}.until";

    private EngineStorage storage;

    public ApiState(EngineStorage storage) {
        this.storage = storage;

        if (isAuthenticated()) {
            DcInfo dcInfo = getDcState(getAuthenticatedDc());
            if (dcInfo == null) {
                this.storage.putBool(SETTING_AUTHENTICATED, false);
            } else {
                AuthKey authKey = getAuthKey(getAuthenticatedDc());
                if (authKey == null || !authKey.isLoggedIn()) {
                    this.storage.putBool(SETTING_AUTHENTICATED, false);
                }
            }
        }
    }

    public AuthKey getAuthKey(int dcId) {
        if (storage.containsKey(SETTING_KEY_AUTH.replace("{n}", "" + dcId)) &&
                storage.containsKey(SETTING_KEY_AUTH_ID.replace("{n}", "" + dcId))) {
            byte[] authKey = storage.getBytes(SETTING_KEY_AUTH.replace("{n}", "" + dcId));
            byte[] authKeyId = storage.getBytes(SETTING_KEY_AUTH_ID.replace("{n}", "" + dcId));
            if (!arrayEq(authKey, authKeyId)) {
                return null;
            }
            byte[] lastSession = storage.getBytes(SETTING_KEY_SESSION.replace("{n}", "" + dcId));
            boolean isAuthenticated = storage.getBool(SETTING_KEY_ENABLED.replace("{n}", "" + dcId));

            ServerSalt[] salts = new ServerSalt[storage.getInt(SETTING_KEY_SALTS.replace("{n}", "" + dcId), 0)];
            for (int i = 0; i < salts.length; i++) {
                int since = storage.getInt(SETTING_KEY_SALTS_SINCE
                        .replace("{n}", "" + dcId)
                        .replace("{d}", "" + i), 0);
                int until = storage.getInt(SETTING_KEY_SALTS_UNTIL
                        .replace("{n}", "" + dcId)
                        .replace("{d}", "" + i), Integer.MAX_VALUE - 1);

                byte[] salt = storage.getBytes(SETTING_KEY_SALTS_VALUE
                        .replace("{n}", "" + dcId)
                        .replace("{d}", "" + i));
                salts[i] = new ServerSalt(salt, since, until);
            }

            return new AuthKey(isAuthenticated, authKey, lastSession, salts);
        } else {
            return null;
        }
    }

    public DcInfo getDcState(int dcId) {
        if (storage.getBool(SETTING_DC_AVAILABLE.replace("{n}", dcId + ""))) {
            String ip = storage.getString(SETTING_DC_ADDRESS.replace("{n}", dcId + ""), null);
            int port = storage.getInt(SETTING_DC_PORT.replace("{n}", dcId + ""), 0);
            AuthKey dcAuthKey = getAuthKey(dcId);
            return new DcInfo(dcAuthKey != null && dcAuthKey.isLoggedIn(), ip, port);
        } else {
            return null;
        }
    }

    public int getAuthenticatedDc() {
        return this.storage.getInt(SETTING_AUTH_DC, 0);
    }

    public boolean isAuthenticated() {
        return this.storage.getBool(SETTING_AUTHENTICATED, false);
    }

    public boolean isBootstrapped() {
        return storage.getBool(SETTING_DC_BOOTSTRAPPED);
    }

    public void addDcInfo(int dcId, String address, int port) {
        storage.putBool(SETTING_DC_AVAILABLE.replace("{n}", "" + dcId), true);
        storage.putString(SETTING_DC_ADDRESS.replace("{n}", "" + dcId), address);
        storage.putInt(SETTING_DC_PORT.replace("{n}", "" + dcId), port);
    }

    public void putNewAuthKey(int dcId, byte[] authKey, byte[] session, byte[] salt) {
        storage.putBytes(SETTING_KEY_AUTH.replace("{n}", "" + dcId), authKey);
        storage.putBytes(SETTING_KEY_AUTH_ID.replace("{n}", "" + dcId), authKey);
        storage.putBool(SETTING_KEY_ENABLED.replace("{n}", "" + dcId), false);
        storage.putBytes(SETTING_KEY_SESSION.replace("{n}", "" + dcId), session);

        storage.putInt(SETTING_KEY_SALTS.replace("{n}", "" + dcId), 1);
        storage.putInt(SETTING_KEY_SALTS_SINCE.replace("{n}", "" + dcId).replace("{d}", "0"), 0);
        storage.putInt(SETTING_KEY_SALTS_UNTIL.replace("{n}", "" + dcId).replace("{d}", "0"), Integer.MAX_VALUE - 1);
        storage.putBytes(SETTING_KEY_SALTS_VALUE.replace("{n}", "" + dcId).replace("{d}", "0"), salt);
    }

    public void markAuthenticated(int dcId) {
        AuthKey key = getAuthKey(dcId);
        if (key != null && !key.isLoggedIn()) {
            storage.putBool(SETTING_KEY_ENABLED.replace("{n}", "" + dcId), true);
        }
    }

    public void markNonAuthenticated(int dcId) {
        AuthKey key = getAuthKey(dcId);
        if (key != null && key.isLoggedIn()) {
            storage.putBool(SETTING_KEY_ENABLED.replace("{n}", "" + dcId), false);
        }
    }
}
