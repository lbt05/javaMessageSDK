package cn.leancloud.leancloud_message.messages;

import com.avos.avoscloud.SaveCallback;

/**
 * Created by lbt05 on 1/27/15.
 */
public class AVIMFileMessageAccessor {
  public static void upload(AVIMFileMessage message, SaveCallback callback) {
    message.upload(callback);
  }
}
