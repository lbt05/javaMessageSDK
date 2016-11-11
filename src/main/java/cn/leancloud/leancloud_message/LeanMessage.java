package cn.leancloud.leancloud_message;

import java.util.concurrent.TimeUnit;

import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;

import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVOSCloud;

public class LeanMessage {
  static ExpiringMap<Integer, AVIMBaseBroadcastReceiver> receivers;

  /**
   * 
   * @param applicationId The application id provided in the AVOSCloud dashboard.
   * @param clientKey The client key provided in the AVOSCloud dashboard.
   */
  public static void initialize(String applicationId, String clientKey) {
    AVOSCloud.initialize(applicationId, clientKey, null);
    receivers =
        ExpiringMap.builder().expiration(AVIMClient.timeoutInSecs, TimeUnit.SECONDS)
            .asyncExpirationListener(new ExpirationListener<Integer, AVIMBaseBroadcastReceiver>() {

              @Override
              public void expired(Integer requestId, AVIMBaseBroadcastReceiver receiver) {
                Intent intent = new Intent();
                intent.putExtra(Conversation.callbackExceptionKey, new AVIMException(
                    AVException.TIMEOUT, "Timeout Exception"));
                receiver.onReceive(intent);
              }
            }).build();
    AVIMServer server = AVIMServer.getInstance();
    server.start();
  }
}
