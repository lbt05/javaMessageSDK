package cn.leancloud.leancloud_message;


import java.util.UUID;

import com.avos.avoscloud.AVUtils;

public class AVIMMessage {

  public AVIMMessage() {
    this(null, null);
  }

  public AVIMMessage(String conversationId, String from) {
    this(conversationId, from, 0, 0);
  }

  public AVIMMessage(String conversationId, String from, long timestamp, long receiptTimestamp) {
    this.ioType = AVIMMessageIOType.AVIMMessageIOTypeOut;
    this.status = AVIMMessageStatus.AVIMMessageStatusNone;
    this.conversationId = conversationId;
    this.from = from;
    this.timestamp = timestamp;
    this.receiptTimestamp = receiptTimestamp;
  }

  /**
   * 获取当前聊天对话对应的id
   * <p/>
   * 对应的是AVOSRealtimeConversations表中的objectId
   *
   * @return
   * @since 3.0
   */
  public String getConversationId() {
    return conversationId;
  }

  /**
   * 设置消息所在的conversationId，本方法一般用于从反序列化时
   *
   * @param conversationId
   */
  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
  }

  /**
   * 获取消息体的内容
   *
   * @return
   * @since 3.0
   */
  public String getContent() {
    return content;
  }

  /**
   * 设置消息体的内容
   *
   * @param content
   * @since 3.0
   */
  public void setContent(String content) {
    this.content = content;
  }

  /**
   * 获取消息的发送者
   *
   * @return
   */
  public String getFrom() {
    return from;
  }

  /**
   * 设置消息的发送者
   *
   * @param from
   * @since 3.7.3
   */
  public void setFrom(String from) {
    this.from = from;
  }

  /**
   * 获取消息发送的时间
   *
   * @return
   */
  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * 获取消息成功到达接收方的时间
   *
   * @return
   * @see AVIMConversation#RECEIPT_MESSAGE_FLAG
   */

  public long getReceiptTimestamp() {
    return receiptTimestamp;
  }

  public void setReceiptTimestamp(long receiptTimestamp) {
    this.receiptTimestamp = receiptTimestamp;
  }

  /**
   * 设置消息当前的状态，本方法一般用于从反序列化时
   *
   * @param status
   */
  public void setMessageStatus(AVIMMessageStatus status) {
    this.status = status;
  }

  /**
   * 获取消息当前的状态
   *
   * @return
   */

  public AVIMMessageStatus getMessageStatus() {
    return this.status;
  }

  /**
   * 获取消息IO类型
   *
   * @return
   */
  public AVIMMessageIOType getMessageIOType() {
    return ioType;
  }

  /**
   * 设置消息的IO类型，本方法一般用于反序列化
   *
   * @param ioType
   */
  public void setMessageIOType(AVIMMessageIOType ioType) {
    this.ioType = ioType;
  }

  /**
   * 获取消息的全局Id
   * <p/>
   * 这个id只有在发送成功或者收到消息时才会有对应的值
   *
   * @return
   */
  public String getMessageId() {
    return messageId;
  }

  /**
   * 仅仅是用于反序列化消息时使用，请不要在其他时候使用
   *
   * @param messageId
   */
  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  String conversationId;
  String content;
  String from;
  long timestamp;
  long receiptTimestamp;

  String messageId;
  String uniqueToken;

  AVIMMessageStatus status;
  AVIMMessageIOType ioType;

  protected synchronized void genUniqueToken() {
    if (AVUtils.isBlankString(uniqueToken)) {
      uniqueToken = UUID.randomUUID().toString();
    }
  }

  protected String getUniqueToken() {
    return uniqueToken;
  }

  /**
   * Created by lbt05 on 3/17/15.
   * <p/>
   * 用于标注AVIMMessage现在所处的状态 主要用于UI标注等用户
   */
  public enum AVIMMessageStatus {
    AVIMMessageStatusNone(0), AVIMMessageStatusSending(1), AVIMMessageStatusSent(2), AVIMMessageStatusReceipt(
        3), AVIMMessageStatusFailed(4);
    int statusCode;

    AVIMMessageStatus(int status) {
      this.statusCode = status;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public static AVIMMessageStatus getMessageStatus(int statusCode) {
      switch (statusCode) {
        case 0:
          return AVIMMessageStatusNone;
        case 1:
          return AVIMMessageStatusSending;
        case 2:
          return AVIMMessageStatusSent;
        case 3:
          return AVIMMessageStatusReceipt;
        case 4:
          return AVIMMessageStatusFailed;
        default:
          return null;
      }
    }
  }


  public enum AVIMMessageIOType {
    /**
     * 标记收到的消息
     */
    AVIMMessageIOTypeIn(1),
    /**
     * 标记发送的消息
     */
    AVIMMessageIOTypeOut(2);
    int ioType;

    AVIMMessageIOType(int type) {
      this.ioType = type;
    }

    public int getIOType() {
      return ioType;
    }

    public static AVIMMessageIOType getMessageIOType(int type) {
      switch (type) {
        case 1:
          return AVIMMessageIOTypeIn;
        case 2:
          return AVIMMessageIOTypeOut;
      }
      return AVIMMessageIOTypeOut;
    }
  }
}
