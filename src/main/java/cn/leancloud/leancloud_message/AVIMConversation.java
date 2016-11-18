package cn.leancloud.leancloud_message;

import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.leancloud.leancloud_message.AVIMClient.SignatureTask;
import cn.leancloud.leancloud_message.Conversation.AVIMOperation;
import cn.leancloud.leancloud_message.callback.AVIMConversationCallback;
import cn.leancloud.leancloud_message.callback.AVIMConversationMemberCountCallback;
import cn.leancloud.leancloud_message.callback.AVIMMessagesQueryCallback;
import cn.leancloud.leancloud_message.callback.AVIMSingleMessageQueryCallback;
import cn.leancloud.leancloud_message.messages.AVIMFileMessage;
import cn.leancloud.leancloud_message.messages.AVIMFileMessageAccessor;
import cn.leancloud.leancloud_message.packet.CommandPacket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVObject;
import com.avos.avoscloud.AVQuery;
import com.avos.avoscloud.AVUtils;
import com.avos.avoscloud.GetCallback;
import com.avos.avoscloud.LogUtil;
import com.avos.avoscloud.SaveCallback;
import com.avos.avoscloud.internal.InternalConfigurationController;

public class AVIMConversation {

  /**
   * 暂存消息
   * <p/>
   * 只有在消息发送时，对方也是在线的才能收到这条消息
   */
  public static final int TRANSIENT_MESSAGE_FLAG = 0x00;
  /**
   * 非暂存消息
   * <p/>
   * 当消息发送时，对方不在线的话，消息会变成离线消息
   */
  public static final int NONTRANSIENT_MESSAGE_FLAG = 0x01;
  /**
   * 回执消息
   * <p/>
   * 当消息送到到对方以后，发送方会收到消息回执说明消息已经成功达到接收方
   */
  public static final int RECEIPT_MESSAGE_FLAG = 0x11;
  static final String NAME_KEY = "name";
  public static final String LAST_MESSAGE = "lm";

  private static final String ATTR_PERFIX = Conversation.ATTRIBUTE_MORE + ".";
  static final Map<String, AVIMMessage> cachedMessage = new HashMap<String, AVIMMessage>();

  String conversationId;
  Set<String> members;
  Map<String, Object> attributes;
  Map<String, Object> pendingAttributes;
  AVIMClient client;
  String creator;
  boolean isTransient;
  Date lastMessageAt;
  String createdAt;
  String updatedAt;

  protected AVIMConversation(AVIMClient client, List<String> members,
      Map<String, Object> attributes, boolean isTransient) {
    this.members = new HashSet<String>();
    if (members != null) {
      this.members.addAll(members);
    }
    this.attributes = new HashMap<String, Object>();
    if (attributes != null) {
      this.attributes.putAll(attributes);
    }
    this.client = client;
    pendingAttributes = new HashMap<String, Object>();
    this.isTransient = isTransient;
  }

  protected AVIMConversation(AVIMClient client, String conversationId) {
    this(client, null, null, false);
    this.conversationId = conversationId;
  }

  public String getConversationId() {
    return this.conversationId;
  }

  protected void setConversationId(String id) {
    this.conversationId = id;
  }

  protected void setCreator(String creator) {
    this.creator = creator;
  }

  /**
   * 获取聊天对话的创建者
   *
   * @return
   * @since 3.0
   */
  public String getCreator() {
    return this.creator;
  }

  /**
   * 发送一条非暂存消息
   *
   * @param message
   * @param callback
   * @since 3.0
   */
  public void sendMessage(AVIMMessage message, final AVIMConversationCallback callback) {
    sendMessage(message, AVIMConversation.NONTRANSIENT_MESSAGE_FLAG, callback);
  }

