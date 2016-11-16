package cn.leancloud.leancloud_message;


import java.util.Arrays;
import java.util.List;
import java.util.Map;

import cn.leancloud.leancloud_message.packet.CommandPacket;
import cn.leancloud.leancloud_message.packet.ConversationControlPacket;
import cn.leancloud.leancloud_message.packet.ConversationControlPacket.ConversationControlOp;
import cn.leancloud.leancloud_message.packet.ConversationDirectMessagePacket;
import cn.leancloud.leancloud_message.packet.ConversationMessageQueryPacket;
import cn.leancloud.leancloud_message.packet.SessionControlPacket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.avos.avoscloud.AVObject;

public interface Conversation {
  String AV_CONVERSATION_INTENT_ACTION = "com.avoscloud.im.v2.action";

  String PARAM_CONVERSATION_MEMBER = "conversation.member";
  String PARAM_CONVERSATION_ATTRIBUTE = "conversation.attributes";
  String PARAM_CONVERSATION_ISTRANSIENT = "conversation.transient";
  String PARAM_CONVERSATION_ISUNIQUE = "conversation.unique";
  String PARAM_ONLINE_CLIENTS = "client.oneline";

  String PARAM_MESSAGE_QUERY_LIMIT = "limit";
  String PARAM_MESSAGE_QUERY_TIMESTAMP = "ts";
  String PARAM_MESSAGE_QUERY_MSGID = "mid";
  String PARAM_MESSAGE_QUERY_TO_MSGID = "tmid";
  String PARAM_MESSAGE_QUERY_TO_TIMESTAMP = "tt";

  String INTENT_KEY_DATA = "conversation.data";
  String INTENT_KEY_CLIENT = "conversation.client";
  String INTENT_KEY_CONVERSATION = "convesration.id";
  String INTENT_KEY_OPERATION = "conversation.operation";
  String INTENT_KEY_REQUESTID = "conversation.requestId";
  String INTENT_KEY_MESSAGEFLAG = "conversation.message.flag";
  String INTENT_KEY_ACTION = "conversation.action";
  String INTENT_KEY_SIGNATURE = "conversation.signature";

  String ACTION_INVITE = "invite";
  String ACTION_KICK = "kick";


  int DEFAULT_CONVERSATION_EXPIRE_TIME_IN_MILLS = 3600000;

  enum AVIMOperation {
    CONVERSATION_CREATION(40000, "com.avoscloud.v2.im.conversation.creation."), CONVERSATION_ADD_MEMBER(
        40001, "com.avoscloud.v2.im.conversation.members."), CONVERSATION_RM_MEMBER(40002,
        "com.avoscloud.v2.im.conversation.members."), CONVERSATION_JOIN(40003,
        "com.avoscloud.v2.im.conversation.join."), CONVERSATION_QUIT(40004,
        "com.avoscloud.v2.im.conversation.quit."), CONVERSATION_SEND_MESSAGE(40005,
        "com.avoscloud.v2.im.conversation.message."), CLIENT_OPEN(40006,
        "com.avoscloud.v2.im.client.initialize."), CLIENT_DISCONNECT(40007,
        "com.avoscloud.v2.im.client.quit."), CONVERSATION_QUERY(40008,
        "com.avoscloud.v2.im.conversation.query."), CONVERSATION_UPDATE(40009,
        "com.avoscloud.v2.im.conversation.update."), CONVERSATION_MESSAGE_QUERY(40010,
        "com.avoscloud.v2.im.conversation.message.query."), CONVERSATION_MUTE(40011,
        "com.avoscloud.v2.im.conversation.mute."), CONVERSATION_UNMUTE(40012,
        "com.avoscloud.v2.im.conversation.unmute"), CONVERSATION_MEMBER_COUNT_QUERY(40013,
        "com.avoscloud.v2.im.conversation.membercount."), CLIENT_ONLINE_QUERY(40014,
        "com.avoscloud.v2.im.client.onlineQuery."), CLIENT_STATUS(40015,
        "com.avoscloud.v2.im.client.status"), CONVERSATION_UNKNOWN(49999,
        "com.avoscloud.v2.im.conversation.unknown");

    private final String header;
    private final int code;

    AVIMOperation(int operationCode, String operationHeader) {
      this.code = operationCode;
      this.header = operationHeader;
    }

    public int getCode() {
      return code;
    }

    public String getOperation() {
      return header;
    }

