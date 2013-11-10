package org.telegram.api.engine;

import org.telegram.api.TLAbsUpdates;
import org.telegram.api.TLApiContext;
import org.telegram.api.TLConfig;
import org.telegram.api.auth.TLExportedAuthorization;
import org.telegram.api.engine.storage.AbsApiState;
import org.telegram.api.requests.*;
import org.telegram.api.upload.TLFile;
import org.telegram.mtproto.CallWrapper;
import org.telegram.mtproto.MTProto;
import org.telegram.mtproto.MTProtoCallback;
import org.telegram.mtproto.log.Logger;
import org.telegram.mtproto.pq.Authorizer;
import org.telegram.mtproto.pq.PqAuth;
import org.telegram.mtproto.state.ConnectionInfo;
import org.telegram.tl.TLBool;
import org.telegram.tl.TLBoolTrue;
import org.telegram.tl.TLMethod;
import org.telegram.tl.TLObject;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 04.11.13
 * Time: 21:54
 */
public class TelegramApi {

    private static final String TAG = "TelegramApi";

    private static final int DEFAULT_TIMEOUT_CHECK = 15000;
    private static final int DEFAULT_TIMEOUT = 15000;
    private static final int FILE_TIMEOUT = 45000;

    private boolean isClosed;

    private int primaryDc;

    private MTProto mainProto;

    private UpdateCatcher updateCatcher;

    private final HashMap<Integer, MTProto> streamingProtos = new HashMap<Integer, MTProto>();
    private final HashMap<Integer, Object> streamingSync = new HashMap<Integer, Object>();

    private ProtoCallback callback;

    private final HashMap<Integer, RpcCallbackWrapper> callbacks = new HashMap<Integer, RpcCallbackWrapper>();
    private final HashMap<Integer, TLMethod> requestedMethods = new HashMap<Integer, TLMethod>();

    private TLApiContext apiContext;

    private CallbackThread timeoutThread;
    private final TreeMap<Long, Integer> timeoutTimes = new TreeMap<Long, Integer>();

    private HashSet<Integer> registeredInApi = new HashSet<Integer>();

    private AbsApiState state;
    private AppInfo appInfo;

    public TelegramApi(AbsApiState state, AppInfo _appInfo) {
        if (state.getAuthKey(state.getPrimaryDc()) == null) {
            throw new RuntimeException("ApiState might be in authenticated state for primaryDc");
        }
        this.appInfo = _appInfo;
        this.state = state;
        this.primaryDc = state.getPrimaryDc();
        this.callback = new ProtoCallback();
        this.apiContext = new TLApiContext();
        this.isClosed = false;
        this.timeoutThread = new CallbackThread();
        this.timeoutThread.start();

        mainProto = new MTProto(state.getMtProtoState(primaryDc), callback,
                new CallWrapper() {
                    @Override
                    public TLObject wrapObject(TLMethod srcRequest) {
                        return wrapForDc(primaryDc, srcRequest);
                    }
                });
    }

    private TLMethod wrapForDc(int dcId, TLMethod method) {
        if (registeredInApi.contains(dcId)) {
            return new TLRequestInvokeWithLayer9(method);
        }

        return new TLRequestInvokeWithLayer9(new TLRequestInitConnection(
                appInfo.getApiId(), appInfo.getDeviceModel(), appInfo.getSystemVersion(), appInfo.getAppVersion(), appInfo.getLangCode(), method));
    }

    public AbsApiState getState() {
        return state;
    }

    public UpdateCatcher getUpdateCatcher() {
        return updateCatcher;
    }

    public void setUpdateCatcher(UpdateCatcher updateCatcher) {
        this.updateCatcher = updateCatcher;
    }

    public TLApiContext getApiContext() {
        return apiContext;
    }

    protected void onMessageArrived(TLObject object) {
        if (object instanceof TLAbsUpdates) {
            if (updateCatcher != null) {
                updateCatcher.onUpdate((TLAbsUpdates) object);
            }
        } else {

        }
    }

    public boolean doSaveFilePart(long _fileId, int _filePart, byte[] _bytes) throws IOException {
        MTProto proto = waitForStreaming(primaryDc);
        TLBool res = doRpcCall(new TLRequestUploadSaveFilePart(_fileId, _filePart, _bytes), FILE_TIMEOUT, proto);
        return res instanceof TLBoolTrue;
    }

    public TLFile doGetFile(int dcId, org.telegram.api.TLAbsInputFileLocation _location, int _offset, int _limit) throws IOException {
        MTProto proto = waitForStreaming(dcId);
        return doRpcCall(new TLRequestUploadGetFile(_location, _offset, _limit), FILE_TIMEOUT, proto);
    }