  /**
   * 发送一条消息。
   *
   * @param message
   * @param messageFlag 消息发送选项。
   * @param callback
   * @since 3.0
   */
  public void sendMessage(final AVIMMessage message, final int messageFlag,
      final AVIMConversationCallback callback) {
    message.setConversationId(conversationId);
    message.setFrom(client.clientId);
    message.genUniqueToken();
    if (!InternalConfigurationController.globalInstance().getAppConfiguration().isConnected()) {
      message.setMessageStatus(AVIMMessage.AVIMMessageStatus.AVIMMessageStatusFailed);
      if (callback != null) {
        callback.internalDone(new AVException(AVException.CONNECTION_FAILED, "Connection lost"));
      }
      return;
    }

    message.setTimestamp(System.currentTimeMillis());
    message.setMessageStatus(AVIMMessage.AVIMMessageStatus.AVIMMessageStatusSending);

    if (AVIMFileMessage.class.isAssignableFrom(message.getClass())) {
      AVIMFileMessageAccessor.upload((AVIMFileMessage) message, new SaveCallback() {
        public void done(AVException e) {
          if (e != null) {
            message.setMessageStatus(AVIMMessage.AVIMMessageStatus.AVIMMessageStatusFailed);
            if (callback != null) {
              callback.internalDone(e);
            }
          } else {
            sendCMDToServer(message, messageFlag, callback);
          }
        }
      });
    } else {
      sendCMDToServer(message, messageFlag, callback);
    }
  }

  /**
   * 查询最近的20条消息记录
   * 
   * @param callback
   */
  public void queryMessages(final AVIMMessagesQueryCallback callback) {
    this.queryMessages(20, callback);
  }

