package cn.leancloud.leancloud_message.callback;

import cn.leancloud.leancloud_message.AVIMClient;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

/**
 * 获取AVIMClient当前连接状态的回调类 Created by lbt05 on 8/6/15.
 */
public abstract class AVIMClientStatusCallback extends AVCallback<AVIMClient.AVIMClientStatus> {
  public abstract void done(AVIMClient.AVIMClientStatus client);

  @Override
  protected void internalDone0(AVIMClient.AVIMClientStatus status, AVException parseException) {
    done(status);
  }
}