    public static AVIMOperation getAVIMOperation(int code) {
      switch (code) {
        case 40000:
          return CONVERSATION_CREATION;
        case 40001:
          return CONVERSATION_ADD_MEMBER;
        case 40002:
          return CONVERSATION_RM_MEMBER;
        case 40003:
          return CONVERSATION_JOIN;
        case 40004:
          return CONVERSATION_QUIT;
        case 40005:
          return CONVERSATION_SEND_MESSAGE;
        case 40006:
          return CLIENT_OPEN;
        case 40007:
          return CLIENT_DISCONNECT;
        case 40008:
          return CONVERSATION_QUERY;
        case 40009:
          return CONVERSATION_UPDATE;
        case 40010:
          return CONVERSATION_MESSAGE_QUERY;
        case 40011:
          return CONVERSATION_MUTE;
        case 40012:
          return CONVERSATION_UNMUTE;
        case 40013:
          return CONVERSATION_MEMBER_COUNT_QUERY;
        case 40014:
          return CLIENT_ONLINE_QUERY;
        case 40015:
          return CLIENT_STATUS;
        default:
          return CONVERSATION_UNKNOWN;
      }
    }

    public CommandPacket genCommand(Intent intent) {
      int requestId = (int) intent.getExtra(Conversation.INTENT_KEY_REQUESTID);
      String clientId = (String) intent.getExtra(INTENT_KEY_CLIENT);
      String conversationId = (String) intent.getExtra(INTENT_KEY_CONVERSATION);
      switch (code) {
        case 40000:
          List<String> members = (List<String>) intent.getExtra(PARAM_CONVERSATION_MEMBER);
          return ConversationControlPacket.genConversationCommand(clientId, null, members,
              ConversationControlPacket.ConversationControlOp.START,
              (Map<String, Object>) intent.getExtra(PARAM_CONVERSATION_ATTRIBUTE),
              (Signature) intent.getExtra(INTENT_KEY_SIGNATURE),
              (boolean) intent.getExtra(PARAM_CONVERSATION_ISTRANSIENT),
              (boolean) intent.getExtra(PARAM_CONVERSATION_ISUNIQUE), requestId);
        case 40001:
          members = JSON.parseArray((String) intent.getExtra(INTENT_KEY_DATA), String.class);
          return ConversationControlPacket.genConversationCommand(clientId, conversationId,
              members, ConversationControlPacket.ConversationControlOp.ADD, null,
              (Signature) intent.getExtra(INTENT_KEY_SIGNATURE), requestId);
        case 40002:
          members = JSON.parseArray((String) intent.getExtra(INTENT_KEY_DATA), String.class);
          ConversationControlPacket.genConversationCommand(clientId, conversationId, members,
              ConversationControlPacket.ConversationControlOp.REMOVE, null,
              (Signature) intent.getExtra(INTENT_KEY_SIGNATURE), requestId);
        case 40003:
          return ConversationControlPacket.genConversationCommand(clientId, conversationId,
              Arrays.asList(clientId), ConversationControlPacket.ConversationControlOp.ADD, null,
              (Signature) intent.getExtra(INTENT_KEY_SIGNATURE), requestId);
        case 40004:
          return ConversationControlPacket.genConversationCommand(clientId, conversationId,
              Arrays.asList(clientId), ConversationControlPacket.ConversationControlOp.REMOVE,
              null, null, requestId);
        case 40005:
          return ConversationDirectMessagePacket
              .getConversationMessagePacket(
                  clientId,
                  conversationId,
                  ((AVIMMessage) intent.getExtra(Conversation.INTENT_KEY_DATA)).getContent(),
                  ((AVIMMessage) intent.getExtra(Conversation.INTENT_KEY_DATA)).getUniqueToken(),
                  ((int) intent.getExtra(Conversation.INTENT_KEY_MESSAGEFLAG) & AVIMConversation.RECEIPT_MESSAGE_FLAG) == AVIMConversation.RECEIPT_MESSAGE_FLAG,
                  ((int) intent.getExtra(Conversation.INTENT_KEY_MESSAGEFLAG) & AVIMConversation.NONTRANSIENT_MESSAGE_FLAG) == AVIMConversation.TRANSIENT_MESSAGE_FLAG,
                  requestId);
        case 40006:
          // CLIENT_OPEN
          return SessionControlPacket.genSessionCommand(clientId, null,
              (String) intent.getExtra(INTENT_KEY_DATA),
              SessionControlPacket.SessionControlOp.OPEN,
              (Signature) intent.getExtra(INTENT_KEY_SIGNATURE), requestId);
        case 40007:
          return SessionControlPacket.genSessionCommand(clientId, null,
              SessionControlPacket.SessionControlOp.CLOSE, null, requestId);
        case 40009:
          String dataInString = (String) intent.getExtra(INTENT_KEY_DATA);
          Map<String, Object> params = JSON.parseObject(dataInString, Map.class);
          JSONObject assembledAttributes = (JSONObject) params.get(PARAM_CONVERSATION_ATTRIBUTE);
          return ConversationControlPacket.genConversationCommand(clientId, conversationId, null,
              ConversationControlOp.UPDATE, assembledAttributes, null, requestId);
        case 40010:
          dataInString = (String) intent.getExtra(INTENT_KEY_DATA);
          params = JSON.parseObject(dataInString, Map.class);
          return ConversationMessageQueryPacket.getConversationMessageQueryPacket(clientId,
              conversationId, (String) params.get(PARAM_MESSAGE_QUERY_MSGID),
              (long) params.get(PARAM_MESSAGE_QUERY_TIMESTAMP),
              (int) params.get(PARAM_MESSAGE_QUERY_LIMIT),
              (String) params.get(PARAM_MESSAGE_QUERY_TO_MSGID),
              (long) params.get(PARAM_MESSAGE_QUERY_TO_TIMESTAMP), requestId);
        case 40011:
          return ConversationControlPacket.genConversationCommand(clientId, conversationId, null,
              ConversationControlOp.MUTE, null, null, requestId);
        case 40012:
          return ConversationControlPacket.genConversationCommand(clientId, conversationId, null,
              ConversationControlOp.UNMUTE, null, null, requestId);
        case 40013:
          return ConversationControlPacket.genConversationCommand(clientId, conversationId, null,
              ConversationControlOp.COUNT, null, null, requestId);
        case 40014:
          members = (List<String>) intent.getExtra(INTENT_KEY_DATA);
          return SessionControlPacket.genSessionCommand(clientId, members,
              SessionControlPacket.SessionControlOp.QUERY, null, requestId);
        default:
          return null;
      }
    }
  }

