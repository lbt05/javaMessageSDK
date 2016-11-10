package cn.leancloud.leancloud_message;

/**
 * Created by lbt05 on 1/30/15.
 */
public abstract class AVIMEventHandler {
  public void processEvent(final int operation, final Object operator, final Object operand,
      final Object eventScene) {
    processEvent0(operation, operator, operand, eventScene);
  };

  protected abstract void processEvent0(int operation, Object operator, Object operand,
      Object eventScene);
}
