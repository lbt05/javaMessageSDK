package cn.leancloud.leancloud_message.packet;

import java.util.ArrayList;
import java.util.List;

import com.avos.avoscloud.AVUtils;

/**
 * Created by nsun on 5/13/14.
 */
public class SessionAckPacket extends PeerBasedCommandPacket {

  List<String> ids;

  public SessionAckPacket() {
    this.setCmd("ack");
  }

  public void setMessageId(String id) {
    ids = new ArrayList<String>(1);
    ids.add(id);
  }

  @Override
  protected Messages.GenericCommand.Builder getGenericCommandBuilder() {
    Messages.GenericCommand.Builder builder = super.getGenericCommandBuilder();
    builder.setAckMessage(getAckCommand());
    return builder;
  }

  protected Messages.AckCommand getAckCommand() {
    Messages.AckCommand.Builder builder = Messages.AckCommand.newBuilder();
    if (!AVUtils.isEmptyList(ids)) {
      builder.addAllIds(ids);
    }
    return builder.build();
  }
}
