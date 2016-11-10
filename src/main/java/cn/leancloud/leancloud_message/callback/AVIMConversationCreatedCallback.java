package cn.leancloud.leancloud_message.callback;

import cn.leancloud.leancloud_message.AVIMConversation;
import cn.leancloud.leancloud_message.AVIMException;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

public abstract class AVIMConversationCreatedCallback extends AVCallback<AVIMConversation> {
  public abstract void done(AVIMConversation conversation, AVIMException e);

  @Override
  protected final void internalDone0(AVIMConversation returnValue, AVException e) {
    done(returnValue, AVIMException.wrapperAVException(e));
  }
}
