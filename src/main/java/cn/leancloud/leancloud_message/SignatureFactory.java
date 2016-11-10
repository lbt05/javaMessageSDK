package cn.leancloud.leancloud_message;

import java.security.SignatureException;
import java.util.List;

public interface SignatureFactory {
  /**
   * 实现 AVIMClient 相关的签名计算
   * 
   * @param client
   * @return
   * @throws SignatureException
   */
  public Signature createSignature(String client) throws SignatureException;

  /**
   * 实现AVIMConversation相关的签名计算
   * 
   * @param conversationId
   * @param clientId
   * @param targetIds 操作所对应的数据
   * @param action - 此次行为的动作，行为分别对应常量 invite（加群和邀请）和 kick（踢出群）
   * @return
   * @throws SignatureException 如果签名计算中间发生任何问题请抛出本异常
   */
  public Signature createConversationSignature(String conversationId, String clientId,
      List<String> targetIds, String action) throws SignatureException;
}
