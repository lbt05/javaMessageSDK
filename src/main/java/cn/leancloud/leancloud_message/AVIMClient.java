package cn.leancloud.leancloud_message;

import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.util.TextUtils;

import cn.leancloud.leancloud_message.Conversation.AVIMOperation;
import cn.leancloud.leancloud_message.callback.AVIMClientCallback;
import cn.leancloud.leancloud_message.callback.AVIMConversationCreatedCallback;
import cn.leancloud.leancloud_message.callback.AVIMOnlineClientsCallback;

import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVInstallation;
import com.avos.avoscloud.AVUtils;

/**
 * 实时聊天的实例类，一个实例表示一个用户的登录
 *
 * 每个用户的登录都是一个单例
 * 
 */
public class AVIMClient {
  String clientId;
  String tag;
  static ConcurrentHashMap<String, AVIMClient> clients =
      new ConcurrentHashMap<String, AVIMClient>();
  ConcurrentHashMap<String, AVIMConversation> conversationCache =
      new ConcurrentHashMap<String, AVIMConversation>();

  static SignatureFactory factory;
  final AtomicBoolean sessionOpened = new AtomicBoolean(false);
  final AtomicBoolean sessionPaused = new AtomicBoolean(false);

  private static boolean isAutoOpen = true;
  protected static int timeoutInSecs = Session.DEFAULT_SESSION_TIMEOUT_SECS;
  static ExecutorService signatureTaskPool = Executors.newFixedThreadPool(10);
  /**
   * 离线消息推送模式 true 为仅推送数量，false 为推送具体消息
   */
  static boolean onlyPushCount = false;

  public SignatureFactory getSignatureFactory() {
    return factory;
  }

  /**
   * 设置AVIMClient通用的签名生成工厂
   * 
   * @param factory
   * @since 3.0
   */

  public static void setSignatureFactory(SignatureFactory factory) {
    AVIMClient.factory = factory;
  }

  /**
   * 设置实时通信的超时时间
   * 
   * 默认为15s
   * 
   * @param timeoutInSecs 设置超时时间
   */

  public static void setTimeoutInSecs(int timeoutInSecs) {
    AVIMClient.timeoutInSecs = timeoutInSecs;
  }

  /**
   * 设置实时通信是否要在 App 重新启动后自动登录
   * 
   * @param isAuto
   */
  public static void setAutoOpen(boolean isAuto) {
    isAutoOpen = isAuto;
  }

  /**
   * 实时通信是否要在 App 重新启动后自动登录
   */
  public static boolean isAutoOpen() {
    return isAutoOpen;
  }

  protected AVIMClient(String clientId) {
    this.clientId = clientId;
  }

  /**
   * 获取一个聊天客户端实例
   * 
   * @param clientId 当前客户端登录账号的id
   * @return
   * @since 3.0
   */

  public static AVIMClient getInstance(String clientId) {
    if (TextUtils.isEmpty(clientId)) {
      throw new IllegalArgumentException("clientId cannot be null.");
    }

    AVIMClient client = clients.get(clientId);
    if (client != null) {
      return client;
    } else {
      client = new AVIMClient(clientId);
      AVIMClient elderClient = clients.putIfAbsent(clientId, client);
      return elderClient == null ? client : elderClient;
    }
  }

  /**
   * 获取聊天客户端实例，并且标记这个客户端实例的tag
   * 
   * @param clientId 当前客户端登录账号的id
   * @param tag 用于标注客户端，以支持单点登录功能
   * @return
   */
  public static AVIMClient getInstance(String clientId, String tag) {
    AVIMClient client = getInstance(clientId);
    client.tag = tag;
    return client;
  }

  /**
   * 连接服务器
   * 
   * @param callback
   * @since 3.0
   */
  public void open(final AVIMClientCallback callback) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put(Session.PARAM_SESSION_PEERIDS, new LinkedList<String>());
    if (!AVUtils.isBlankString(tag)) {
      params.put(Session.PARAM_SESSION_TAG, tag);
    }
    if (sessionOpened.get()) {
      callback.done(this, null);
      return;
    }

