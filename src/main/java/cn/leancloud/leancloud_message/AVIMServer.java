package cn.leancloud.leancloud_message;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.leancloud.leancloud_message.AVIMMessage.AVIMMessageIOType;
import cn.leancloud.leancloud_message.AVIMMessage.AVIMMessageStatus;
import cn.leancloud.leancloud_message.AVIMRouter.RouterResponseListener;
import cn.leancloud.leancloud_message.AVIMWebSocketClient.AVSocketListener;
import cn.leancloud.leancloud_message.packet.CommandPacket;
import cn.leancloud.leancloud_message.packet.ConversationAckPacket;
import cn.leancloud.leancloud_message.packet.ConversationControlPacket;
import cn.leancloud.leancloud_message.packet.LoginPacket;
import cn.leancloud.leancloud_message.packet.Messages;
import cn.leancloud.leancloud_message.packet.Messages.ConvCommand;
import cn.leancloud.leancloud_message.packet.Messages.DirectCommand;
import cn.leancloud.leancloud_message.packet.Messages.ErrorCommand;
import cn.leancloud.leancloud_message.packet.Messages.LogsCommand;
import cn.leancloud.leancloud_message.packet.Messages.RcpCommand;
import cn.leancloud.leancloud_message.packet.Messages.SessionCommand;
import cn.leancloud.leancloud_message.packet.Messages.UnreadCommand;
import cn.leancloud.leancloud_message.packet.OfflineMessagesUnreadClearPacket;
import cn.leancloud.leancloud_message.packet.SessionControlPacket;

import com.alibaba.fastjson.JSON;
import com.avos.avoscloud.AVInstallation;
import com.avos.avoscloud.AVOSCloud;
import com.avos.avoscloud.AVUtils;
import com.avos.avoscloud.LogUtil;
import com.avos.avoscloud.internal.InternalConfigurationController;
import com.google.protobuf.InvalidProtocolBufferException;



class AVIMServer implements AVSocketListener {

  private AVIMWebSocketClient socketClient;
  private static AVIMServer server = new AVIMServer();
  private static AVIMRouter router;

  private AVIMServer() {
    // get push server from appRouter
    // connect to server
    router = new AVIMRouter(new RouterResponseListener() {

      @Override
      public void onServerAddress(String address) {
        if (!AVUtils.isBlankString(address)) {
          createNewWebSocket(address);
        } else {
          processRemoteServerNotAvailable();
        }
      }
    });
  }

  private synchronized void createNewWebSocket(final String pushServer) {
    if (socketClient == null || socketClient.isClosed()) {
      // 由于需要链接到新的server address上,原来的client就要被抛弃了,抛弃前需要取消自动重连的任务
      if (socketClient != null) {
        socketClient.destroy();
      }

      if (AVIMClient.onlyPushCount) {
        socketClient = new AVIMWebSocketClient(URI.create(pushServer), this, "lc.protobuf.2", true);
      } else {
        socketClient = new AVIMWebSocketClient(URI.create(pushServer), this, "lc.protobuf.1", true);
      }

      if (AVOSCloud.isDebugLogEnabled()) {
        LogUtil.avlog.d("About to connect to server.");
      }
      socketClient.connect();
    }
  }

  public static AVIMServer getInstance() {
    return server;
  }

  public void sendData(CommandPacket cp) {
    int requestId = cp.getRequestId();
    if (socketClient.isOpen()) {
      socketClient.send(cp);
    } else {
      AVIMBaseBroadcastReceiver receiver = AVIMBaseBroadcastReceiver.removeReceiver(requestId);
      Intent intent = new Intent();
      intent.putExtra(Conversation.callbackExceptionKey, new RuntimeException("Connection Lost"));
      receiver.onReceive(intent);
    }
  }

  @Override
  public void loginCmd() {
    LoginPacket lp = new LoginPacket();
    lp.setAppId(InternalConfigurationController.globalInstance().getAppConfiguration()
        .getApplicationId());
    lp.setInstallationId(AVInstallation.getCurrentInstallation().getInstallationId());
    socketClient.send(lp);
  }

