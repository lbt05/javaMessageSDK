package cn.leancloud.leancloud_message.packet;

import com.alibaba.fastjson.JSONObject;
import com.avos.avoscloud.AVUtils;
import com.avos.avoscloud.internal.InternalConfigurationController;

public class ConversationQueryPacket extends PeerBasedCommandPacket {

  int limit = 10;
  int skip = 0;
  String sort;
  JSONObject where;

  public ConversationQueryPacket() {
    this.setCmd("conv");
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  public int getSkip() {
    return skip;
  }

  public void setSkip(int skip) {
    this.skip = skip;
  }

  public String getSort() {
    return sort;
  }

  public void setSort(String sort) {
    this.sort = sort;
  }

  public JSONObject getWhere() {
    return where;
  }

  public void setWhere(JSONObject where) {
    this.where = where;
  }

  @Override
  protected Messages.GenericCommand.Builder getGenericCommandBuilder() {
    Messages.GenericCommand.Builder builder = super.getGenericCommandBuilder();
    builder.setConvMessage(getConvCommand());
    builder.setOp(Messages.OpType.valueOf(ConversationControlPacket.ConversationControlOp.QUERY));
    return builder;
  }

  protected Messages.ConvCommand getConvCommand() {
    Messages.ConvCommand.Builder builder = Messages.ConvCommand.newBuilder();
    if (where != null) {
      Messages.JsonObjectMessage.Builder messageBuild = Messages.JsonObjectMessage.newBuilder();
      messageBuild.setData(getWhere().toString());
      builder.setWhere(messageBuild);
    }
    if (!AVUtils.isBlankString(sort)) {
      builder.setSort(sort);
    }
    if (skip > 0) {
      builder.setSkip(skip);
    }

    if (limit != 10) {
      builder.setLimit(limit);
    }
    return builder.build();
  }

  public static ConversationQueryPacket getConversationQueryPacket(String peerId, JSONObject where,
      String sort, int skip, int limit, int requestId) {
    ConversationQueryPacket cqp = new ConversationQueryPacket();
    cqp.setAppId(InternalConfigurationController.globalInstance().getAppConfiguration()
        .getApplicationId());
    cqp.setPeerId(peerId);

    cqp.setLimit(limit);
    cqp.setSkip(skip);
    cqp.setSort(sort);
    cqp.setWhere(where);
    cqp.setRequestId(requestId);

    return cqp;
  }
}
