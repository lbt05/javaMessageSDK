package cn.leancloud.leancloud_message.callback;

import cn.leancloud.leancloud_message.AVIMException;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

/**
 * 作为Conversation操作的回调抽象类
 */
public abstract class AVIMConversationCallback extends AVCallback<Void> {

  /**
   * Override this function with the code you want to run after the save is complete.
   *
   * @param e The exception raised by the save, or null if it succeeded.
   */
  public abstract void done(AVIMException e);

  @Override
  protected final void internalDone0(java.lang.Void returnValue, AVException e) {
    done(AVIMException.wrapperAVException(e));
  }
}
