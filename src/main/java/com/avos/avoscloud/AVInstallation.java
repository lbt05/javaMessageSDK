package com.avos.avoscloud;


import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import android.os.Parcel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.avos.avoscloud.internal.InternalConfigurationController;

/**
 *
 */
@AVClassName("_Installation")
public final class AVInstallation extends AVObject {
  private static final String LOGTAG = AVInstallation.class.getName();
  private static final String INSTALLATION = "installation";
  private static final String DEVICETYPETAG = "deviceType";
  private static final String CHANNELSTAG = "channel";
  private static final String INSTALLATIONIDTAG = "installationId";
  private static final String INSTALLATION_AVNAME = "_Installation";
  public static final String GCM_REGISTRATION_ID = "registrationId";
  private static volatile AVInstallation currentInstallation;
  private volatile String installationId = null;

  static {
    AVPowerfulUtils.createSettings(AVInstallation.class.getSimpleName(), "installations",
        "_Installation");
    AVPowerfulUtils.createSettings("_Installation", "installations", "_Installation");
    AVObject.registerSubclass(AVInstallation.class);
  }


  // setter for fastjson.
  void setInstallationId(String installationId) {
    this.installationId = installationId;
  }

  public static AVInstallation getCurrentInstallation() {
    if (currentInstallation == null) {
      synchronized (AVInstallation.class) {
        // double check.
        if (currentInstallation == null && readInstallationFile() == null) {
          createNewInstallation();
        }
      }
    }
    // 每次取到以后强制加上deviceType跟timeZone...因为在测试过程中间遇到取到null的情况。
    currentInstallation.initialize();
    return currentInstallation;
  }

  private static void createNewInstallation() {
    String id = genInstallationId();
    currentInstallation = new AVInstallation();
    currentInstallation.setInstallationId(id);
    currentInstallation.put(INSTALLATIONIDTAG, id);
    saveCurrentInstalationToLocal();
  }

  private static String genInstallationId() {
    // path
    String buildPath = System.getProperty("java.class.path", null);
    // 机器的mac地址
    String macAddress = getMacAddress();

    String additionalStr = null;
    if (AVUtils.isBlankString(macAddress)) {
      additionalStr = UUID.randomUUID().toString();
    }
    return AVUtils.md5(buildPath + macAddress + additionalStr);
  }

  private static String getMacAddress() {
    return getMacAddressWithNetworkInterface();
  }

  /**
   * 被逼无奈下的获取 mac 地址的方法 对于 6.0 以上的机器,如果通过 getMacAddressWithWifiManager() 不能获取都 mac 地址的话,用此备选方案
   * 如果获取不到 wlan0,则以 eth1 代替,如果再获取不到,那就没办法了
   *
   * @return
   */
  private static String getMacAddressWithNetworkInterface() {
    List<NetworkInterface> interfaceList = null;
    try {
      interfaceList = Collections.list(NetworkInterface.getNetworkInterfaces());
      String wlan = getHardwareAddress(interfaceList, "wlan0");
      if (AVUtils.isBlankString(wlan)) {
        return getHardwareAddress(interfaceList, "eth1");
      } else {
        return wlan;
      }
    } catch (SocketException e) {
    }
    return null;
  }

  private static String getHardwareAddress(List<NetworkInterface> interfaceList, String name) {
    if (null != interfaceList) {
      try {
        for (NetworkInterface networkInterface : interfaceList) {
          String interfaceName = networkInterface.getName();
          if (!AVUtils.isBlankString(interfaceName) && interfaceName.equalsIgnoreCase(name)) {
            byte[] macBytes = networkInterface.getHardwareAddress();
            if (null != macBytes) {
              StringBuilder mac = new StringBuilder();
              for (byte b : macBytes) {
                mac.append(String.format("%02X:", b));
              }
              if (mac.length() > 0) {
                mac.deleteCharAt(mac.length() - 1);
              }
              return mac.toString();
            }
          }
        }
      } catch (Exception e) {
      }
    }
    return null;
  }

  private static void saveCurrentInstalationToLocal() {
    try {
      writeInstallationFile(currentInstallation);
    } catch (Exception e) {
      LogUtil.log.e(LOGTAG, e);
    }
  }

  public AVInstallation() {
    super(INSTALLATION_AVNAME);
    requestStatistic = false;
    initialize();
  }

  public AVInstallation(Parcel in) {
    super(in);
  }

  private void initialize() {
    try {
      if (!AVUtils.isBlankString(getInstallationId())) {
        put(INSTALLATIONIDTAG, getInstallationId(), false);
      }
      if (currentInstallation != null) {
        put(INSTALLATIONIDTAG, currentInstallation.getInstallationId(), false);
      }
      this.put(DEVICETYPETAG, deviceType(), false);
      this.put("timeZone", timezone(), false);
    } catch (IllegalArgumentException exception) {
      // TODO: need to find out why this exception happen
      exception.printStackTrace();
    }
  }

  private static String timezone() {
    TimeZone defaultTimezone = TimeZone.getDefault();
    return defaultTimezone != null ? defaultTimezone.getID() : "unknown";
  }

  // Zeng: finally, we decide to use multi service. The reasons are
  // 1. easy to develop and maintain.
  // 2. use unique id for different apps with avoscloud backend.
  // problems with single service are
  // 1. has to use unique mac address as the installation id.
  // 2. has to communicate with the single service. The service has to
  // serialize package name and class name.
  // 3. The single service has to maintain receiver list.

