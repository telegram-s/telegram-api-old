Java Telegram Api Library
============

This library allows you to make rpc calls to [Telegram](http://telegram.org).

Depends on [tl-core](https://github.com/ex3ndr/telegram-tl-core) and [mtproto](https://github.com/ex3ndr/telegram-mt) libraries.

Now it used in our production-ready product [Telegram S](https://play.google.com/store/apps/details?id=org.telegram.android).

[![Telegram build server](http://ci.81port.com/app/rest/builds/buildType:%28id:TelegramNetworking_JavaTelegramApi%29/statusIcon)](http://ci.81port.com/viewType.html?buildTypeId=TelegramNetworking_JavaTelegramApi)

Including in your project
------------
#### Dependencies

This project depends on [java MTProto implementation](https://github.com/ex3ndr/telegram-mt) and [tl-core library](https://github.com/ex3ndr/telegram-tl-core)

#### Binary
Download latest distribution at [releases page](https://github.com/ex3ndr/telegram-api/releases) and include jars from it to your project.

#### Building from source code

Project definded and gradle and expected to use IntelliJ IDEA 13 (now it is Beta) as IDE.

1. Checkout this repository to ````telegram-api```` folder
2. Checkout [mtproto java implementation](https://github.com/ex3ndr/telegram-mt) to ````mtproto```` folder
3. Checkout [tl core library](https://github.com/ex3ndr/telegram-tl-core) to ````tl-core```` folder
4. Execute ```gradle build``` at ```telegram-api``` folder

Usage of library
------------
#### Implement storage

You might to implement storage class for working with api. This storage used for saving state of telegram api across execution instances.

Extend class ````org.telegram.api.engine.storage.AbsApiState```` and implement suitable methods.

#### Generating keys
In your storage might be information about keys in current datacenter.
Keys might be generated manualy by using ````org.telegram.mtproto.pq.Authorizer class````.

#### RPC calls
Now you have proper key for accessing telegram api.

```java
TelegramApi api = new TelegramApi(new MyApiStorage(), new AppInfo(... put application information here...), new ApiCallback()
{
  @Override
  public void onApiDies(TelegramApi api) {
    // When auth key or user authorization dies
  }
  @Override
  public void onUpdatesInvalidated(TelegramApi api) {
    // When api engine expects that update sequence might be broken  
  }
});

// Syncronized call
// All request objects are in org.telegram.api.requests package
TLConfig config = api.doRpcCall(new TLRequestHelpGetConfig());

// Standart async call
api.doRpcCall(new TLRequestHelpGetConfig(), new RpcCallback<TLConfig>()
{
  public void onResult(TLConfig result)
  {
    
  }

  public void onError(int errorCode, String message)
  {
    // errorCode == 0 if request timeouted  
  }
});

// Priority async call
// Such rpc call executed with high pripory and sends to server as fast as possible this may improve message delivery speed
api.doRpcCall(new TLRequestHelpGetConfig(), new RpcCallbackEx<TLConfig>()
{
  public void onConfirmed()
  {
    // when message was received by server
  }

  public void onResult(TLConfig result)
  {
    
  }
  
  public void onError(int errorCode, String message)
  {
    // errorCode == 0 if request timeouted  
  }
});

// File operations
// Method that downloads file part. Automaticaly managed connections for file operations, automaticaly create keys for dc if there is no one.
api.doGetFile(...)
// Uploads file part
api.doSaveFilePart(...)
```

More information
----------------
####Telegram project

http://telegram.org/

#### Telegram api documentation

English: http://core.telegram.org/api

Russian: http://dev.stel.com/api

#### MTProto documentation

English: http://core.telegram.org/mtproto

Russian: http://dev.stel.com/mtproto

#### Type Language documentation

English: http://core.telegram.org/mtproto/TL

Russian: http://dev.stel.com/mtproto/TL

#### Android Client that uses this library

[![Telegram S](https://developer.android.com/images/brand/en_generic_rgb_wo_45.png)](https://play.google.com/store/apps/details?id=org.telegram.android "Telegram S")

Licence
----------------
Project uses [MIT Licence](LICENCE)
