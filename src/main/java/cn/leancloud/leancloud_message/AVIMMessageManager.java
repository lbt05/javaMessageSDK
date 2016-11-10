package cn.leancloud.leancloud_message;

import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.util.SparseArray;
import cn.leancloud.leancloud_message.callback.AVIMConversationCallback;
import cn.leancloud.leancloud_message.messages.AVIMAudioMessage;
import cn.leancloud.leancloud_message.messages.AVIMFileMessage;
import cn.leancloud.leancloud_message.messages.AVIMImageMessage;
import cn.leancloud.leancloud_message.messages.AVIMLocationMessage;
import cn.leancloud.leancloud_message.messages.AVIMTextMessage;
import cn.leancloud.leancloud_message.messages.AVIMVideoMessage;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.avos.avoscloud.AVOSCloud;
import com.avos.avoscloud.LogUtil;

public class AVIMMessageManager {

  static SparseArray<Class<? extends AVIMTypedMessage>> messageTypesRepository =
      new SparseArray<Class<? extends AVIMTypedMessage>>();
  static AVIMMessageHandler defaultMessageHandler;
  static ConcurrentHashMap<Class<? extends AVIMMessage>, Set<MessageHandler>> messageHandlerRepository =
      new ConcurrentHashMap<Class<? extends AVIMMessage>, Set<MessageHandler>>();

  static AVIMConversationEventHandler conversationEventHandler;

  static {
    registerAVIMMessageType(AVIMTextMessage.class);
    registerAVIMMessageType(AVIMFileMessage.class);
    registerAVIMMessageType(AVIMImageMessage.class);
    registerAVIMMessageType(AVIMAudioMessage.class);
    registerAVIMMessageType(AVIMVideoMessage.class);
    registerAVIMMessageType(AVIMLocationMessage.class);
  }

  /**
   * 注册自定义的消息类型
   *
   * @param messageType
   */
  public static void registerAVIMMessageType(Class<? extends AVIMTypedMessage> messageType) {
    AVIMMessageType type = messageType.getAnnotation(AVIMMessageType.class);
    if (type == null) {
      throw new IncompleteAnnotationException(AVIMMessageType.class, "type");
    }
    int messageTypeValue = type.type();

    messageTypesRepository.put(messageTypeValue, messageType);
    try {
      Method initializeMethod = messageType.getDeclaredMethod("computeFieldAttribute", Class.class);
      initializeMethod.setAccessible(true);
      initializeMethod.invoke(null, messageType);
    } catch (Exception e) {
      if (AVOSCloud.showInternalDebugLog()) {
        LogUtil.avlog.d("failed to initialize message Fields");
      }
    }
  }

  /**
   * 注册一般情况下的消息handler，只有在没有类型的AVIMMessage或者没有其他handler时才会被调用
   * <p/>
   * 请在Application初始化时设置
   *
   * @param handler
   */
  public static void registerDefaultMessageHandler(AVIMMessageHandler handler) {
    defaultMessageHandler = handler;
  }

  /**
   * 注册特定消息格式的处理单元
   *
   * @param clazz 特定的消息类
   * @param handler
   */
  public static void registerMessageHandler(Class<? extends AVIMMessage> clazz,
      MessageHandler<?> handler) {
    Set<MessageHandler> handlerSet = new HashSet<MessageHandler>();;

    Set<MessageHandler> set = messageHandlerRepository.putIfAbsent(clazz, handlerSet);
    if (set != null) {
      handlerSet = set;
    }
    handlerSet.add(handler);
  }

  /**
   * 取消特定消息格式的处理单元
   *
   * @param clazz
   * @param handler
   */
  public static void unregisterMessageHandler(Class<? extends AVIMMessage> clazz,
      MessageHandler<?> handler) {
    Set<MessageHandler> handlerSet = messageHandlerRepository.get(clazz);
    if (handlerSet != null) {
      handlerSet.remove(handler);
    }
  }

  /**
   * 设置Conversataion相关事件的处理单元,
   * <p/>
   * 推荐在Application初始化时设置
   *
   * @param handler
   */
  public static void setConversationEventHandler(AVIMConversationEventHandler handler) {
    conversationEventHandler = handler;
  }

