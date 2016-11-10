package cn.leancloud.leancloud_message.callback;

import cn.leancloud.leancloud_message.AVIMException;
import cn.leancloud.leancloud_message.AVIMMessage;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;

/**
 * 针对某些明确知道只有一个消息返回的消息查询接口的回调
 *
 * 比如getLastMessage
 */
public abstract class AVIMSingleMessageQueryCallback extends AVCallback<AVIMMessage> {


  public abstract void done(AVIMMessage msg, AVIMException e);

  @Override
  protected final void internalDone0(AVIMMessage returnValue, AVException e) {
    done(returnValue, AVIMException.wrapperAVException(e));
  }
}
