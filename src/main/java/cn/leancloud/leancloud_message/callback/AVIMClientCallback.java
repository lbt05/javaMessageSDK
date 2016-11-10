package cn.leancloud.leancloud_message.callback;

import cn.leancloud.leancloud_message.AVIMClient;
import cn.leancloud.leancloud_message.AVIMException;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

/**
 * Created by lbt05 on 3/19/15.
 */
public abstract class AVIMClientCallback extends AVCallback<AVIMClient> {
  public abstract void done(AVIMClient client, AVIMException e);

  @Override
  protected void internalDone0(AVIMClient client, AVException parseException) {
    done(client, AVIMException.wrapperAVException(parseException));
  }
}
