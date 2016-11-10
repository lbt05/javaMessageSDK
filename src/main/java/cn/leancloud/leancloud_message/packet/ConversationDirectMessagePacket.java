package cn.leancloud.leancloud_message.packet;

import com.avos.avoscloud.AVUtils;
import com.avos.avoscloud.internal.InternalConfigurationController;

public class ConversationDirectMessagePacket extends PeerBasedCommandPacket {
  String conversationId;
  boolean isReceipt;
  boolean isTransient;
  String message;
  String messageToken;

  public ConversationDirectMessagePacket() {
    this.setCmd("direct");
  }

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
  }

  public boolean isReceipt() {
    return isReceipt;
  }

  public void setReceipt(boolean isReceipt) {
    this.isReceipt = isReceipt;
  }

  public boolean isTransient() {
    return isTransient;
  }

  public void setTransient(boolean isTransient) {
    this.isTransient = isTransient;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  protected Messages.GenericCommand.Builder getGenericCommandBuilder() {
    Messages.GenericCommand.Builder builder = super.getGenericCommandBuilder();
    builder.setDirectMessage(getDirectCommand());
    return builder;
  }

  protected Messages.DirectCommand getDirectCommand() {
    Messages.DirectCommand.Builder builder = Messages.DirectCommand.newBuilder();
    builder.setMsg(message);
    builder.setCid(conversationId);
    if (isReceipt) {
      builder.setR(true);
    }
    if (isTransient) {
      builder.setTransient(isTransient);
    }
    if (!AVUtils.isBlankString(messageToken)) {
      builder.setDt(messageToken);
    }
    return builder.build();
  }

  public static ConversationDirectMessagePacket getConversationMessagePacket(String peerId,
      String conversationId, String msg, boolean isReceipt, boolean isTransient, int requestId) {
    ConversationDirectMessagePacket cdmp = new ConversationDirectMessagePacket();
    cdmp.setAppId(InternalConfigurationController.globalInstance().getAppConfiguration()
        .getApplicationId());
    cdmp.setPeerId(peerId);
    cdmp.setConversationId(conversationId);
    cdmp.setRequestId(requestId);
    cdmp.setTransient(isTransient);
    cdmp.setReceipt(isReceipt);
    cdmp.setMessage(msg);
    return cdmp;
  }

  public static ConversationDirectMessagePacket getConversationMessagePacket(String peerId,
      String conversationId, String msg, String messageToken, boolean isReceipt,
      boolean isTransient, int requestId) {
    ConversationDirectMessagePacket cdmp =
        getConversationMessagePacket(peerId, conversationId, msg, isReceipt, isTransient, requestId);
    cdmp.messageToken = messageToken;
    return cdmp;
  }

}
