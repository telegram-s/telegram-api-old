package org.telegram.api;

import org.telegram.api.engine.*;
import org.telegram.api.engine.storage.ApiState;
import org.telegram.api.engine.storage.EngineStorage;
import org.telegram.api.engine.storage.StorageInterface;
import org.telegram.api.requests.TLRequestAuthSendCode;
import org.telegram.api.requests.TLRequestHelpGetConfig;
import org.telegram.mtproto.pq.Authorizer;
import org.telegram.mtproto.pq.PqAuth;
import org.telegram.mtproto.secure.Entropy;
import org.telegram.mtproto.time.TimeOverlord;
import org.telegram.mtproto.tl.MTPing;

import java.io.*;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 04.11.13
 * Time: 21:46
 */
public class Main {
    public static void main(String[] args) throws IOException {

        ApiState apiState = new ApiState(new EngineStorage(new StorageInterface() {
            @Override
            public void writeAll(HashMap<String, byte[]> data) {
                try {
                    FileOutputStream stream = new FileOutputStream("state.ini");
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(stream);
                    objectOutputStream.writeObject(data);
                    objectOutputStream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public HashMap<String, byte[]> readAll() {
                try {
                    FileInputStream stream = new FileInputStream("state.ini");
                    ObjectInputStream objectInputStream = new ObjectInputStream(stream);
                    HashMap<String, byte[]> res = (HashMap<String, byte[]>) objectInputStream.readObject();
                    objectInputStream.close();
                    return res;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return new HashMap<String, byte[]>();
            }
        }));

        // "173.240.5.1", 443

        Authorizer authorizer = new Authorizer();

        PqAuth auth = authorizer.doAuth("173.240.5.1", 443);

        TimeOverlord.getInstance().setTimeDelta(24 * 60 * 60 * 1000L, Long.MAX_VALUE);

        apiState.putNewAuthKey(1, auth.getAuthKey(), Entropy.generateSeed(8), auth.getServerSalt());
        apiState.addDcInfo(1, "173.240.5.1", 443);

        TelegramApi telegramApi = new TelegramApi(apiState, 1);

        TLConfig config = telegramApi.doRpcCall(new TLRequestHelpGetConfig());
        for (TLDcOption option : config.dcOptions) {
            apiState.addDcInfo(option.id, option.ipAddress, option.port);
        }

        for (int i = 0; i < 20; i++) {
            telegramApi.doRpcCall(new TLRequestHelpGetConfig());
        }

        telegramApi.getMainProto().closeConnections();
        // telegramApi.getMainProto().sendMessage(new MTPing(0), 15 * 1000L, false);
    }
}