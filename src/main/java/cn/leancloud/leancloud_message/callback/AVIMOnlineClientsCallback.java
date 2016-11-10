package cn.leancloud.leancloud_message.callback;

import java.util.List;

import cn.leancloud.leancloud_message.AVIMException;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

/**
 *
 * 作为AVIMClient方法中checkOnlineClients的回调方法
 */
public abstract class AVIMOnlineClientsCallback extends AVCallback<List<String>> {
  public abstract void done(List<String> object, AVIMException e);

  @Override
  protected final void internalDone0(List<String> object, AVException error) {
    this.done(object, AVIMException.wrapperAVException(error));
  }
}