    SignatureCallback signatureCallback = new SignatureCallback() {

      @Override
      public void onSignatureReady(Signature sig, AVException e) {
        {
          if (e == null) {
            int requestId = AVUtils.getNextIMRequestId();
            AVIMBaseBroadcastReceiver receiver = new AVIMBaseBroadcastReceiver(callback) {
              @Override
              public void execute(Intent intent, Throwable error) {
                if (error != null) {
                  sessionOpened.set(true);
                } else {
                  sessionOpened.set(false);
                }
                if (callback != null)
                  callback.internalDone(AVIMClient.this, AVIMException.wrapperAVException(error));
                if (error != null) {
                  AVInstallation.getCurrentInstallation().addUnique("channels", clientId);
                  AVInstallation.getCurrentInstallation().saveInBackground();
                }
              }
            };
            AVIMBaseBroadcastReceiver.register(requestId, receiver);
            Intent intent = new Intent();
            intent.putExtra(Conversation.INTENT_KEY_CLIENT, AVIMClient.this.clientId);
            intent.putExtra(Conversation.INTENT_KEY_REQUESTID, requestId);
            intent.putExtra(Conversation.INTENT_KEY_DATA, tag);
            intent.putExtra(Conversation.INTENT_KEY_SIGNATURE, sig);
            AVIMServer.getInstance().sendData(
                AVIMOperation.CLIENT_OPEN.genCommand(intent));
          } else {
            if (callback != null) {
              callback.done(AVIMClient.this, new AVIMException(e));
            }
          }
        }
      }

      @Override
      public Signature computeSignature() throws SignatureException {
        if (factory != null) {
          return factory.createSignature(AVIMClient.this.getClientId());
        }
        return null;
      }

      @Override
      public boolean cacheSignature() {
        return false;
      }

    };
    signatureTaskPool.execute(new SignatureTask(signatureCallback));
  }

  /**
   * 获取client列表中间当前为在线的client
   *
   * 每次查询最多20个client对象，超出部分会被忽略
   * 
   * @param clients 需要检查的client列表
   * @param callback
   */
  public void getOnlineClients(List<String> clients, final AVIMOnlineClientsCallback callback) {
    int requestId = AVUtils.getNextIMRequestId();
    AVIMBaseBroadcastReceiver receiver = null;

    if (callback != null) {
      receiver = new AVIMBaseBroadcastReceiver(callback) {
        @Override
        public void execute(Intent intent, Throwable error) {
          if (error != null) {
            if (callback != null) {
              callback.internalDone(null, AVIMException.wrapperAVException(error));
            }
          } else {
            List<String> onlineClients =
                (List<String>) intent.getExtras().get(Conversation.callbackOnlineClients);
            if (callback != null) {
              callback.internalDone(onlineClients, null);
            }
          }
        }
      };
    }

    AVIMBaseBroadcastReceiver.register(requestId, receiver);
    Intent intent = new Intent();
    intent.putExtra(Conversation.INTENT_KEY_REQUESTID, requestId);
    intent.putExtra(Conversation.INTENT_KEY_CLIENT, clientId);
    intent.putExtra(Conversation.INTENT_KEY_DATA, clients);
    AVIMServer.getInstance().sendData(
        AVIMOperation.CLIENT_ONLINE_QUERY.genCommand(intent));
  }

  /**
   * 
   * @return 返回clientId
   */
  public String getClientId() {
    return this.clientId;
  }

  /**
   * 创建一个聊天对话
   * 
   * @param conversationMembers 对话参与者
   * @param attributes 对话属性
   * @param callback
   * @since 3.0
   */

  public void createConversation(final List<String> conversationMembers,
      final Map<String, Object> attributes, final AVIMConversationCreatedCallback callback) {
    this.createConversation(conversationMembers, null, attributes, false, callback);
  }

  public void createConversation(final List<String> conversationMembers, String name,
      final Map<String, Object> attributes, final AVIMConversationCreatedCallback callback) {
    this.createConversation(conversationMembers, name, attributes, false, callback);
  }

  /**
   * 创建一个聊天对话
   * 
   * @param members 对话参与者
   * @param attributes 对话的额外属性
   * @param isTransient 是否创建
   * @param callback
   */
  public void createConversation(final List<String> members, final String name,
      final Map<String, Object> attributes, final boolean isTransient,
      final AVIMConversationCreatedCallback callback) {
    this.createConversation(members, name, attributes, isTransient, false, callback);
  }

  /**
   * 创建或查询一个已有 conversation
   *
   * @param members 对话的成员
   * @param name 对话的名字
   * @param attributes 对话的额外属性
   * @param isTransient 是否是暂态会话
   * @param isUnique 如果已经存在符合条件的会话，是否返回已有回话 为 false 时，则一直为创建新的回话 为 true
   *        时，则先查询，如果已有符合条件的回话，则返回已有的，否则，创建新的并返回 为 true 时，仅 members 为有效查询条件
   * @param callback
   */
  public void createConversation(final List<String> members, final String name,
      final Map<String, Object> attributes, final boolean isTransient, final boolean isUnique,
      final AVIMConversationCreatedCallback callback) {
    try {
      AVUtils.ensureElementsNotNull(members, Session.ERROR_INVALID_SESSION_ID);
    } catch (Exception e) {
      if (callback != null) {
        callback.internalDone(null, AVIMException.wrapperAVException(e));
      }
      return;
    }

    if (!sessionOpened.get()) {
      if (callback != null) {
        callback.done(null, new AVIMException(new IllegalStateException("Session is not open.")));
      }
      return;
    }

    final HashMap<String, Object> conversationAttributes = new HashMap<String, Object>();
    if (attributes != null) {
      conversationAttributes.putAll(attributes);
    }
    if (!AVUtils.isBlankString(name)) {
      conversationAttributes.put(AVIMConversation.NAME_KEY, name);
    }
    final List<String> conversationMembers = new ArrayList<String>();
    conversationMembers.addAll(members);
    if (!conversationMembers.contains(clientId)) {
      conversationMembers.add(clientId);
    }

    SignatureCallback signatureCallback = new SignatureCallback() {
      @Override
      public void onSignatureReady(Signature sig, AVException e) {
        {
          if (e == null) {
            final int requestId = AVUtils.getNextIMRequestId();

            final AVIMBaseBroadcastReceiver receiver = new AVIMBaseBroadcastReceiver(callback) {
              @Override
              public void execute(Intent intent, Throwable error) {
                String conversationId =
                    (String) intent.getExtras().get(Conversation.callbackConversationKey);
                String createdAt = (String) intent.getExtras().get(Conversation.callbackCreatedAt);
                AVIMConversation conversation = null;
                if (error == null) {
                  conversation =
                      new AVIMConversation(AVIMClient.this, conversationMembers,
                          conversationAttributes, isTransient);
                  conversation.setConversationId(conversationId);
                  conversation.setCreator(clientId);
                  conversation.setCreatedAt(createdAt);
                  conversation.setUpdatedAt(createdAt);
                  conversationCache.put(conversationId, conversation);
                }
                if (callback != null) {
                  callback.internalDone(conversation, AVIMException.wrapperAVException(error));
                }
              }
            };

            AVIMBaseBroadcastReceiver.register(requestId, receiver);
            Intent intent = new Intent();
            intent.putExtra(Conversation.INTENT_KEY_CLIENT, AVIMClient.this.clientId);
            intent.putExtra(Conversation.INTENT_KEY_REQUESTID, requestId);
            intent.putExtra(Conversation.INTENT_KEY_SIGNATURE, sig);
            intent.putExtra(Conversation.PARAM_CONVERSATION_MEMBER, members);
            intent.putExtra(Conversation.PARAM_CONVERSATION_ATTRIBUTE, attributes);
            intent.putExtra(Conversation.PARAM_CONVERSATION_ISTRANSIENT, isTransient);
            intent.putExtra(Conversation.PARAM_CONVERSATION_ISUNIQUE, isUnique);
            AVIMServer.getInstance().sendData(
                AVIMOperation.CLIENT_OPEN.genCommand(intent));
          } else {
            if (callback != null) {
              callback.done(null, new AVIMException(e));
            }
          }
        }
      }

      @Override
      public Signature computeSignature() throws SignatureException {
        if (factory != null) {
          return factory.createSignature(AVIMClient.this.getClientId());
        }
        return null;
      }

      @Override
      public boolean cacheSignature() {
        return false;
      }
    };

    signatureTaskPool.execute(new SignatureTask(signatureCallback));
  }

  /**
   * 获取某个特定的聊天对话
   * 
   * @param conversationId 对应的是_Conversation表中的objectId
   * @return
   * @since 3.0
   */
  public AVIMConversation getConversation(String conversationId) {
    AVIMConversation conversation = conversationCache.get(conversationId);
    if (conversation != null) {
      return conversation;
    } else {
      conversation = new AVIMConversation(this, conversationId);
      AVIMConversation elderConversation =
          conversationCache.putIfAbsent(conversationId, conversation);
      return elderConversation == null ? conversation : elderConversation;
    }
  }


  /**
   * 获取AVIMConversationQuery对象，以此来查询conversation
   * 
   * @return
   */
  public AVIMConversationQuery getQuery() {
    return new AVIMConversationQuery(this);
  }

  static AVIMClientEventHandler clientEventHandler;

  /**
   * 设置AVIMClient的事件处理单元，
   * 
   * 包括Client断开链接和重连成功事件
   * 
   * @param handler
   */
  public static void setClientEventHandler(AVIMClientEventHandler handler) {
    clientEventHandler = handler;
  }

  protected static AVIMClientEventHandler getClientEventHandler() {
    return clientEventHandler;
  }

  /**
   * 设置离线消息推送模式
   * 
   * @param isOnlyCount true 为仅推送离线消息数量，false 为推送离线消息
   */
  public static void setOfflineMessagePush(boolean isOnlyCount) {
    AVIMClient.onlyPushCount = isOnlyCount;
  }

  /**
   * 注销当前的聊天客户端链接
   * 
   * @param callback
   * @since 3.0
   */
  public void close(final AVIMClientCallback callback) {
    if (!sessionOpened.compareAndSet(true, false)) {
      if (callback != null) {
        callback.done(this, null);
      }
      return;
    }
    AVIMBaseBroadcastReceiver receiver = new AVIMBaseBroadcastReceiver(callback) {
      @Override
      public void execute(Intent intent, Throwable error) {
        AVIMClient.this.close();
        if (callback != null) {
          callback.internalDone(AVIMClient.this, AVIMException.wrapperAVException(error));
        }
        if (error != null) {
          clients.remove(AVIMClient.this);
          AVInstallation.getCurrentInstallation().removeAll("channels", Arrays.asList(clientId));
          AVInstallation.getCurrentInstallation().saveInBackground();
        }
      }
    };
    int requestId = AVUtils.getNextIMRequestId();
    AVIMBaseBroadcastReceiver.register(requestId, receiver);
    Intent intent = new Intent();
    intent.putExtra(Conversation.INTENT_KEY_REQUESTID, requestId);
    intent.putExtra(Conversation.INTENT_KEY_CLIENT, this.clientId);
    AVIMServer.getInstance().sendData(
        AVIMOperation.CLIENT_DISCONNECT.genCommand(intent));
  }

  /**
   * Local close to clean up
   */
  protected void close() {
    clients.remove(clientId);
    sessionOpened.set(false);
    conversationCache.clear();
  }

  /**
   * 当前client的状态
   */
  public enum AVIMClientStatus {
    /**
     * 当前client尚未open，或者已经close
     */
    AVIMClientStatusNone(110),
    /**
     * 当前client已经打开，连接正常
     */
    AVIMClientStatusOpened(111),
    /**
     * 当前client由于网络因素导致的连接中断
     */
    AVIMClientStatusPaused(120);

    int code;

    AVIMClientStatus(int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }

    static AVIMClientStatus getClientStatus(int code) {
      switch (code) {
        case 110:
          return AVIMClientStatusNone;
        case 111:
          return AVIMClientStatusOpened;
        case 120:
          return AVIMClientStatusPaused;
        default:
          return null;
      }
    };
  }

  public AVIMClientStatus getClientStatus() {
    return sessionOpened.get() ? (sessionPaused.get() ? AVIMClientStatus.AVIMClientStatusPaused
        : AVIMClientStatus.AVIMClientStatusOpened) : AVIMClientStatus.AVIMClientStatusNone;
  }

  static AVException validateNonEmptyConversationMembers(List<String> members) {
    if (members == null || members.isEmpty()) {
      return new AVException(AVException.UNKNOWN,
          "Conversation can't be created with empty members");
    }
    try {
      AVUtils.ensureElementsNotNull(members, Session.ERROR_INVALID_SESSION_ID);
    } catch (Exception e) {
      return new AVException(e);
    }
    return null;
  }

  protected void removeConversationCache(AVIMConversation conversation) {
    conversationCache.remove(conversation.getConversationId());
  }

  @Override
  public boolean equals(Object object) {
    if (object == null) {
      return false;
    }
    AVIMClient anotherClient = (AVIMClient) object;
    if (clientId == null) {
      return anotherClient.clientId == null;
    }
    return this.clientId.equals(anotherClient.clientId);
  }

  protected static class SignatureTask implements Runnable {
    SignatureCallback callback;

    public SignatureTask(SignatureCallback callback) {
      this.callback = callback;
    }

    @Override
    public void run() {
      if (callback != null) {
        try {
          Signature sig = callback.computeSignature();
        } catch (SignatureException e) {
          callback.onSignatureReady(null, new AVException(e));
        }
      }
    }
  }

}
