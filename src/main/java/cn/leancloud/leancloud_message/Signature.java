package cn.leancloud.leancloud_message;

import java.util.Collections;
import java.util.List;

import com.alibaba.fastjson.annotation.JSONType;

/**
 * Created by nsun on 5/15/14.
 */
@JSONType(ignores = {"expired"})
public class Signature {

  private String signature;

  private long timestamp;

  private String nonce;

  private List<String> signedPeerIds;

  public List<String> getSignedPeerIds() {
    if (signedPeerIds == null) {
      signedPeerIds = Collections.emptyList();
    }
    return signedPeerIds;
  }

  public void setSignedPeerIds(List<String> signedPeerIds) {
    this.signedPeerIds = signedPeerIds;
  }

  public String getSignature() {
    return signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

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

  protected boolean isExpired() {
    return timestamp + 14400 < (System.currentTimeMillis() / 1000);
  }
}
