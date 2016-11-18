package cn.leancloud.leancloud_message;

import java.util.List;

import com.avos.avoscloud.internal.InternalConfigurationController;

public class CustomConversationEventHandler extends AVIMConversationEventHandler {

  @Override
  public void onMemberLeft(AVIMClient client, AVIMConversation conversation, List<String> members,
      String kickedBy) {
    InternalConfigurationController
        .globalInstance()
        .getInternalLogger()
        .d(CustomConversationEventHandler.class.getName(),
            members + " lefted conversation:" + conversation.getConversationId());
  }

  @Override
  public void onMemberJoined(AVIMClient client, AVIMConversation conversation,
      List<String> members, String invitedBy) {
    InternalConfigurationController
        .globalInstance()
        .getInternalLogger()
        .d(CustomConversationEventHandler.class.getName(),
            members + " joined conversation:" + conversation.getConversationId());
  }

  @Override
  public void onKicked(AVIMClient client, AVIMConversation conversation, String kickedBy) {
    InternalConfigurationController
        .globalInstance()
        .getInternalLogger()
        .d(CustomConversationEventHandler.class.getName(),
            " you're kicked from conversation:" + conversation.getConversationId() + " by "
                + kickedBy);
  }

  @Override
  public void onInvited(AVIMClient client, AVIMConversation conversation, String operator) {
    InternalConfigurationController
        .globalInstance()
        .getInternalLogger()
        .d(CustomConversationEventHandler.class.getName(),
            " you're invited to conversation:" + conversation.getConversationId() + " by "
                + operator);
  }

}
