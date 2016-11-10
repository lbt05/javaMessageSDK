package cn.leancloud.leancloud_message;


import java.util.List;

import cn.leancloud.leancloud_message.callback.AVIMConversationCallback;

/**
 * 用于处理AVIMConversation中产生的事件 Created by lbt05 on 1/29/15.
 */
public abstract class AVIMConversationEventHandler extends AVIMEventHandler {
  /**
   * 实现本方法以处理聊天对话中的参与者离开事件
   *
   * @param client
   * @param conversation
   * @param members 离开的参与者
   * @param kickedBy 离开事件的发动者，有可能是离开的参与者本身
   * @since 3.0
   */

  public abstract void onMemberLeft(AVIMClient client, AVIMConversation conversation,
      List<String> members, String kickedBy);

  /**
   * 实现本方法以处理聊天对话中的参与者加入事件
   *
   * @param client
   * @param conversation
   * @param members 加入的参与者
   * @param invitedBy 加入事件的邀请人，有可能是加入的参与者本身
   * @since 3.0
   */

  public abstract void onMemberJoined(AVIMClient client, AVIMConversation conversation,
      List<String> members, String invitedBy);

  /**
   * 实现本方法来处理当前用户被踢出某个聊天对话事件
   *
   * @param client
   * @param conversation
   * @param kickedBy 踢出你的人
   * @since 3.0
   */

  public abstract void onKicked(AVIMClient client, AVIMConversation conversation, String kickedBy);

  /**
   * 实现本方法来处理当前用户被邀请到某个聊天对话事件
   *
   * @param client
   * @param conversation 被邀请的聊天对话
   * @param operator 邀请你的人
   * @since 3.0
   */
  public abstract void onInvited(AVIMClient client, AVIMConversation conversation, String operator);

  /**
   * 实现本地方法来处理离线消息数量的通知
   * 
   * @param client
   * @param conversation
   * @param unreadCount 未读消息数量
   */
  public void onOfflineMessagesUnread(AVIMClient client, AVIMConversation conversation,
      int unreadCount) {}

  @Override
  protected final void processEvent0(final int operation, final Object operator,
      final Object operand, Object eventScene) {
    final AVIMConversation conversation = (AVIMConversation) eventScene;
    if (conversation.isShouldFetch()) {
      conversation.fetchInfoInBackground(new AVIMConversationCallback() {
        @Override
        public void done(AVIMException e) {
          if (null != e && e.getCode() > 0) {
            conversation.latestConversationFetch = System.currentTimeMillis();
          }
          processConversationEvent(operation, operator, operand, conversation);
        }
      });
    } else {
      processConversationEvent(operation, operator, operand, conversation);
    }
  }

  private void processConversationEvent(int operation, Object operator, Object operand,
      AVIMConversation conversation) {
    switch (operation) {
      case Conversation.STATUS_ON_MEMBERS_LEFT:
        onMemberLeft(conversation.client, conversation, (List<String>) operand, (String) operator);
        break;
      case Conversation.STATUS_ON_MEMBERS_JOINED:
        onMemberJoined(conversation.client, conversation, (List<String>) operand, (String) operator);
        break;
      case Conversation.STATUS_ON_JOINED:
        onInvited(conversation.client, conversation, (String) operator);
        break;
      case Conversation.STATUS_ON_KICKED_FROM_CONVERSATION:
        onKicked(conversation.client, conversation, (String) operator);
        break;
      case Conversation.STATUS_ON_OFFLINE_UNREAD:
        onOfflineMessagesUnread(conversation.client, conversation, (Integer) operator);
    }
  }
}
