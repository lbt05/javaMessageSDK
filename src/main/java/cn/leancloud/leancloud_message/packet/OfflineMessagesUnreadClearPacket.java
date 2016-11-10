package cn.leancloud.leancloud_message.packet;

import com.avos.avoscloud.internal.InternalConfigurationController;

/**
 * Created by wli on 15/10/15. 清除离线消息通知
 */
public class OfflineMessagesUnreadClearPacket extends PeerBasedCommandPacket {

  String conversationId;

  public OfflineMessagesUnreadClearPacket() {
    this.setCmd("read");
  }

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
  }

  @Override
  protected Messages.GenericCommand.Builder getGenericCommandBuilder() {
    Messages.GenericCommand.Builder builder = super.getGenericCommandBuilder();
    builder.setReadMessage(getReadCommand());
    return builder;
  }

  protected Messages.ReadCommand getReadCommand() {
    Messages.ReadCommand.Builder builder = Messages.ReadCommand.newBuilder();
    builder.setCid(conversationId);
    return builder.build();
  }

  public static OfflineMessagesUnreadClearPacket getUnreadClearPacket(String peerId,
      String conversationId) {
    OfflineMessagesUnreadClearPacket packet = new OfflineMessagesUnreadClearPacket();
    packet.setAppId(InternalConfigurationController.globalInstance().getAppConfiguration()
        .getApplicationId());
    packet.setPeerId(peerId);
    packet.setConversationId(conversationId);
    return packet;
  }
}