  @Override
  public void processCommand(ByteBuffer bytes) {
    try {
      Messages.GenericCommand command = Messages.GenericCommand.parseFrom(bytes.array());
      if (AVOSCloud.isDebugLogEnabled()) {
        LogUtil.avlog.d("downlink : " + command.toString());
      }

      String peerId = command.getPeerId();
      String operation = command.getOp().name();
      Integer requestKey = command.hasI() ? command.getI() : null;
      if (!AVIMClient.clients.isEmpty()) {
        switch (command.getCmd().getNumber()) {
          case Messages.CommandType.direct_VALUE:
            processDirectCommand(peerId, command.getDirectMessage());
            break;
          case Messages.CommandType.session_VALUE:
            processClientCommand(peerId, command.getOp().name(), requestKey,
                command.getSessionMessage());
            break;
          case Messages.CommandType.ack_VALUE:
            processAckCommand(peerId, requestKey, command.getAckMessage());
            break;
          case Messages.CommandType.rcp_VALUE:
            processRcpCommand(peerId, command.getRcpMessage());
            break;
          case Messages.CommandType.conv_VALUE:
            processConvCommand(peerId, command.getOp().name(), requestKey, command.getConvMessage());
            break;
          case Messages.CommandType.error_VALUE:
            processErrorCommand(peerId, requestKey, command.getErrorMessage());
            break;
          case Messages.CommandType.logs_VALUE:
            processLogsCommand(peerId, requestKey, command.getLogsMessage());
            break;
          case Messages.CommandType.read_VALUE:
            break;
          case Messages.CommandType.unread_VALUE:
            processUnreadCommand(peerId, command.getUnreadMessage());
            break;
          default:
            break;
        }
      }
    } catch (InvalidProtocolBufferException e) {
      e.printStackTrace();
    }
  }

  private void processUnreadCommand(String peerId, UnreadCommand unreadCommand) {
    if (AVIMClient.onlyPushCount && unreadCommand.getConvsCount() > 0) {
      List<Messages.UnreadTuple> unreadTupleList = unreadCommand.getConvsList();
      if (null != unreadTupleList) {
        List<String> conversationList = new ArrayList<String>();
        AVIMClient client = AVIMClient.getInstance(peerId);
        for (Messages.UnreadTuple unreadTuple : unreadTupleList) {
          String conversationId = unreadTuple.getCid();
          AVIMConversationEventHandler handler = AVIMMessageManager.getConversationEventHandler();
          if (handler != null) {
            AVIMConversation conversation = client.getConversation(conversationId);
            handler.processEvent(Conversation.STATUS_ON_OFFLINE_UNREAD, unreadTuple.getUnread(),
                null, conversation);
          }
          conversationList.add(conversationId);
        }
        sendClearUnreadAck(peerId, conversationList);
      }
    }
  }

  /**
   * 清除离线消息，接收到离线消息后发送
   */
  private void sendClearUnreadAck(String clientId, List<String> conversationList) {
    if (AVIMClient.onlyPushCount && null != conversationList) {
      for (String conversationId : conversationList) {
        OfflineMessagesUnreadClearPacket packet =
            OfflineMessagesUnreadClearPacket.getUnreadClearPacket(clientId, conversationId);
        sendData(packet);
      }
    }
  }

  private void processLogsCommand(String peerId, Integer requestKey, LogsCommand logsMessage) {
    ArrayList<AVIMMessage> messageList = new ArrayList<AVIMMessage>();
    for (Messages.LogItem item : logsMessage.getLogsList()) {
      String from = item.getFrom();
      Object data = item.getData();
      long timestamp = item.getTimestamp();
      String msgId = item.getMsgId();
      AVIMMessage message = new AVIMMessage(logsMessage.getCid(), from, timestamp, -1);
      message.setMessageId(msgId);
      if (data instanceof String || data instanceof JSON) {
        message.setContent(data.toString());
      } else {
        continue;
      }
      message = AVIMMessageManager.parseTypedMessage(message);
      messageList.add(message);
    }
    AVIMBaseBroadcastReceiver receiver = AVIMBaseBroadcastReceiver.removeReceiver(requestKey);
    Intent intent = new Intent();
    intent.putExtra(Conversation.callbackClientKey, peerId);
    intent.putExtra(Conversation.callbackConversationKey, logsMessage.getCid());
    intent.putExtra(Conversation.callbackHistoryMessages, messageList);
    receiver.onReceive(intent);
  }

  private void processErrorCommand(String peerId, Integer requestKey, ErrorCommand errorCommand) {
    int code = errorCommand.getCode();
    int appCode = (errorCommand.hasAppCode() ? errorCommand.getAppCode() : 0);
    String reason = errorCommand.getReason();
    AVIMBaseBroadcastReceiver receiver = AVIMBaseBroadcastReceiver.removeReceiver(requestKey);
    Intent intent = new Intent();
    intent.putExtra(Conversation.callbackExceptionKey, new AVIMException(appCode, code, reason));
    receiver.onReceive(intent);
  }

  private void processConvCommand(String peerId, String operation, Integer requestKey,
      ConvCommand convMessage) {
    AVIMBaseBroadcastReceiver receiver = AVIMBaseBroadcastReceiver.removeReceiver(requestKey);
    Intent intent = new Intent();
    if (ConversationControlPacket.ConversationControlOp.QUERY_RESULT.equals(operation)) {
      String result = convMessage.getResults().getData();
      intent.putExtra(Conversation.callbackConversationKey, result);
    } else {
      String conversationId = convMessage.getCid();
      intent.putExtra(Conversation.callbackConversationKey, conversationId);
    }
    receiver.onReceive(intent);
  }

