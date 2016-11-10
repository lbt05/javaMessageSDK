package cn.leancloud.leancloud_message.callback;

import java.util.List;

import cn.leancloud.leancloud_message.AVIMException;
import cn.leancloud.leancloud_message.AVIMMessage;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

/**
 * 消息记录查询的回调 Created by lbt05 on 2/3/15.
 */
public abstract class AVIMMessagesQueryCallback extends AVCallback<List<AVIMMessage>> {

  public abstract void done(List<AVIMMessage> messages, AVIMException e);

  @Override
  protected final void internalDone0(List<AVIMMessage> returnValue, AVException e) {
    done(returnValue, AVIMException.wrapperAVException(e));
  }
}