    private MTProto waitForStreaming(final int dcId) throws IOException {
        if (isClosed) {
            throw new TimeoutException();
        }

        if (!state.isAuthenticated(primaryDc)) {
            throw new TimeoutException();
        }

        Object syncObj;
        synchronized (streamingSync) {
            syncObj = streamingSync.get(dcId);
            if (syncObj == null) {
                syncObj = new Object();
                streamingSync.put(dcId, syncObj);
            }
        }

        synchronized (syncObj) {
            MTProto proto;
            synchronized (streamingProtos) {
                proto = streamingProtos.get(dcId);
                if (proto != null) {
                    if (proto.isClosed()) {
                        streamingProtos.remove(dcId);
                        proto = null;
                    }
                }
            }

            if (proto == null) {
                ConnectionInfo connectionInfo = state.getConnectionInfo(dcId);

                if (connectionInfo == null) {
                    TLConfig config = doRpcCall(new TLRequestHelpGetConfig());
                    state.updateSettings(config);
                    connectionInfo = state.getConnectionInfo(dcId);
                }

                if (connectionInfo == null) {
                    throw new TimeoutException();
                }

                if (dcId != primaryDc) {

                    if (state.isAuthenticated(dcId)) {
                        byte[] authKey = state.getAuthKey(dcId);
                        if (authKey == null) {
                            throw new TimeoutException();
                        }
                        proto = new MTProto(state.getMtProtoState(dcId), callback,
                                new CallWrapper() {
                                    @Override
                                    public TLObject wrapObject(TLMethod srcRequest) {
                                        return wrapForDc(dcId, srcRequest);
                                    }
                                });

                        if (!state.isAuthenticated(dcId)) {
                            TLExportedAuthorization exAuth = doRpcCall(new TLRequestAuthExportAuthorization(dcId));
                            doRpcCall(new TLRequestAuthImportAuthorization(exAuth.getId(), exAuth.getBytes()), DEFAULT_TIMEOUT, proto);
                        }

                        streamingProtos.put(dcId, proto);
                        return proto;
                    } else {
                        Authorizer authorizer = new Authorizer();
                        PqAuth auth = authorizer.doAuth(connectionInfo.getAddress(), connectionInfo.getPort());
                        if (auth == null) {
                            throw new TimeoutException();
                        }
                        state.putAuthKey(dcId, auth.getAuthKey());
                        state.setAuthenticated(dcId, false);
                        state.getMtProtoState(dcId).initialServerSalt(auth.getServerSalt());

                        byte[] authKey = state.getAuthKey(dcId);
                        if (authKey == null) {
                            throw new TimeoutException();
                        }

                        proto = new MTProto(state.getMtProtoState(dcId), callback,
                                new CallWrapper() {
                                    @Override
                                    public TLObject wrapObject(TLMethod srcRequest) {
                                        return wrapForDc(dcId, srcRequest);
                                    }
                                });

                        TLExportedAuthorization exAuth = doRpcCall(new TLRequestAuthExportAuthorization(dcId));

                        doRpcCall(new TLRequestAuthImportAuthorization(exAuth.getId(), exAuth.getBytes()), DEFAULT_TIMEOUT, proto);

                        state.setAuthenticated(dcId, true);

                        streamingProtos.put(dcId, proto);

                        return proto;
                    }
                } else {
                    byte[] authKey = state.getAuthKey(dcId);
                    if (authKey == null) {
                        throw new TimeoutException();
                    }
                    proto = new MTProto(state.getMtProtoState(dcId), callback,
                            new CallWrapper() {
                                @Override
                                public TLObject wrapObject(TLMethod srcRequest) {
                                    return wrapForDc(dcId, srcRequest);
                                }
                            });
                    streamingProtos.put(dcId, proto);
                    return proto;
                }
            }
            return proto;
        }
    }