  /**
   * 查询消息记录，上拉时使用。
   *
   * @param msgId 消息id，从消息id开始向前查询
   * @param timestamp 查询起始的时间戳，返回小于这个时间的记录。 客户端时间不可靠，请用 0 代替 System.currentTimeMillis()
   * @param limit 返回条数限制
   * @param callback
   */
  public void queryMessages(String msgId, long timestamp, int limit, String toMsgId,
      long toTimestamp, AVIMMessagesQueryCallback callback) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put(Conversation.PARAM_MESSAGE_QUERY_LIMIT, limit);
    params.put(Conversation.PARAM_MESSAGE_QUERY_MSGID, msgId);
    params.put(Conversation.PARAM_MESSAGE_QUERY_TIMESTAMP, timestamp);
    params.put(Conversation.PARAM_MESSAGE_QUERY_TO_MSGID, toMsgId);
    params.put(Conversation.PARAM_MESSAGE_QUERY_TO_TIMESTAMP, toTimestamp);
    sendCMDToServer(JSON.toJSONString(params), AVIMOperation.CONVERSATION_MESSAGE_QUERY, callback);
  }

  /**
   * 获取最新的消息记录
   * 
   * @param limit
   * @param callback
   */
  public void queryMessages(final int limit, final AVIMMessagesQueryCallback callback) {
    if (limit <= 0 || limit > 1000) {
      if (callback != null) {
        callback.internalDone(null, new AVException(new IllegalArgumentException(
            "limit should be in [1, 1000]")));
      }
    }

    queryMessages(null, 0l, limit, null, 0l, callback);
  }

  /**
   * 查询消息记录，上拉时使用。
   *
   * @param msgId 消息id，从消息id开始向前查询
   * @param timestamp 查询起始的时间戳，返回小于这个时间的记录。 客户端时间不可靠，请用 0 代替 System.currentTimeMillis()
   * @param limit 返回条数限制
   * @param callback
   */
  public void queryMessages(final String msgId, final long timestamp, final int limit,
      final AVIMMessagesQueryCallback callback) {
    queryMessages(msgId, timestamp, limit, null, 0l, callback);
  }

  /**
   * 获取本聊天室的最后一条消息
   *
   * 如果AVIMClient.setMessageQueryCacheEnable(false)则强制走网络
   *
   * 否则只会取本地的缓存而不走网络，所以在一定程度上缺乏实时性
   * 
   * @param callback
   */
  public void getLastMessage(final AVIMSingleMessageQueryCallback callback) {
    queryMessages(null, 0l, 1, null, 0l, new AVIMMessagesQueryCallback() {
      @Override
      public void done(List<AVIMMessage> messages, AVIMException e) {
        processLastMessageResult(messages, e, callback);
      }
    });
  }

  private void processLastMessageResult(List<AVIMMessage> resultMessages, AVIMException e,
      AVIMSingleMessageQueryCallback callback) {
    if (e == null) {
      if (resultMessages.size() > 0) {
        callback.internalDone(resultMessages.get(0), null);
      } else {
        callback.done(null, null);
      }
    } else {
      callback.internalDone(null, e);
    }
  }

  public void getMemberCount(AVIMConversationMemberCountCallback callback) {
    sendCMDToServer(null, AVIMOperation.CONVERSATION_MEMBER_COUNT_QUERY, callback);
  }

  /**
   * 在聊天对话中间增加新的参与者
   *
   * @param friendsList
   * @param callback
   * @since 3.0
   */

  public void addMembers(final List<String> friendsList, final AVIMConversationCallback callback) {
    AVException membersCheckException = AVIMClient.validateNonEmptyConversationMembers(friendsList);
    if (membersCheckException != null) {
      if (callback != null) {
        callback.internalDone(null, membersCheckException);
      }
      return;
    }

    SignatureCallback signatureCallback = new SignatureCallback() {

      @Override
      public void onSignatureReady(Signature sig, AVException e) {
        if (e != null) {
          callback.done(new AVIMException(e));
        } else {
          sendCMDToServer(JSON.toJSONString(friendsList), AVIMOperation.CONVERSATION_ADD_MEMBER,
              callback, new OperationCompleteCallback() {

                @Override
                public void onComplete() {
                  members.addAll(friendsList);
                }

                @Override
                public void onFailure() {}
              });
        }
      }

      @Override
      public Signature computeSignature() throws SignatureException {
        if (AVIMClient.factory != null) {
          return AVIMClient.factory.createConversationSignature(conversationId, client.clientId,
              friendsList, Conversation.ACTION_INVITE);
        }
        return null;
      }
    };
    AVIMClient.signatureTaskPool.execute(new SignatureTask(signatureCallback));
  }

  /**
   * 在聊天记录中间剔除参与者
   *
   * @param friendsList
   * @param callback
   * @since 3.0
   */

  public void kickMembers(final List<String> friendsList, final AVIMConversationCallback callback) {

    AVException membersCheckException = AVIMClient.validateNonEmptyConversationMembers(friendsList);
    if (membersCheckException != null) {
      if (callback != null) {
        callback.internalDone(null, membersCheckException);
      }
      return;
    }

    SignatureCallback signatureCallback = new SignatureCallback() {

      @Override
      public void onSignatureReady(Signature sig, AVException e) {
        if (e != null) {
          if (callback != null) {
            callback.done(new AVIMException(e));
          }
        } else {
          sendCMDToServer(JSON.toJSONString(friendsList), AVIMOperation.CONVERSATION_RM_MEMBER,
              callback, new OperationCompleteCallback() {

                @Override
                public void onComplete() {
                  members.removeAll(friendsList);
                }

                @Override
                public void onFailure() {}
              });
        }
      }

      @Override
      public Signature computeSignature() throws SignatureException {
        if (AVIMClient.factory != null) {
          AVIMClient.factory.createConversationSignature(conversationId, client.clientId,
              friendsList, Conversation.ACTION_KICK);
        }
        return null;
      }
    };
    AVIMClient.signatureTaskPool.execute(new SignatureTask(signatureCallback));
  }

  /**
   * 获取conversation当前的参与者
   *
   * @return
   * @since 3.0
   */
  public List<String> getMembers() {
    List<String> allList = new ArrayList<String>();
    allList.addAll(members);

    return Collections.unmodifiableList(allList);
  }

  /**
   * 静音，客户端拒绝收到服务器端的离线推送通知
   *
   * @param callback
   */
  public void mute(final AVIMConversationCallback callback) {
    this.sendCMDToServer(null, AVIMOperation.CONVERSATION_MUTE, callback, null);
  }

  /**
   * 取消静音，客户端取消静音设置
   * 
   * @param callback
   */
  public void unmute(final AVIMConversationCallback callback) {
    this.sendCMDToServer(null, AVIMOperation.CONVERSATION_UNMUTE, callback, null);
  }

  protected void setMembers(List<String> m) {
    members.clear();
    if (m != null) {
      members.addAll(m);
    }
  }

  /**
   * 退出当前的聊天对话
   *
   * @param callback
   * @since 3.0
   */
  public void quit(final AVIMConversationCallback callback) {
    this.sendCMDToServer(null, AVIMOperation.CONVERSATION_QUIT, callback,
        new OperationCompleteCallback() {
          @Override
          public void onComplete() {
            members.remove(client.getClientId());
          }

          @Override
          public void onFailure() {

          }
        });
  }

  /**
   * 获取当前聊天对话的属性
   *
   * @return
   * @since 3.0
   */

  public Object getAttribute(String key) {
    Object value;
    if (pendingAttributes.containsKey(key)) {
      value = pendingAttributes.get(key);
    } else {
      value = attributes.get(key);
    }
    return value;
  }

  public void setAttribute(String key, Object value) {
    if (!AVUtils.isBlankContent(key)) {
      // 以往的 sdk 支持 setAttribute("attr.key", "attrValue") 这种格式，这里兼容一下
      if (key.startsWith(ATTR_PERFIX)) {
        this.pendingAttributes.put(key.substring(ATTR_PERFIX.length()), value);
      } else {
        this.pendingAttributes.put(key, value);
      }
    }
  }

  /**
   * 设置当前聊天对话的属性
   *
   * @param attr
   * @since 3.0
   */
  public void setAttributes(Map<String, Object> attr) {
    pendingAttributes.clear();
    pendingAttributes.putAll(attr);
  }

  /**
   * 设置当前聊天对话的属性，仅用于初始化时 因为 attr 涉及到本地缓存，所以初始化时与主动调用 setAttributes 行为不同
   * 
   * @param attr
   */
  void setAttributesForInit(Map<String, Object> attr) {
    this.attributes.clear();
    if (attr != null) {
      this.attributes.putAll(attr);
    }
  }

  /**
   * 获取conversation的名字
   * 
   * @return
   */
  public String getName() {
    return (String) getAttribute(NAME_KEY);
  }

  public void setName(String name) {
    pendingAttributes.put(NAME_KEY, name);
  }

  /**
   * 获取最新一条消息的时间
   * 
   * @return
   */
  public Date getLastMessageAt() {
    return lastMessageAt;
  }

  void setLastMessageAt(Date lastMessageAt) {
    this.lastMessageAt = lastMessageAt;
  }

  /**
   * 获取Conversation的创建时间
   * 
   * @return
   */
  public Date getCreatedAt() {
    return AVUtils.dateFromString(createdAt);
  }

  void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * 获取Conversation的更新时间
   * 
   * @return
   */
  public Date getUpdatedAt() {
    return AVUtils.dateFromString(updatedAt);
  }

  void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }

  /**
   * 更新当前对话的属性至服务器端
   *
   * @param callback
   * @since 3.0
   */

  public void updateInfoInBackground(AVIMConversationCallback callback) {
    if (!this.pendingAttributes.isEmpty()) {
      Map<String, Object> params = new HashMap<String, Object>();
      JSONObject assembledAttributes = processAttributes(pendingAttributes, false);
      if (assembledAttributes != null) {
        params.put(Conversation.PARAM_CONVERSATION_ATTRIBUTE, assembledAttributes);
      }

      this.sendCMDToServer(JSON.toJSONString(params), AVIMOperation.CONVERSATION_UPDATE, callback,
          new OperationCompleteCallback() {

            @Override
            public void onComplete() {
              attributes.putAll(pendingAttributes);
              pendingAttributes.clear();
            }

            @Override
            public void onFailure() {

            }
          });
    }
  }

  /**
   * 从服务器同步对话的属性
   * 
   * @param callback
   */

  public void fetchInfoInBackground(final AVIMConversationCallback callback) {
    if (AVUtils.isBlankString(conversationId)) {
      if (callback != null) {
        callback.internalDone(null, new AVException(AVException.INVALID_QUERY,
            "ConversationId is empty"));
      } else {
        LogUtil.avlog.e("ConversationId is empty");
      }
      return;
    }
    final AVQuery<AVObject> conversationQuery = new AVQuery<AVObject>("_Conversation");
    conversationQuery.whereEqualTo("objectId", conversationId);
    conversationQuery.getFirstInBackground(new GetCallback<AVObject>() {

      @Override
      public void done(AVObject object, AVException e) {
        if (e != null && callback != null) {
          callback.internalDone(null, e);
        } else if (e == null && object != null) {
          Map<String, Object> attributes = new HashMap<String, Object>();
          if (object.get(Conversation.ATTRIBUTE_MORE) != null) {
            org.json.JSONObject attr = object.getJSONObject(Conversation.ATTRIBUTE_MORE);

            if (attr != null) {
              Iterator<String> iterator = attr.keys();
              while (iterator.hasNext()) {
                String key = iterator.next();
                try {
                  attributes.put(key, attr.get(key));
                } catch (org.json.JSONException e1) {
                  continue;
                }
              }
            }
          }


          String name = object.getString(NAME_KEY);
          if (!AVUtils.isBlankString(name)) {
            attributes.put(NAME_KEY, name);
          }

          List<String> members = object.getList("m");
          setMembers(members);
          AVIMConversation.this.setCreatedAt(AVUtils.stringFromDate(object.getCreatedAt()));
          AVIMConversation.this.setUpdatedAt(AVUtils.stringFromDate(object.getUpdatedAt()));
          AVIMConversation.this.lastMessageAt = object.getDate("lm");
          AVIMConversation.this.attributes.putAll(attributes);
          String creator = object.getString("c");
          setCreator(creator);
          if (callback != null) {
            callback.internalDone(null, null);
          }
          // cache it
          client.conversationCache.put(conversationId, AVIMConversation.this);
        } else if (e == null && object == null && callback != null) {
          callback.internalDone(null, new AVException(AVException.MISSING_OBJECT_ID,
              "Object not found"));
        }
      }
    });
  }

  /**
   * 加入当前聊天对话
   *
   * @param callback
   */

  public void join(final AVIMConversationCallback callback) {
    SignatureCallback signatureCallback = new SignatureCallback() {

      @Override
      public void onSignatureReady(Signature sig, AVException e) {
        if (e != null) {
          callback.done(new AVIMException(e));
        } else {
          sendCMDToServer(null, AVIMOperation.CONVERSATION_JOIN, callback,
              new OperationCompleteCallback() {
                @Override
                public void onComplete() {
                  members.add(client.getClientId());
                }

                @Override
                public void onFailure() {

                }
              });
        }

      }

      @Override
      public Signature computeSignature() throws SignatureException {
        if (AVIMClient.factory != null) {
          return AVIMClient.factory.createConversationSignature(conversationId, client.clientId,
              Arrays.asList(client.clientId), Conversation.ACTION_INVITE);
        }
        return null;
      }
    };
    AVIMClient.signatureTaskPool.execute(new SignatureTask(signatureCallback));
  }

  public boolean isTransient() {
    return isTransient;
  }


  /**
   * 超时的时间间隔设置为一个小时，即 fetch 操作并且返回了错误，则一个小时内 sdk 不再进行调用 fetch
   */
  int FETCH_TIME_INTERVEL = 3600 * 1000;

  /**
   * 最近的 sdk 调用的 fetch 操作的时间
   */
  long latestConversationFetch = 0;

  /**
   * 判断当前 Conversation 是否有效，因为 AVIMConversation 为客户端创建，有可能因为没有同步造成数据丢失 可以根据此函数来判断，如果无效，则需要调用
   * fetchInfoInBackground 同步数据 如果 fetchInfoInBackground 出错（比如因为 acl 问题造成 Forbidden to find by class
   * permissions ）， 客户端就会在收到消息后一直做 fetch 操作，所以这里加了一个判断，如果在 FETCH_TIME_INTERVEL 内有业务类型的 error code
   * 返回，则不在请求
   */
  boolean isShouldFetch() {
    return null == getCreatedAt()
        && (System.currentTimeMillis() - latestConversationFetch > FETCH_TIME_INTERVEL);
  }

  private void sendCMDToServer(final AVIMMessage message, int messageFlag, final AVCallback callback) {
    sendCMDToServer(null, message, messageFlag, AVIMOperation.CONVERSATION_SEND_MESSAGE, callback,
        null, null);
  }

  private void sendCMDToServer(String dataInString, final Conversation.AVIMOperation operation,
      final AVCallback callback, final OperationCompleteCallback occ) {
    sendCMDToServer(dataInString, null, 0, operation, callback, occ, null);
  }

  private void sendCMDToServer(String dataInString, final AVIMOperation operation,
      AVCallback callback) {
    sendCMDToServer(dataInString, null, 0, operation, callback, null, null);
  }

  private void sendCMDToServer(String dataInString, final AVIMMessage message,
      final int messageFlag, final Conversation.AVIMOperation operation, final AVCallback callback,
      final OperationCompleteCallback occ, final Signature sig) {
    final int requestId = AVUtils.getNextIMRequestId();

    CommandPacket command = null;
    Intent i = new Intent();
    if (!AVUtils.isBlankString(dataInString)) {
      i.putExtra(Conversation.INTENT_KEY_DATA, dataInString);
    }
    if (message != null) {
      i.putExtra(Conversation.INTENT_KEY_DATA, message);
      i.putExtra(Conversation.INTENT_KEY_MESSAGEFLAG, messageFlag);
    }
    i.putExtra(Conversation.INTENT_KEY_CLIENT, client.clientId);
    i.putExtra(Conversation.INTENT_KEY_CONVERSATION, this.conversationId);
    i.putExtra(Conversation.INTENT_KEY_REQUESTID, requestId);
    if (sig != null) {
      i.putExtra(Conversation.INTENT_KEY_SIGNATURE, sig);
    }
    command = operation.genCommand(i);

    AVIMBaseBroadcastReceiver.register(requestId, new AVIMBaseBroadcastReceiver(callback) {
      @Override
      public void execute(Intent intent, Throwable error) {
        // 处理side effect调用
        if (error == null && occ != null) {
          occ.onComplete();
        } else if (error != null && occ != null) {
          occ.onFailure();
        }
        if (error != null) {
          if (callback != null) {
            callback.internalDone(new AVException(error));
          }
          return;
        }
        // 处理退出命令时
        if (operation.getCode() == AVIMOperation.CONVERSATION_QUIT.getCode()) {
          client.removeConversationCache(AVIMConversation.this);
        }

        // 消息命令
        if (message != null) {
          if (error == null) {
            // 处理发送成功的消息
            long timestamp =
                (intent.getExtra(Conversation.callbackMessageTimeStamp) == null) ? -1
                    : (long) intent.getExtra(Conversation.callbackMessageTimeStamp);
            String messageId = (String) intent.getExtra(Conversation.callbackMessageId);
            message.setMessageId(messageId);
            message.setMessageStatus(AVIMMessage.AVIMMessageStatus.AVIMMessageStatusSent);
            if (timestamp != -1) {
              message.setTimestamp(timestamp);
            }
            AVIMConversation.this.lastMessageAt = new Date(timestamp);

            // 如果需要接受receiptMessage消息则需要在这里做额外的缓存
            if ((messageFlag & RECEIPT_MESSAGE_FLAG) == RECEIPT_MESSAGE_FLAG) {
              cachedMessage.put(messageId, message);
            }
          } else {
            message.setMessageStatus(AVIMMessage.AVIMMessageStatus.AVIMMessageStatusFailed);
          }
        }
        // 处理历史消息查询
        if (operation.getCode() == AVIMOperation.CONVERSATION_MESSAGE_QUERY.getCode()) {
          List<AVIMMessage> historyMessages =
              (List<AVIMMessage>) intent.getExtra(Conversation.callbackHistoryMessages);

          if (error != null) {
            if (callback != null) {
              callback.internalDone(null, AVIMException.wrapperAVException(error));
            }
          } else {
            if (historyMessages == null) {
              historyMessages = Collections.EMPTY_LIST;
            }
            if (callback != null) {
              callback.internalDone(historyMessages, null);
            }
          }
          return;
        }
        // 刷新Conversation 更新时间
        if (operation.getCode() == AVIMOperation.CONVERSATION_UPDATE.getCode()) {
          if (intent.getExtras().containsKey(Conversation.callbackUpdatedAt)) {
            String updatedAt = (String) intent.getExtras().get(Conversation.callbackUpdatedAt);
            AVIMConversation.this.updatedAt = updatedAt;
          }
        }

        if (operation.getCode() == AVIMOperation.CONVERSATION_MEMBER_COUNT_QUERY.getCode()) {
          int memberCount =
              (int) (intent.getExtra(Conversation.callbackMemberCount) == null ? 0 : intent
                  .getExtra(Conversation.callbackMemberCount));
          if (callback != null) {
            callback.internalDone(memberCount, AVIMException.wrapperAVException(error));
          }
          return;
        }
        if (callback != null) {
          callback.internalDone(null, AVIMException.wrapperAVException(error));
        }
      }
    });

    AVIMServer.getInstance().sendData(command);
  }

  /**
   * parse AVIMConversation from jsonObject
   * 
   * @param client
   * @param jsonObj
   * @return
   */
  public static AVIMConversation parseFromJson(AVIMClient client, JSONObject jsonObj) {
    if (null == jsonObj || null == client) {
      return null;
    }

    String conversationId = jsonObj.getString(AVObject.OBJECT_ID);
    List<String> m = jsonObj.getObject("m", List.class);
    AVIMConversation conversation = new AVIMConversation(client, conversationId);
    conversation.setMembers(m);
    conversation.setCreator(jsonObj.getString("c"));
    HashMap<String, Object> attributes = new HashMap<String, Object>();
    if (jsonObj.containsKey(Conversation.ATTRIBUTE_CONVERSATION_NAME)) {
      attributes.put(Conversation.ATTRIBUTE_CONVERSATION_NAME,
          jsonObj.getString(Conversation.ATTRIBUTE_CONVERSATION_NAME));
    }
    if (jsonObj.containsKey(Conversation.ATTRIBUTE_MORE)) {
      JSONObject moreAttributes = jsonObj.getJSONObject(Conversation.ATTRIBUTE_MORE);
      if (moreAttributes != null) {
        Map<String, Object> moreAttributesMap = JSON.toJavaObject(moreAttributes, Map.class);
        attributes.putAll(moreAttributesMap);
      }
    }
    conversation.setAttributesForInit(attributes);

    if (jsonObj.containsKey(AVObject.CREATED_AT)) {
      conversation.setCreatedAt(jsonObj.getString(AVObject.CREATED_AT));
    }

    if (jsonObj.containsKey(AVObject.UPDATED_AT)) {
      conversation.setUpdatedAt(jsonObj.getString(AVObject.UPDATED_AT));
    }

    if (jsonObj.containsKey(LAST_MESSAGE)) {
      conversation
          .setLastMessageAt(AVUtils.dateFromMap(jsonObj.getObject(LAST_MESSAGE, Map.class)));
    }

    if (jsonObj.containsKey(Conversation.COLUMN_TRANSIENT)) {
      conversation.isTransient = jsonObj.getBoolean(Conversation.COLUMN_TRANSIENT);
    }
    return conversation;
  }

  /**
   * 处理 AVIMConversation attr 列 因为 sdk 支持增量更新与覆盖更新，而增量更新与覆盖更新需要的结构不一样，所以这里处理一下
   * 具体格式可参照下边的注释，注意，两种格式不能同时存在，否则 server 会报错
   * 
   * @param attributes
   * @param isCovered
   * @return
   */
  static JSONObject processAttributes(Map<String, Object> attributes, boolean isCovered) {
    if (isCovered) {
      return processAttributesForCovering(attributes);
    } else {
      return processAttributesForIncremental(attributes);
    }
  }

  /**
   * 增量更新 attributes 这里处理完的格式应该类似为 {"attr.key1":"value2", "attr.key2":"value2"}
   * 
   * @param attributes
   * @return
   */
  static JSONObject processAttributesForIncremental(Map<String, Object> attributes) {
    Map<String, Object> attributeMap = new HashMap<>();
    if (attributes.containsKey(Conversation.ATTRIBUTE_CONVERSATION_NAME)) {
      attributeMap.put(Conversation.ATTRIBUTE_CONVERSATION_NAME,
          attributes.get(Conversation.ATTRIBUTE_CONVERSATION_NAME));
    }
    for (String k : attributes.keySet()) {
      if (!Conversation.CONVERSATION_COLUMN_LIST.contains(k)) {
        attributeMap.put(ATTR_PERFIX + k, attributes.get(k));
      }
    }
    if (attributeMap.isEmpty()) {
      return null;
    }
    return new JSONObject(attributeMap);
  }

  /**
   * 覆盖更新 attributes 这里处理完的格式应该类似为 {"attr":{"key1":"value1","key2":"value2"}}
   * 
   * @param attributes
   * @return
   */
  static JSONObject processAttributesForCovering(Map<String, Object> attributes) {
    HashMap<String, Object> attributeMap = new HashMap<String, Object>();
    if (attributes.containsKey(Conversation.ATTRIBUTE_CONVERSATION_NAME)) {
      attributeMap.put(Conversation.ATTRIBUTE_CONVERSATION_NAME,
          attributes.get(Conversation.ATTRIBUTE_CONVERSATION_NAME));
    }
    Map<String, Object> innerAttribute = new HashMap<String, Object>();
    for (String k : attributes.keySet()) {
      if (!Conversation.CONVERSATION_COLUMN_LIST.contains(k)) {
        innerAttribute.put(k, attributes.get(k));
      }
    }
    if (!innerAttribute.isEmpty()) {
      attributeMap.put(Conversation.ATTRIBUTE_MORE, innerAttribute);
    }
    if (attributeMap.isEmpty()) {
      return null;
    }
    return new JSONObject(attributeMap);
  }

  interface OperationCompleteCallback {
    void onComplete();

    void onFailure();
  }
}