  private void processRcpCommand(String peerId, RcpCommand command) {
    AVIMClient client = AVIMClient.getInstance(peerId);
    String messageId = command.getId();
    Long timestamp = command.getT();
    AVIMMessage message = AVIMConversation.cachedMessage.get(messageId);
    message.setReceiptTimestamp(timestamp);
    AVIMMessageManager.processMessageReceipt(message, client);
  }

  public void processAckCommand(String peerId, Integer requestKey, Messages.AckCommand ackCommand) {
    AVIMBaseBroadcastReceiver receiver = AVIMBaseBroadcastReceiver.removeReceiver(requestKey);

    if (ackCommand.hasCode()) {
      // 这里是发送消息异常时的ack
      int code = ackCommand.getCode();
      int appCode = (ackCommand.hasAppCode() ? ackCommand.getAppCode() : 0);
      String reason = ackCommand.getReason();
      AVIMException error = new AVIMException(code, appCode, reason);
      Intent intent = new Intent();
      intent.putExtra(Conversation.callbackExceptionKey, error);
      intent.putExtra(Conversation.callbackClientKey, peerId);
      if (receiver != null) {
        receiver.onReceive(intent);
      }
    } else {
      if (receiver != null) {
        receiver.onReceive(null);
      }
    }
  }

  private void processClientCommand(String peerId, String op, Integer requestKey,
      SessionCommand command) {
    AVIMClient client = AVIMClient.getInstance(peerId);
    if (op.equals(SessionControlPacket.SessionControlOp.OPENED)) {
      client.sessionOpened.set(true);

      if (!client.sessionPaused.getAndSet(false)) {
        int requestId = (null != requestKey ? requestKey : CommandPacket.UNSUPPORTED_OPERATION);
        AVIMBaseBroadcastReceiver receiver = AVIMBaseBroadcastReceiver.removeReceiver(requestId);
        if (receiver != null) {
          receiver.onReceive(null);
        }
      } else {
        if (AVOSCloud.showInternalDebugLog()) {
          LogUtil.avlog.d("session resumed");
        }
        if (AVIMClient.clientEventHandler != null) {
          AVIMClient.clientEventHandler.onConnectionResume(client);
        }
      }
    } else if (op.equals(SessionControlPacket.SessionControlOp.QUERY_RESULT)) {
      final List<String> sessionPeerIds = command.getOnlineSessionPeerIdsList();
      int requestId = (null != requestKey ? requestKey : CommandPacket.UNSUPPORTED_OPERATION);
      AVIMBaseBroadcastReceiver receiver = AVIMBaseBroadcastReceiver.removeReceiver(requestId);
      if (receiver != null) {
        Intent intent = new Intent();
        intent.putExtra(Conversation.callbackOnlineClients, sessionPeerIds);
        receiver.onReceive(intent);
      }
    } else if (op.equals(SessionControlPacket.SessionControlOp.CLOSED)) {
      int requestId = (null != requestKey ? requestKey : CommandPacket.UNSUPPORTED_OPERATION);
      if (command.hasCode()) {
        if (AVIMClient.clientEventHandler != null) {
          AVIMClient.clientEventHandler.onClientOffline(client, command.getCode());
        }
      } else {
        AVIMBaseBroadcastReceiver receiver = AVIMBaseBroadcastReceiver.removeReceiver(requestId);
        if (receiver != null) {
          receiver.onReceive(null);
        }
      }
    }
  }

  private void processDirectCommand(String peerId, DirectCommand directCommand) {
    AVIMClient client = AVIMClient.clients.get(peerId);
    final String msg = directCommand.getMsg();
    final String fromPeerId = directCommand.getFromPeerId();
    final String conversationId = directCommand.getCid();
    final Long timestamp = directCommand.getTimestamp();
    final String messageId = directCommand.getId();
    final boolean isTransient = directCommand.hasTransient() && directCommand.getTransient();
    final boolean hasMore = directCommand.hasHasMore() && directCommand.getHasMore();

    if (!isTransient) {
      if (!AVUtils.isBlankString(conversationId)) {
        this.sendData(ConversationAckPacket.getConversationAckPacket(peerId, conversationId,
            messageId));
      }
    }

    if (!AVUtils.isBlankString(conversationId)) {
      AVIMConversation conversation = client.getConversation(conversationId);
      AVIMMessage message = new AVIMMessage(conversationId, fromPeerId, timestamp, -1);
      message.setMessageId(messageId);
      message.setContent(msg);
      message.setMessageIOType(AVIMMessageIOType.AVIMMessageIOTypeIn);
      message.setMessageStatus(AVIMMessageStatus.AVIMMessageStatusSent);
      AVIMMessageManager.processMessage(message, client, hasMore, isTransient);
    }
  }

  @Override
  public void processRemoteServerNotAvailable() {
    this.start();
  }

  public void start() {
    router.fetchPushServer();
  }
}
