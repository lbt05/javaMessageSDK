package cn.leancloud.leancloud_message;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

abstract class AVIMBaseBroadcastReceiver {
  AVCallback callback;

  public AVIMBaseBroadcastReceiver(AVCallback callback) {
    this.callback = callback;
  }

  public void onReceive(Intent intent) {
    try {
      Throwable error =
          intent == null ? null : (Throwable) intent.getExtras().get(
              Conversation.callbackExceptionKey);
      execute(intent, error);
    } catch (Exception e) {
      if (callback != null) {
        callback.internalDone(null, new AVException(e));
      }
    }
  }

  public abstract void execute(Intent intent, Throwable error);


  public static void register(int requestId, AVIMBaseBroadcastReceiver receiver) {
    LeanMessage.receivers.put(requestId, receiver);
  }

  public static AVIMBaseBroadcastReceiver removeReceiver(int requestId) {
    return LeanMessage.receivers.remove(requestId);
  }
}
