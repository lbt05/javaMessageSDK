package cn.leancloud.leancloud_message.callback;

import java.util.List;

import cn.leancloud.leancloud_message.AVIMConversation;
import cn.leancloud.leancloud_message.AVIMException;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

/**
 * 从AVIMClient查询AVIMConversation时的回调抽象类
 */
public abstract class AVIMConversationQueryCallback extends AVCallback<List<AVIMConversation>> {

  public abstract void done(List<AVIMConversation> conversations, AVIMException e);

  @Override
  protected final void internalDone0(List<AVIMConversation> returnValue, AVException e) {
    done(returnValue, AVIMException.wrapperAVException(e));
  }

}
