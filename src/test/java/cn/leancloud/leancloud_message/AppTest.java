package cn.leancloud.leancloud_message;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import cn.leancloud.leancloud_message.callback.AVIMClientCallback;
import cn.leancloud.leancloud_message.callback.AVIMConversationCallback;
import cn.leancloud.leancloud_message.callback.AVIMConversationCreatedCallback;

import com.avos.avoscloud.AVOSCloud;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase {
  /**
   * Create the test case
   *
   * @param testName name of the test case
   */
  public AppTest(String testName) {
    super(testName);
  }

  /**
   * @return the suite of tests being tested
   */
  public static Test suite() {
    return new TestSuite(AppTest.class);
  }

  String clientId = "testAccount";

  public String prepareConversation() throws InterruptedException {
    final CountDownLatch lock = new CountDownLatch(1);
    AVOSCloud.setDebugLogEnabled(true);
    LeanMessage.initialize("anruhhk6visejjip57psvv5uuv8sggrzdfl9pg2bghgsiy35",
        "xhiibo2eiyokjdu2y3kqcb7334rtw4x33zam98buxzkjuq5g");
    AVIMClient client = AVIMClient.getInstance(clientId);
    final String[] result = new String[1];
    client.open(new AVIMClientCallback() {

      @Override
      public void done(AVIMClient client, AVIMException e) {
        if (e != null) {
          e.printStackTrace();
        } else {
          client.createConversation(Arrays.asList("testAccount", "Tango"), "testConversation",
              null, false, true, new AVIMConversationCreatedCallback() {

                @Override
                public void done(AVIMConversation conversation, AVIMException e) {
                  if (e != null) {
                    e.printStackTrace();
                  } else {
                    result[0] = conversation.getConversationId();
                  }
                  lock.countDown();
                }
              });
        }
      }
    });
    lock.await();
    return result[0];
  }

  public void testSendMessage() throws Exception {
    String conversationId = prepareConversation();
    final CountDownLatch lock = new CountDownLatch(1);
    AVIMClient client = AVIMClient.getInstance(clientId);
    AVIMConversation conversation = client.getConversation(conversationId);
    AVIMMessage msg = new AVIMMessage();
    msg.setContent("Test MSG");

    final Exception[] result = new Exception[1];
    conversation.sendMessage(msg, AVIMConversation.NONTRANSIENT_MESSAGE_FLAG,
        new AVIMConversationCallback() {

          @Override
          public void done(AVIMException e) {
            result[0] = e;
            if (e != null) {
              e.printStackTrace();
            }
            lock.countDown();
          }
        });
    lock.await();
    Assert.assertNull(result[0]);
  }
}