  protected static AVIMConversationEventHandler getConversationEventHandler() {
    return conversationEventHandler;
  }

  protected static void processMessage(AVIMMessage message, AVIMClient client, boolean hasMore,
      boolean isTransient) {
    message = parseTypedMessage(message);
    final AVIMConversation conversation = client.getConversation(message.getConversationId());
    conversation.setLastMessageAt(new Date(message.getTimestamp()));

    if (conversation.isShouldFetch()) {
      final AVIMMessage finalMessageObject = message;
      conversation.fetchInfoInBackground(new AVIMConversationCallback() {

        @Override
        public void done(AVIMException e) {
          if (null != e && e.getCode() > 0) {
            conversation.latestConversationFetch = System.currentTimeMillis();
          }
          retrieveAllMessageHandlers(finalMessageObject, conversation, false);
        }
      });
    } else {
      retrieveAllMessageHandlers(message, conversation, false);
    }
  }

  protected static void processMessageReceipt(AVIMMessage message, AVIMClient client) {
    message = parseTypedMessage(message);
    final AVIMMessage finalMessageObject = message;
    final AVIMConversation conversation = client.getConversation(message.getConversationId());
    if (conversation.isShouldFetch()) {
      conversation.fetchInfoInBackground(new AVIMConversationCallback() {

        @Override
        public void done(AVIMException e) {
          if (null != e && e.getCode() > 0) {
            conversation.latestConversationFetch = System.currentTimeMillis();
          }
          retrieveAllMessageHandlers(finalMessageObject, conversation, true);
        }
      });
    } else {
      retrieveAllMessageHandlers(message, conversation, true);
    }
  }

  private static void retrieveAllMessageHandlers(AVIMMessage message,
      AVIMConversation conversation, boolean receipt) {
    boolean messageProcessed = false;
    for (Class clazzKey : messageHandlerRepository.keySet()) {
      if (clazzKey.isAssignableFrom(message.getClass())) {
        Set<MessageHandler> handlers = messageHandlerRepository.get(clazzKey);
        if (handlers.size() > 0) {
          messageProcessed = true;
        }
        for (MessageHandler handler : handlers) {
          if (receipt) {
            handler.processEvent(Conversation.STATUS_ON_MESSAGE_RECEIPTED, null, message,
                conversation);
          } else {
            handler.processEvent(Conversation.STATUS_ON_MESSAGE, null, message, conversation);
          }
        }
      }
    }
    if (!messageProcessed && defaultMessageHandler != null) {
      if (receipt) {
        defaultMessageHandler.processEvent(Conversation.STATUS_ON_MESSAGE_RECEIPTED, null, message,
            conversation);
      } else {
        defaultMessageHandler.processEvent(Conversation.STATUS_ON_MESSAGE, null, message,
            conversation);
      }
    }
  }

  /**
   * 解析AVIMMessage对象的子类
   * 
   * @param message
   * @return
   */
  protected static AVIMMessage parseTypedMessage(AVIMMessage message) {
    int messageType = getMessageType(message.getContent());
    if (messageType != 0) {
      Class<? extends AVIMTypedMessage> clazz = messageTypesRepository.get(messageType);
      if (clazz != null) {
        try {
          AVIMMessage typedMessage = clazz.newInstance();
          typedMessage.setConversationId(message.getConversationId());
          typedMessage.setFrom(message.getFrom());
          typedMessage.setReceiptTimestamp(message.getReceiptTimestamp());
          typedMessage.setTimestamp(message.getTimestamp());
          typedMessage.setContent(message.getContent());
          typedMessage.setMessageId(message.getMessageId());
          typedMessage.setMessageStatus(message.getMessageStatus());
          typedMessage.setMessageIOType(message.getMessageIOType());
          typedMessage.uniqueToken = message.uniqueToken;
          message = typedMessage;
        } catch (Exception e) {
        }
      }
    }
    return message;
  }

  private static int getMessageType(String messageContent) {
    try {
      JSONObject object = JSON.parseObject(messageContent);
      int type = object.getInteger("_lctype");
      return type;
    } catch (Exception e) {

    }
    return 0;
  }
}
