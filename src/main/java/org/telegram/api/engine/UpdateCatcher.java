package org.telegram.api.engine;

import org.telegram.api.TLAbsUpdates;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 08.11.13
 * Time: 14:17
 */
public interface UpdateCatcher {
    public void onUpdate(TLAbsUpdates updates);
}