    public int getPrimaryDc() {
        return primaryDc;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void close() {
        if (!this.isClosed) {
            this.isClosed = true;
            if (this.timeoutThread != null) {
                this.timeoutThread.interrupt();
                this.timeoutThread = null;
            }
            mainProto.close();
        }
    }

    public <T extends TLObject> void doRpcCall(TLMethod<T> method, RpcCallback<T> callback) {
        doRpcCall(method, DEFAULT_TIMEOUT, callback);
    }

    public <T extends TLObject> void doRpcCall(TLMethod<T> method, int timeout, RpcCallback<T> callback) {
        doRpcCall(method, timeout, callback, mainProto);
    }

    private <T extends TLObject> void doRpcCall(TLMethod<T> method, int timeout, RpcCallback<T> callback, MTProto destProto) {
        if (isClosed) {
            if (callback != null) {
                callback.onError(0, null);
            }
            return;
        }
        synchronized (callbacks) {
            boolean isHighPriority = callback != null && callback instanceof RpcCallbackEx;
            int rpcId = destProto.sendRpcMessage(method, DEFAULT_TIMEOUT, isHighPriority);
            if (callback != null) {
                callbacks.put(rpcId, new RpcCallbackWrapper(callback));
                requestedMethods.put(rpcId, method);
                long timeoutTime = System.nanoTime() + timeout * 1000 * 1000L;
                synchronized (timeoutTimes) {
                    while (timeoutTimes.containsKey(timeoutTime)) {
                        timeoutTime++;
                    }
                    timeoutTimes.put(timeoutTime, rpcId);
                    timeoutTimes.notifyAll();
                }
            }
        }
    }

    public <T extends TLObject> T doRpcCall(TLMethod<T> method) throws IOException {
        return doRpcCall(method, DEFAULT_TIMEOUT);
    }

    public <T extends TLObject> T doRpcCall(TLMethod<T> method, int timeout) throws IOException {
        return doRpcCall(method, timeout, mainProto);
    }

    public <T extends TLObject> T doRpcCall(TLMethod<T> method, int timeout, MTProto destProto) throws IOException {
        if (isClosed) {
            throw new TimeoutException();
        }
        final Object waitObj = new Object();
        final Object[] res = new Object[3];
        doRpcCall(method, timeout, new RpcCallback<T>() {
            @Override
            public void onResult(T result) {
                synchronized (waitObj) {
                    res[0] = result;
                    res[1] = null;
                    res[2] = null;
                    waitObj.notifyAll();
                }
            }

            @Override
            public void onError(int errorCode, String message) {
                synchronized (waitObj) {
                    res[0] = null;
                    res[1] = errorCode;
                    res[2] = message;
                    waitObj.notifyAll();
                }
            }
        }, destProto);

        synchronized (waitObj) {
            try {
                waitObj.wait(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new TimeoutException();
            }
        }

        if (res[0] == null) {
            if (res[1] != null) {
                Integer code = (Integer) res[1];
                if (code == 0) {
                    throw new TimeoutException();
                } else {
                    throw new RpcException(code, (String) res[2]);
                }
            } else {
                throw new RpcException(0, null);
            }
        } else {
            return (T) res[0];
        }
    }

    private class ProtoCallback implements MTProtoCallback {

        @Override
        public void onSessionCreated(MTProto proto) {
            if (isClosed) {
                return;
            }

            if (proto == mainProto) {
                registeredInApi.add(primaryDc);
            } else {
                for (Map.Entry<Integer, MTProto> p : streamingProtos.entrySet()) {
                    if (p.getValue() == proto) {
                        registeredInApi.add(p.getKey());
                        return;
                    }
                }
            }
        }

        @Override
        public void onAuthInvalidated(MTProto proto) {
            close();
        }

        @Override
        public void onApiMessage(byte[] message, MTProto proto) {
            if (isClosed) {
                return;
            }

            if (proto == mainProto) {
                registeredInApi.add(primaryDc);
            } else {
                for (Map.Entry<Integer, MTProto> p : streamingProtos.entrySet()) {
                    if (p.getValue() == proto) {
                        registeredInApi.add(p.getKey());
                        return;
                    }
                }
            }

            try {
                TLObject object = apiContext.deserializeMessage(message);
                onMessageArrived(object);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override
        public void onRpcResult(int callId, byte[] response, MTProto proto) {
            if (isClosed) {
                return;
            }

            if (proto == mainProto) {
                registeredInApi.add(primaryDc);
            } else {
                for (Map.Entry<Integer, MTProto> p : streamingProtos.entrySet()) {
                    if (p.getValue() == proto) {
                        registeredInApi.add(p.getKey());
                        return;
                    }
                }
            }

            try {
                RpcCallbackWrapper currentCallback;
                TLMethod method;
                synchronized (callbacks) {
                    currentCallback = callbacks.remove(callId);
                    method = requestedMethods.remove(callId);
                }
                if (currentCallback != null && method != null) {
                    TLObject object = method.deserializeResponse(response, apiContext);
                    synchronized (currentCallback) {
                        if (currentCallback.isCompleted) {
                            return;
                        } else {
                            currentCallback.isCompleted = true;
                        }
                    }
                    currentCallback.callback.onResult(object);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override
        public void onRpcError(int callId, int errorCode, String message, MTProto proto) {
            if (isClosed) {
                return;
            }

            if (errorCode == 400 && message != null && message.startsWith("CONNECTION_NOT_INITED")) {
                System.out.println("Error: CONNECTION_NOT_INITED");

                int dc = -1;
                if (proto == mainProto) {
                    dc = primaryDc;
                } else {
                    for (Map.Entry<Integer, MTProto> p : streamingProtos.entrySet()) {
                        if (p.getValue() == proto) {
                            dc = p.getKey();
                            break;
                        }
                    }
                }

                if (dc < 0) {
                    return;
                }

                registeredInApi.remove(dc);
                RpcCallbackWrapper currentCallback;
                TLMethod method;
                synchronized (callbacks) {
                    currentCallback = callbacks.remove(callId);
                    method = requestedMethods.remove(callId);
                }

                if (currentCallback != null && method != null) {
                    // Incorrect timeouts, but this is unreal case and we might at least continue working
                    int rpcId = proto.sendRpcMessage(method, DEFAULT_TIMEOUT, false);
                    callbacks.put(rpcId, currentCallback);
                    requestedMethods.put(rpcId, method);
                    long timeoutTime = System.nanoTime() + DEFAULT_TIMEOUT * 1000 * 1000L;
                    synchronized (timeoutTimes) {
                        while (timeoutTimes.containsKey(timeoutTime)) {
                            timeoutTime++;
                        }
                        timeoutTimes.put(timeoutTime, rpcId);
                        timeoutTimes.notifyAll();
                    }
                }
                return;
            } else {
                if (proto == mainProto) {
                    registeredInApi.add(primaryDc);
                } else {
                    for (Map.Entry<Integer, MTProto> p : streamingProtos.entrySet()) {
                        if (p.getValue() == proto) {
                            registeredInApi.add(p.getKey());
                            return;
                        }
                    }
                }
            }

            try {
                RpcCallbackWrapper currentCallback;
                TLMethod method;
                synchronized (callbacks) {
                    currentCallback = callbacks.remove(callId);
                    method = requestedMethods.remove(callId);
                }
                if (currentCallback != null) {
                    synchronized (currentCallback) {
                        if (currentCallback.isCompleted) {
                            return;
                        } else {
                            currentCallback.isCompleted = true;
                        }
                    }
                    currentCallback.callback.onError(errorCode, message);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override
        public void onConfirmed(int callId) {
            RpcCallbackWrapper currentCallback;
            synchronized (callbacks) {
                currentCallback = callbacks.get(callId);
            }
            if (currentCallback != null) {
                synchronized (currentCallback) {
                    if (currentCallback.isCompleted || currentCallback.isConfirmed) {
                        return;
                    } else {
                        currentCallback.isConfirmed = true;
                    }
                }
                if (currentCallback.callback instanceof RpcCallbackEx) {
                    ((RpcCallbackEx) currentCallback.callback).onConfirmed();
                }
            }
        }
    }

    private class CallbackThread extends Thread {
        public CallbackThread() {
            setName("Callback#" + hashCode());
        }

        @Override
        public void run() {
            while (!isClosed) {
                Logger.d(TAG, "Callback Iteration");
                Map.Entry<Long, Integer> entry = null;
                synchronized (timeoutTimes) {
                    entry = timeoutTimes.firstEntry();
                    if (entry == null) {
                        try {
                            timeoutTimes.wait(DEFAULT_TIMEOUT_CHECK);
                        } catch (InterruptedException e) {
                            // e.printStackTrace();
                        }
                        continue;
                    }
                    long delta = (entry.getKey() - System.nanoTime()) / (1000 * 1000);
                    if (delta > 0) {
                        try {
                            timeoutTimes.wait(delta);
                        } catch (InterruptedException e) {
                            // e.printStackTrace();
                        }
                        continue;
                    }

                    timeoutTimes.remove(entry.getKey());
                }

                RpcCallbackWrapper currentCallback;
                synchronized (callbacks) {
                    currentCallback = callbacks.remove(entry.getValue());
                }
                if (currentCallback != null) {
                    synchronized (currentCallback) {
                        if (currentCallback.isCompleted) {
                            return;
                        } else {
                            currentCallback.isCompleted = true;
                        }
                    }
                    currentCallback.callback.onError(0, null);
                }
            }
            synchronized (timeoutTimes) {
                for (Map.Entry<Long, Integer> entry : timeoutTimes.entrySet()) {
                    RpcCallbackWrapper currentCallback;
                    synchronized (callbacks) {
                        currentCallback = callbacks.remove(entry.getValue());
                    }
                    if (currentCallback != null) {
                        synchronized (currentCallback) {
                            if (currentCallback.isCompleted) {
                                return;
                            } else {
                                currentCallback.isCompleted = true;
                            }
                        }
                        currentCallback.callback.onError(0, null);
                    }
                }
            }
        }
    }

    private class RpcCallbackWrapper {
        public boolean isCompleted = false;
        public boolean isConfirmed = false;
        public RpcCallback callback;

        private RpcCallbackWrapper(RpcCallback callback) {
            this.callback = callback;
        }
    }
}