  int STATUS_ON_MESSAGE = 50000;
  int STATUS_ON_MESSAGE_RECEIPTED = 50001;
  int STATUS_ON_MEMBERS_LEFT = 50004;
  int STATUS_ON_MEMBERS_JOINED = 50005;
  int STATUS_ON_CONNECTION_PAUSED = 50006;
  int STATUS_ON_CONNECTION_RESUMED = 50007;
  int STATUS_ON_JOINED = 50008;
  int STATUS_ON_KICKED_FROM_CONVERSATION = 50009;
  int STATUS_ON_CLIENT_OFFLINE = 50010;
  int STATUS_ON_OFFLINE_UNREAD = 50011;

  String callbackExceptionKey = "callbackException";
  String callbackClientKey = "callbackclient";
  String callbackConversationKey = "callbackconversation";
  String callbackMessageTimeStamp = "callbackMessageTimeStamp";
  String callbackMessageId = "callbackMessageId";
  String callbackHistoryMessages = "callbackHistoryMessages";
  String callbackMemberCount = "callbackMemberCount";
  String callbackOnlineClients = "callbackOnlineClient";
  String callbackCreatedAt = "callbackCreatedAt";
  String callbackUpdatedAt = "callbackUpdatedAt";
  String callbackClientStatus = "callbackClientStatus";

  String QUERY_PARAM_OFFSET = "skip";
  String QUERY_PARAM_LIMIT = "limit";
  String QUERY_PARAM_SORT = "sort";
  String QUERY_PARAM_WHERE = "where";

  String ATTRIBUTE_CONVERSATION_NAME = "name";
  String ATTRIBUTE_MORE = "attr";
  String COLUMN_MEMBERS = "m";
  String COLUMN_TRANSIENT = "tr";
  String LAST_MESSAGE = "lm";
  String COLUMN_SYS = "sys";

  String[] CONVERSATION_COLUMNS = {COLUMN_MEMBERS, ATTRIBUTE_CONVERSATION_NAME, "c", LAST_MESSAGE,
      "objectId", "mu", AVObject.UPDATED_AT, AVObject.CREATED_AT, ATTRIBUTE_MORE, COLUMN_TRANSIENT,
      COLUMN_SYS};
  List<String> CONVERSATION_COLUMN_LIST = Arrays.asList(CONVERSATION_COLUMNS);

}
