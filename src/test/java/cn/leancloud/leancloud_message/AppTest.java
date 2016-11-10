package cn.leancloud.leancloud_message;

import java.util.concurrent.TimeUnit;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;

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

  /**
   * Rigourous Test :-)
   */
  public void testApp() {
    assertTrue(true);
  }

  public void testExpireMap() throws InterruptedException {
    ExpiringMap<String, Long> receiver =
        ExpiringMap.builder().expiration(1, TimeUnit.SECONDS)
            .asyncExpirationListener(new ExpirationListener<String, Long>() {

              @Override
              public void expired(String k, Long v) {
                System.out.println(k + ":" + v + System.currentTimeMillis());
              }
            }).build();
    receiver.put("action", 123l);
    System.out.println(System.currentTimeMillis());
    Thread.sleep(2000);
    System.out.println(receiver.size());
  }
}
