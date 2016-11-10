package cn.leancloud.leancloud_message;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import cn.leancloud.leancloud_message.packet.CommandPacket;

import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVOSCloud;
import com.avos.avoscloud.LogUtil;
import com.avos.avoscloud.internal.InternalConfigurationController;
import com.avos.avoscloud.java_websocket.WebSocket;
import com.avos.avoscloud.java_websocket.client.WebSocketClient;
import com.avos.avoscloud.java_websocket.drafts.Draft_17;
import com.avos.avoscloud.java_websocket.framing.CloseFrame;
import com.avos.avoscloud.java_websocket.framing.Framedata;
import com.avos.avoscloud.java_websocket.framing.FramedataImpl1;
import com.avos.avoscloud.java_websocket.handshake.ServerHandshake;

public class AVIMWebSocketClient extends WebSocketClient {
  private static final String HEADER_SUB_PROTOCOL = "Sec-WebSocket-Protocol";
  private static final int PING_TIMEOUT_CODE = 3000;
  private static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

  long lastLiveTS;
  long lastPingTS;
  long DEFAULT_LIVE_INTERNAL = 30 * 1000;
  long HEALTHY_THRESHOLD = 1 * 60 * 1000;
  long reconnectInterval = 10 * 1000;
  Runnable reconnectTask = new Runnable() {
    @Override
    public void run() {
      reconnect();
    }
  };
  Future healthFuture;
  Runnable healthMonitor = new Runnable() {
    @Override
    public void run() {
      if (!isHealthy() && !AVIMWebSocketClient.this.isClosed()) {
        closeConnection(PING_TIMEOUT_CODE, "No response for ping");
      }
      if (isPingNeeded() && AVIMWebSocketClient.this.isOpen()) {
        ping();
      }
    }
  };

  AVSocketListener listener;

  public AVIMWebSocketClient(URI serverURI, AVSocketListener listener, final String subProtocol,
      boolean secEnabled) {
    super(serverURI, new Draft_17(), new HashMap<String, String>() {
      {
        put(HEADER_SUB_PROTOCOL, subProtocol);
      }
    }, 0);
    if (AVOSCloud.showInternalDebugLog()) {
      LogUtil.avlog.d("trying to connect " + serverURI);
    }
    if (secEnabled) {
      setSocket();
    }
    this.listener = listener;
  }

  private void setSocket() {
    try {
      SSLContext sslContext = SSLContext.getDefault();
      SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
      this.setSocket(sslSocketFactory.createSocket());
    } catch (NoSuchAlgorithmException e) {
      InternalConfigurationController.globalInstance().getInternalLogger()
          .e("Socket Error", "", new AVException(e));
    } catch (IOException e) {
      InternalConfigurationController.globalInstance().getInternalLogger()
          .e("Socket Error", "", new AVException(e));
    }
  }

  @Override
  public void onOpen(ServerHandshake handshakedata) {
    lastLiveTS = System.currentTimeMillis();
    this.cancelReconnect();
    healthFuture =
        executor.scheduleAtFixedRate(healthMonitor, DEFAULT_LIVE_INTERNAL, DEFAULT_LIVE_INTERNAL,
            TimeUnit.MILLISECONDS);
    if (listener != null) {
      listener.loginCmd();
      listener.processConnectionStatus(null);
      listener.processSessionsStatus(false);
    }
  }

  @Override
  public void onMessage(ByteBuffer byteBuffer) {
    lastLiveTS = System.currentTimeMillis();
    if (listener != null) {
      listener.processCommand(byteBuffer);
    }
  }

  public void onMessage(String msg) {
    lastLiveTS = System.currentTimeMillis();
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    if (healthFuture != null) {
      healthFuture.cancel(true);
    }
    if (listener != null) {
      listener.processSessionsStatus(true);
    }
    if (listener != null) {
      listener.processConnectionStatus(new AVException(code, reason));
    }
    LogUtil.avlog.d("local disconnection:" + code + "  " + reason + " :" + remote);
    switch (code) {
      case -1:
        LogUtil.avlog.d("connection refused");
        if (remote) {
          if (listener != null) {
            listener.processRemoteServerNotAvailable();
          }
        } else {
          scheduleReconnect();
        }
        break;
      case CloseFrame.ABNORMAL_CLOSE:
        scheduleReconnect();
        break;
      case PING_TIMEOUT_CODE:
        LogUtil.avlog.d("connection unhealthy");
        reconnect();
        break;
      default:
        scheduleReconnect();
        break;
    }
  }

  @Override
  public void onError(Exception ex) {
    ex.printStackTrace();
    if (listener != null
        && InternalConfigurationController.globalInstance().getAppConfiguration().isConnected()) {
      listener.processRemoteServerNotAvailable();
    }
  }

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  Future reconnectTaskFuture;

  protected void scheduleReconnect() {
    if (reconnectInterval > 0) {
      reconnectTaskFuture =
          scheduler.schedule(reconnectTask, reconnectInterval, TimeUnit.MILLISECONDS);
    }
  }

  protected void cancelReconnect() {
    if (reconnectTaskFuture != null && !reconnectTaskFuture.isDone()) {
      reconnectTaskFuture.cancel(true);
    }
  }

  AtomicBoolean destroyed = new AtomicBoolean(false);

  protected void destroy() {
    destroyed.set(true);
    cancelReconnect();
    if (healthFuture != null) {
      healthFuture.cancel(true);
    }
  }

  protected synchronized void reconnect() {
    if (this.isConnecting() || this.isOpen()) {
      // 已经是健康的状态了就没必要再发了
      return;
    } else if (InternalConfigurationController.globalInstance().getAppConfiguration().isConnected()) {
      this.connect();
    } else if (!destroyed.get()) {
      // 网络状态有问题,我们延期再尝试吧
      scheduleReconnect();
    }
  }

  private boolean isPingNeeded() {
    long currentTS = System.currentTimeMillis();
    return (currentTS - lastLiveTS > DEFAULT_LIVE_INTERNAL);
  }

  protected void ping() {
    lastPingTS = System.currentTimeMillis();
    FramedataImpl1 frame = new FramedataImpl1(Framedata.Opcode.PING);
    frame.setFin(true);
    this.sendFrame(frame);
  }

  protected boolean isHealthy() {
    long currentTS = System.currentTimeMillis();
    if (lastPingTS <= lastLiveTS) {
      // ping请求还没发,暂时认为是健康的
      return true;
    } else {
      return currentTS - lastLiveTS <= HEALTHY_THRESHOLD;
    }
  }

  public void send(CommandPacket packet) {
    if (AVOSCloud.isDebugLogEnabled()) {
      LogUtil.avlog.d("uplink : " + packet.getGenericCommand().toString());
    }
    try {
      send(packet.getGenericCommand().toByteArray());
    } catch (Exception e) {
      LogUtil.avlog.e(e.getMessage());
    }
  }

  @Override
  public void onWebsocketPong(WebSocket conn, Framedata f) {
    super.onWebsocketPong(conn, f);
    lastLiveTS = System.currentTimeMillis();
  }

  public interface AVSocketListener {
    void loginCmd();

    void processCommand(ByteBuffer bytes);

    void processConnectionStatus(AVException e);

    void processRemoteServerNotAvailable();

    void processSessionsStatus(boolean closeEvent);
  }
}
