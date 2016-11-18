package cn.leancloud.leancloud_message;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import cn.leancloud.leancloud_message.callback.AVIMClientCallback;
import cn.leancloud.leancloud_message.callback.AVIMConversationCallback;
import cn.leancloud.leancloud_message.callback.AVIMConversationCreatedCallback;
import cn.leancloud.leancloud_message.callback.AVIMConversationMemberCountCallback;
import cn.leancloud.leancloud_message.callback.AVIMConversationQueryCallback;
import cn.leancloud.leancloud_message.callback.AVIMMessagesQueryCallback;

import com.avos.avoscloud.AVOSCloud;
import com.avos.avoscloud.AVUtils;

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

  public void setUp() {
    AVOSCloud.setDebugLogEnabled(true);
    AVIMClient.setClientEventHandler(new CustomClientEventHandler());
    AVIMMessageManager.setConversationEventHandler(new CustomConversationEventHandler());

    LeanMessage.initialize("anruhhk6visejjip57psvv5uuv8sggrzdfl9pg2bghgsiy35",
        "xhiibo2eiyokjdu2y3kqcb7334rtw4x33zam98buxzkjuq5g");
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

  public void testSendMessageNonTransient() throws Exception {
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

  public void testSendMessageTransient() throws Exception {
    String conversationId = prepareConversation();
    final CountDownLatch lock = new CountDownLatch(1);
    AVIMClient client = AVIMClient.getInstance(clientId);
    AVIMConversation conversation = client.getConversation(conversationId);
    AVIMMessage msg = new AVIMMessage();
    msg.setContent("Test MSG");
    final Exception[] result = new Exception[1];
    conversation.sendMessage(msg, AVIMConversation.TRANSIENT_MESSAGE_FLAG,
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

  public void testChangeMember() throws Exception {
    String conversationId = prepareConversation();
    final CountDownLatch lock = new CountDownLatch(1);
    AVIMClient client = AVIMClient.getInstance(clientId);
    AVIMConversation conversation = client.getConversation(conversationId);
    final Exception[] result = new Exception[1];
    conversation.addMembers(Arrays.asList("QiaQia"), new AVIMConversationCallback() {

      @Override
      public void done(AVIMException e) {
        result[0] = e;
        lock.countDown();
      }
    });
    lock.await();
    Assert.assertNull(result[0]);
    final CountDownLatch lock1 = new CountDownLatch(1);
    conversation.getMemberCount(new AVIMConversationMemberCountCallback() {

      @Override
      public void done(Integer memberCount, AVIMException e) {
        Assert.assertNull(e);
        Assert.assertEquals(3, memberCount.intValue());
        System.out.println("SHIT");
        lock1.countDown();
      }
    });
    lock1.await();
    final CountDownLatch lock2 = new CountDownLatch(1);
    conversation.kickMembers(Arrays.asList("QiaQia"), new AVIMConversationCallback() {

      @Override
      public void done(AVIMException e) {
        result[0] = e;
        lock2.countDown();
      }
    });
    lock2.await();
    Assert.assertNull(result[0]);
  }

  public void testUpdateAttribute() throws Exception {
    String conversationId = prepareConversation();
    final CountDownLatch lock = new CountDownLatch(1);
    AVIMClient client = AVIMClient.getInstance(clientId);
    AVIMConversation conversation = client.getConversation(conversationId);
    final int randomValue = AVUtils.getNextIMRequestId();
    conversation.setAttribute("seed", randomValue);
    final Exception[] result = new Exception[1];
    conversation.updateInfoInBackground(new AVIMConversationCallback() {

      @Override
      public void done(AVIMException e) {
        result[0] = e;
        lock.countDown();
      }
    });
    lock.await();
    Assert.assertNull(result[0]);
    Assert.assertEquals(randomValue, conversation.getAttribute("seed"));

    AVIMConversationQuery query = client.getQuery();
    query.whereEqualTo("objectId", conversationId);
    final CountDownLatch lock1 = new CountDownLatch(1);
    query.findInBackground(new AVIMConversationQueryCallback() {

      @Override
      public void done(List<AVIMConversation> conversations, AVIMException e) {
        Assert.assertEquals(1, conversations.size());
        Assert.assertEquals(randomValue, conversations.get(0).getAttribute("seed"));
        lock1.countDown();
      }
    });
    lock1.await();
  }

  public void testMessageReceived() throws Exception {
    AVIMClient tango = new AVIMClient("Tango");
    final CountDownLatch lock = new CountDownLatch(1);
    AVIMMessageManager.registerDefaultMessageHandler(new AVIMMessageHandler() {

      @Override
      public void onMessage(AVIMMessage message, AVIMConversation conversation, AVIMClient client) {
        Assert.assertEquals("Tango", client.getClientId());
        Assert.assertEquals(clientId, message.getFrom());
        lock.countDown();
      }
    });

    tango.open(new AVIMClientCallback() {

      @Override
      public void done(AVIMClient client, AVIMException e) {
        Assert.assertNull(e);
      }
    });
    lock.await();
  }

  public void testMessageHistoryQuery() throws Exception {
    String conversationId = prepareConversation();
    final CountDownLatch lock = new CountDownLatch(1);
    AVIMClient client = AVIMClient.getInstance(clientId);
    AVIMConversation conversation = client.getConversation(conversationId);
    conversation.queryMessages(new AVIMMessagesQueryCallback() {

      @Override
      public void done(List<AVIMMessage> messages, AVIMException e) {
        lock.countDown();
        Assert.assertTrue(messages.size() > 0);
        Assert.assertEquals(clientId, messages.get(0).getFrom());
        Assert.assertNull(e);
      }
    });
    lock.await();
  }

  public void testConversationMuteAndUnmute() throws Exception {
    String conversationId = prepareConversation();
    final CountDownLatch lock = new CountDownLatch(1);
    AVIMClient client = AVIMClient.getInstance(clientId);
    final AVIMConversation conversation = client.getConversation(conversationId);
    conversation.mute(new AVIMConversationCallback() {

      @Override
      public void done(AVIMException e) {
        Assert.assertNull(e);
        conversation.unmute(new AVIMConversationCallback() {

          @Override
          public void done(AVIMException e) {
            Assert.assertNull(e);
            lock.countDown();
          }
        });
      }
    });
    lock.await();
  }
}
