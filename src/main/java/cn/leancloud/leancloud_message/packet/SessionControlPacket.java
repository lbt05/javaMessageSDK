package cn.leancloud.leancloud_message.packet;

import java.util.Collection;
import java.util.List;

import cn.leancloud.leancloud_message.Signature;

import com.avos.avoscloud.AVInstallation;
import com.avos.avoscloud.AVUtils;
import com.avos.avoscloud.internal.InternalConfigurationController;

/**
 * Created by nsun on 4/24/14.
 */
public class SessionControlPacket extends PeerBasedCommandPacket {

  public static class SessionControlOp {
    public static final String OPEN = "open";

    public static final String ADD = "add";

    public static final String REMOVE = "remove";

    public static final String CLOSE = "close";

    public static final String QUERY = "query";

    public static final String OPENED = "opened";

    public static final String ADDED = "added";

    public static final String QUERY_RESULT = "query_result";

    public static final String REMOVED = "removed";

    public static final String CLOSED = "closed";

    public static final String SESSION_TOKEN = "st";

    public static final String SESSION_TOKEN_TTL = "stTtl";
  }

  public static final String USERAGENT = InternalConfigurationController.globalInstance()
      .getClientConfiguration().getUserAgent();

  public SessionControlPacket() {
    this.setCmd("session");
  }

  private String op;

  private Collection<String> sessionPeerIds;

  public String getSignature() {
    return signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

  private String signature;

  private long timestamp;

  private String nonce;

  private boolean reconnectionRequest = false;

  String tag;
  String sessionToken;

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }

  public String getOp() {
    return op;
  }

  public void setOp(String op) {
    this.op = op;
  }

  public String getSessionToken() {
    return sessionToken;
  }

  public void setSessionToken(String sessionToken) {
    this.sessionToken = sessionToken;
  }

  public Collection<String> getSessionPeerIds() {
    return sessionPeerIds;
  }

  public void setSessionPeerIds(Collection<String> sessionPeerIds) {
    this.sessionPeerIds = sessionPeerIds;
  }

  public boolean isReconnectionRequest() {
    return reconnectionRequest;
  }

  public void setReconnectionRequest(boolean reconnectionRequest) {
    this.reconnectionRequest = reconnectionRequest;
  }

  @Override
  protected Messages.GenericCommand.Builder getGenericCommandBuilder() {
    Messages.GenericCommand.Builder builder = super.getGenericCommandBuilder();
    builder.setOp(Messages.OpType.valueOf(op));
    builder.setSessionMessage(getSessionCommand());
    return builder;
  }

  protected Messages.SessionCommand getSessionCommand() {
    Messages.SessionCommand.Builder builder = Messages.SessionCommand.newBuilder();

    if (sessionPeerIds != null && !sessionPeerIds.isEmpty()) {
      builder.addAllSessionPeerIds(sessionPeerIds);
    }

    if (op.equals(SessionControlOp.OPEN)) {
      builder.setUa(USERAGENT);
      builder.setDeviceId(AVInstallation.getCurrentInstallation().getInstallationId());
      if (!AVUtils.isBlankString(tag)) {
        builder.setTag(tag);
      }
    }

    if (getSignature() != null) {
      builder.setS(getSignature());
      builder.setT(getTimestamp());
      builder.setN(getNonce());
    }

    if (reconnectionRequest) {
      builder.setR(true);
    }

    if (!AVUtils.isBlankString(sessionToken)) {
      builder.setSt(sessionToken);
    }
    return builder.build();
  }


  public static SessionControlPacket genSessionCommand(String selfId, List<String> peers,
      String op, Signature signature) {

    SessionControlPacket scp = new SessionControlPacket();
    scp.setAppId(InternalConfigurationController.globalInstance().getAppConfiguration()
        .getApplicationId());
    scp.setPeerId(selfId);
    if (!AVUtils.isEmptyList(peers)) {
      scp.setSessionPeerIds(peers);
    }
    scp.setOp(op);
    scp.setRequestId(SessionControlPacket.UNSUPPORTED_OPERATION);

    if (signature != null) {
      if (op.equals(SessionControlPacket.SessionControlOp.OPEN)
          || op.equals(SessionControlPacket.SessionControlOp.ADD)) {
        scp.setSignature(signature.getSignature());
        scp.setNonce(signature.getNonce());
        scp.setTimestamp(signature.getTimestamp());
      }
    }
    return scp;
  }

  public static SessionControlPacket genSessionCommand(String selfId, List<String> peers,
      String op, Signature signature, int id) {
    SessionControlPacket scp = genSessionCommand(selfId, peers, op, signature);
    scp.setRequestId(id);
    return scp;
  }

  public static SessionControlPacket genSessionCommand(String selfId, List<String> peers,
      String tag, String op, Signature signature, int id) {
    SessionControlPacket scp = genSessionCommand(selfId, peers, op, signature);
    scp.setRequestId(id);
    scp.tag = tag;
    return scp;
  }

  public static SessionControlPacket genSessionCommand(String selfId, String op, String sessionToken) {
    SessionControlPacket scp = genSessionCommand(selfId, null, op, null);
    scp.setSessionToken(sessionToken);
    return scp;
  }
}
