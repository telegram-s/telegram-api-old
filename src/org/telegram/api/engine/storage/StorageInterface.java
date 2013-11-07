package org.telegram.api.engine.storage;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 2:13
 */
public interface StorageInterface {
    void writeAll(HashMap<String, byte[]> data);

    HashMap<String, byte[]> readAll();
}
