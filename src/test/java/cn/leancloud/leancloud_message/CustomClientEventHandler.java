package cn.leancloud.leancloud_message;

import com.avos.avoscloud.internal.InternalConfigurationController;

public class CustomClientEventHandler extends AVIMClientEventHandler {

  @Override
  public void onConnectionPaused(AVIMClient client) {
    InternalConfigurationController.globalInstance().getInternalLogger()
        .d(CustomClientEventHandler.class.getName(), client.getClientId() + ": paused ");

  }

  @Override
  public void onConnectionResume(AVIMClient client) {
    InternalConfigurationController.globalInstance().getInternalLogger()
        .d(CustomClientEventHandler.class.getName(), client.getClientId() + ": resume ");
  }

  @Override
  public void onClientOffline(AVIMClient client, int code) {
    InternalConfigurationController.globalInstance().getInternalLogger()
        .d(CustomClientEventHandler.class.getName(), client.getClientId() + ": offline " + code);
  }

}