  /**
   * Returns the unique ID of this installation.
   *
   * @return A UUID that represents this device.
   */
  public String getInstallationId() {
    return installationId;
  }


  @Override
  protected void onSaveSuccess() {
    super.onSaveSuccess();
    try {
      writeInstallationFile(this);
    } catch (Exception e) {
      LogUtil.log.e(LOGTAG, e);
    }
  }

  @Override
  protected void onDataSynchronized() {
    super.onDataSynchronized();
    this.onSaveSuccess();
  }

  /*
   * 如果保存发生了异常或者失败，内存中的AVInstallation进行一次回滚
   */
  @Override
  protected void onSaveFailure() {
    LogUtil.avlog.d("roll back installationId since error there");
    synchronized (AVInstallation.class) {
      if (readInstallationFile() == null) {
        createNewInstallation();
      }
    }
  }

  protected static AVInstallation readInstallationFile() {
    if (!InternalConfigurationController.globalInstance().getAppConfiguration().isConfigured()) {
      throw new IllegalStateException("Please call AVOSCloud.initialize at first in Application");
    }
    String json = "";
    try {
      File cacheDir =
          InternalConfigurationController.globalInstance().getInternalPersistence().getCacheDir();
      if (cacheDir != null && cacheDir.exists() && cacheDir.isDirectory()) {
        File installationFile = new File(cacheDir, INSTALLATION);
        if (installationFile.exists()) {
          json =
              InternalConfigurationController.globalInstance().getInternalPersistence()
                  .readContentFromFile(installationFile);
          if (json.indexOf("{") >= 0) {
            currentInstallation = (AVInstallation) JSON.parse(json);
            return currentInstallation;
          } else {
            if (json.length() == UUID_LEN) {
              // old sdk verson.
              currentInstallation = new AVInstallation();
              currentInstallation.setInstallationId(json);
              // update it
              saveCurrentInstalationToLocal();
              return currentInstallation;
            }
          }
        }
      }
    } catch (Exception e) {
      // try to instance a new installation
      LogUtil.log.e(LOGTAG, json, e);
    }
    return null;
  }

  private static void writeInstallationFile(AVInstallation installation) throws IOException {
    if (installation != null) {
      installation.initialize();
      File cacheDir =
          InternalConfigurationController.globalInstance().getInternalPersistence().getCacheDir();
      if (cacheDir != null && cacheDir.exists() && cacheDir.isDirectory()) {
        File installationFile = new File(cacheDir, INSTALLATION);
        String jsonString =
            JSON.toJSONString(installation, ObjectValueFilter.instance,
                SerializerFeature.WriteClassName, SerializerFeature.DisableCircularReferenceDetect);

        InternalConfigurationController.globalInstance().getInternalPersistence()
            .saveContentToFile(jsonString, installationFile);
      }
    }
  }

  public static AVQuery<AVInstallation> getQuery() {
    AVQuery<AVInstallation> query = new AVQuery<AVInstallation>(INSTALLATION_AVNAME);
    return query;
  }

  /**
   * Add a key-value pair to this object. It is recommended to name keys in
   * partialCamelCaseLikeThis.
   *
   * @param key Keys must be alphanumerical plus underscore, and start with a letter.
   * @param value Values may be numerical, String, JSONObject, JSONArray, JSONObject.NULL, or other
   *        AVObjects. value may not be null.
   */
  @Override
  public void put(String key, Object value) {
    super.put(key, value);
  }

  /**
   * Removes a key from this object's data if it exists.
   *
   * @param key The key to remove.
   */
  @Override
  public void remove(String key) {
    super.remove(key);
  }

  static private String deviceType() {
    return "android";
  }

  @Override
  protected boolean alwaysUsePost() {
    return true;
  }

  @Override
  protected boolean alwaysSaveAllKeyValues() {
    return true;
  }

  protected static void updateCurrentInstallation() {
    try {

      if (AVOSCloud.showInternalDebugLog()) {
        LogUtil.avlog.d("try to update installation to fix date type data");
      }
      AVInstallation currentInstallation = AVInstallation.readInstallationFile();
      if (currentInstallation != null && !AVUtils.isBlankString(currentInstallation.getObjectId())) {
        currentInstallation.fetchInBackground(new GetCallback<AVObject>() {

          @Override
          public void done(AVObject object, AVException e) {
            AVInstallation updatedInstallation = (AVInstallation) object;
            try {
              AVInstallation.writeInstallationFile(updatedInstallation);
            } catch (IOException e1) {
              if (AVOSCloud.showInternalDebugLog()) {
                e1.printStackTrace();
              }
            }
          }
        });
      }
    } catch (Exception e) {
      if (AVOSCloud.showInternalDebugLog()) {
        LogUtil.log.e("failed to update installation", e);
      }
    }
  }

  protected boolean isDirty() {
    return (AVUtils.isBlankString(objectId) || !this.operationQueue.isEmpty()
        || this.getUpdatedAt() == null || (System.currentTimeMillis() - this.getUpdatedAt()
        .getTime()) > 86400000);
  }

  @Override
  protected void rebuildInstanceData() {
    super.rebuildInstanceData();
    this.installationId = this.getString("installationId");
  }

  public static transient final Creator CREATOR = AVObjectCreator.instance;
}
