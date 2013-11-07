package org.telegram.api.engine.storage;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 06.11.13
 * Time: 22:31
 */
public class EngineStorage {
    private HashMap<String, byte[]> storage = new HashMap<String, byte[]>();

    private StorageInterface storageInterface;

    public EngineStorage(StorageInterface storageInterface) {
        this.storageInterface = storageInterface;
        this.storage = (HashMap<String, byte[]>) storageInterface.readAll().clone();
    }

    public void putBool(String key, boolean value) {
        putString(key, "" + value);
    }

    public void putInt(String key, int value) {
        putString(key, "" + value);
    }

    public void putString(String key, String value) {
        putBytes(key, value.getBytes());
    }

    public void putBytes(String key, byte[] data) {
        storage.put(key, data);
        this.storageInterface.writeAll(storage);
    }

    public boolean containsKey(String key) {
        return storage.containsKey(key);
    }

    public byte[] getBytes(String key) {
        return getBytes(key, null);
    }

    public byte[] getBytes(String key, byte[] def) {
        if (storage.containsKey(key)) {
            return storage.get(key);
        } else {
            return def;
        }
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String def) {
        byte[] data = getBytes(key);
        if (data == null) {
            return def;
        } else {
            return new String(data);
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(getString(key, "" + def));
    }

    public boolean getBool(String key) {
        return getBool(key, false);
    }

    public int getInt(String key, int def) {
        return Integer.parseInt(getString(key, "" + def));
    }
}
