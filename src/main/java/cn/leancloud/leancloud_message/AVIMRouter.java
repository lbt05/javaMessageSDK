package cn.leancloud.leancloud_message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson.JSON;
import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVInstallation;
import com.avos.avoscloud.AVOSCloud;
import com.avos.avoscloud.AVUtils;
import com.avos.avoscloud.AppRouterManager;
import com.avos.avoscloud.GenericObjectCallback;
import com.avos.avoscloud.GetHttpResponseHandler;
import com.avos.avoscloud.LogUtil;
import com.avos.avoscloud.PaasClient;
import com.avos.avoscloud.internal.InternalConfigurationController;
import com.avos.avoscloud.okhttp.Request;

public class AVIMRouter {
  public static final String SERVER = "server";
  private static final String EXPIRE_AT = "expireAt";
  private static final String SECONDARY = "secondary";

  private static String ROUTER_QUERY_SRTING = "/v1/route?appId=%s&installationId=%s&secure=1";
  private static final String US_ROUTER_SERVICE_FMT = "http://router-a0-push.leancloud.cn"
      + ROUTER_QUERY_SRTING;
  private static final String PUSH_SERVER_CACHE_KEY_FMT = "com.avos.push.router.server.cache%s";
  private static final String PUSH_SERVER_KEYZONE = "com.avos.push.router.keyzone";
  private static boolean isCN = true;
  private final String installationId;
  private int ttlInSecs = -1;

  // router 请求的默认超时时间为 5 秒，否则 15 秒时间太长了
  private static final int ROUTER_REQUEST_TIME_OUT = 5 * 1000;

  private PaasClient.AVHttpClient routerHttpClient;

  private boolean isPrimarySever = true;
  private RouterResponseListener listener;

  /**
   * AVPushRouter is used to get push server refer to appId and installationId.
   *
   * @param context application context of PushService
   */
  public AVIMRouter(RouterResponseListener listener) {
    this.listener = listener;
    this.installationId = AVInstallation.getCurrentInstallation().getInstallationId();
  }

  private String getRouterUrl() {
    String routerUrl = AppRouterManager.getInstance().getRouterServer() + ROUTER_QUERY_SRTING;
    if (!isCN) {
      routerUrl = US_ROUTER_SERVICE_FMT;
    }
    return String.format(routerUrl, InternalConfigurationController.globalInstance()
        .getAppConfiguration().getApplicationId(), installationId);
  }

  private synchronized PaasClient.AVHttpClient getRouterHttpClient() {
    if (null == routerHttpClient) {
      routerHttpClient = new PaasClient.AVHttpClient();
      routerHttpClient.setConnectTimeout(ROUTER_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);
    }
    return routerHttpClient;
  }

  /**
   * fetch push server:<br/>
   * <ul>
   * <li>fetch push server from router.</li>
   * <li>use ttl to judge if it's no need to fetch push server from router and get push server from
   * cache instead.</li>
   * <ul/>
   *
   * @return null or {"groupId":"xxx", "server":"ws://..."}
   */
  public void fetchPushServer() {
    // if (true) {
    // HashMap<String, Object> result = new HashMap<String, Object>();
    // result.put(GROUP_ID, "g0");
    // result.put(SERVER, "wss://puppet.avoscloud.com:5799/");
    // result.put(SECONDARY, "wss://puppet.avoscloud.com:5799/");
    // result.put(EXPIRE_AT, ttlInSecs * 1000l + System.currentTimeMillis());
    // processRouterInformation(code, result);
    // return;
    // }
    Map<String, Object> pushServerCache = getPushServerFromCache();
    // 要是网络暂时是崩溃的,我们就先走走缓存吧,确实没办法了
    if (pushServerCache != null
        && (Long) pushServerCache.get(EXPIRE_AT) > System.currentTimeMillis()
        && (isPrimarySever || !InternalConfigurationController.globalInstance()
            .getAppConfiguration().isConnected())) {
      if (AVOSCloud.showInternalDebugLog()) {
        LogUtil.avlog.d("get push server from cache:" + pushServerCache.get(SERVER));
      }
      if (InternalConfigurationController.globalInstance().getAppConfiguration().isConnected()) {
        isPrimarySever = false;
      }
      listener.onServerAddress((String) pushServerCache.get(SECONDARY));
      return;
    }

    final String routerUrlStr = getRouterUrl();
    if (AVOSCloud.showInternalDebugLog()) {
      LogUtil.avlog.d("try to fetch push server from :" + routerUrlStr);
    }

    if (!AVUtils.isBlankString(InternalConfigurationController.globalInstance()
        .getAppConfiguration().getApplicationId())) {
      final GenericObjectCallback callback = new GenericObjectCallback() {
        @Override
        public void onSuccess(String content, AVException e) {
          if (e == null) {
            try {
              HashMap<String, Object> response = JSON.parseObject(content, HashMap.class);
              ttlInSecs = (Integer) response.get("ttl");

              HashMap<String, Object> result = new HashMap<String, Object>();
              result.put(SERVER, response.get(SERVER));
              result.put(EXPIRE_AT, ttlInSecs * 1000l + System.currentTimeMillis());
              result.put(SECONDARY, response.get(SECONDARY));
              if (response.containsKey("groupUrl")) {
                AppRouterManager.getInstance().updateRouterServer(
                    (String) response.get("groupUrl"), true);
              }
              cachePushServer(result);
              isPrimarySever = true;
              listener.onServerAddress((String) result.get(SERVER));
            } catch (Exception e1) {
              this.onFailure(e1, content);
            }
          }
        }

        @Override
        public void onFailure(Throwable error, String content) {
          if (AVOSCloud.showInternalDebugLog()) {
            LogUtil.avlog.d("failed to fetch push server:" + error);
          }
          listener.onServerAddress(null);
        }
      };
      Request.Builder builder = new Request.Builder();
      builder.url(routerUrlStr).get();
      getRouterHttpClient().execute(builder.build(), false, new GetHttpResponseHandler(callback));
    } else {
      LogUtil.avlog.e("Please initialize Application first");
    }
  }

  private HashMap<String, Object> getPushServerFromCache() {
    HashMap<String, Object> pushServerMap = new HashMap<String, Object>();
    String pushServerData =
        InternalConfigurationController
            .globalInstance()
            .getInternalPersistence()
            .getPersistentSettingString(
                PUSH_SERVER_KEYZONE,
                String.format(PUSH_SERVER_CACHE_KEY_FMT, InternalConfigurationController
                    .globalInstance().getAppConfiguration().getApplicationId()), null);
    if (AVUtils.isBlankString(pushServerData)) {
      Map<String, Object> serverData = JSON.parseObject(pushServerData, Map.class);
      pushServerMap.put(SERVER, serverData.get(SERVER));
      pushServerMap.put(EXPIRE_AT, serverData.get(EXPIRE_AT));
      pushServerMap.put(SECONDARY, serverData.get(SECONDARY));
    }
    return pushServerMap;
  }

  private void cachePushServer(HashMap<String, Object> pushServerMap) {
    InternalConfigurationController
        .globalInstance()
        .getInternalPersistence()
        .savePersistentSettingString(
            PUSH_SERVER_KEYZONE,
            String.format(PUSH_SERVER_CACHE_KEY_FMT, InternalConfigurationController
                .globalInstance().getAppConfiguration().getApplicationId()),
            JSON.toJSONString(pushServerMap));
  }

  public static void useAVOSCloudCN() {
    isCN = true;
  }

  public static void useAVOSCloudUS() {
    isCN = false;
  }

  public interface RouterResponseListener {
    void onServerAddress(String address);
  }
}
