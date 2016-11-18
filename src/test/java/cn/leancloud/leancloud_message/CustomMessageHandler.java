package cn.leancloud.leancloud_message;

import com.avos.avoscloud.internal.InternalConfigurationController;

public class CustomMessageHandler extends AVIMMessageHandler {
  @Override
  public void onMessage(AVIMMessage message, AVIMConversation conversation, AVIMClient client) {
    InternalConfigurationController
        .globalInstance()
        .getInternalLogger()
        .e(CustomMessageHandler.class.getName(),
            client.getClientId() + " have msg:" + message.getContent() + "  from "
                + conversation.getConversationId());
  }

  @Override
  public void onMessageReceipt(AVIMMessage message, AVIMConversation conversation, AVIMClient client) {
    InternalConfigurationController
        .globalInstance()
        .getInternalLogger()
        .e(CustomMessageHandler.class.getName(),
            client.getClientId() + " sent msg:" + message.getContent() + " received in "
                + conversation.getConversationId());
  }
}
