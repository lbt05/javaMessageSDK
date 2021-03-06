package cn.leancloud.leancloud_message.messages;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.leancloud.leancloud_message.AVIMMessageType;

import com.alibaba.fastjson.JSON;
import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVFile;
import com.avos.avoscloud.AVUtils;
import com.avos.avoscloud.GenericObjectCallback;
import com.avos.avoscloud.GetHttpResponseHandler;
import com.avos.avoscloud.PaasClient;
import com.avos.avoscloud.SaveCallback;
import com.avos.avoscloud.okhttp.Request;

@AVIMMessageType(type = AVIMMessageType.AUDIO_MESSAGE_TYPE)
public class AVIMAudioMessage extends AVIMFileMessage {

  public AVIMAudioMessage() {}

  public AVIMAudioMessage(String localPath) throws IOException {
    super(localPath);
  }

  public AVIMAudioMessage(File localFile) throws IOException {
    super(localFile);
  }

  public AVIMAudioMessage(AVFile file) {
    super(file);
  }

  /**
   * 获取文件的metaData
   *
   * @return
   */
  @Override
  public Map<String, Object> getFileMetaData() {
    if (file == null) {
      file = new HashMap<String, Object>();
    }
    if (file.containsKey(FILE_META)) {
      return (Map<String, Object>) file.get(FILE_META);
    }
    if (localFile != null) {
      Map<String, Object> meta = new HashMap<String, Object>();
      meta.put(FILE_SIZE, actualFile.getSize());
      file.put(FILE_META, meta);
      return meta;
    } else if (actualFile != null) {
      Map<String, Object> meta = actualFile.getMetaData();
      file.put(FILE_META, meta);
      return meta;
    }
    return null;
  }

  /**
   * 获取音频的时长
   *
   * @return
   */
  public double getDuration() {
    Map<String, Object> meta = getFileMetaData();
    if (meta != null && meta.containsKey(DURATION)) {
      return ((Number) meta.get(DURATION)).doubleValue();
    }
    return 0;
  }

  @Override
  protected void getAdditionalMetaData(final Map<String, Object> meta, final SaveCallback callback) {
    if (!AVUtils.isBlankString(actualFile.getUrl()) && localFile == null
        && !isExternalAVFile(actualFile)) {
      PaasClient.AVHttpClient client = AVUtils.getDirectlyClientForUse();
      Request.Builder builder = new Request.Builder();
      builder.url(actualFile.getUrl() + "?avinfo").get();
      client.execute(builder.build(), false, new GetHttpResponseHandler(
          new GenericObjectCallback() {
            @Override
            public void onSuccess(String content, AVException e) {
              try {
                com.alibaba.fastjson.JSONObject response = JSON.parseObject(content);
                com.alibaba.fastjson.JSONObject formatInfo = response.getJSONObject(FORMAT);
                String fileFormat = formatInfo.getString("format_name");
                Double durationInDouble = formatInfo.getDouble("duration");
                long size = formatInfo.getLong(FILE_SIZE);
                meta.put(FILE_SIZE, size);
                meta.put(DURATION, durationInDouble);
                meta.put(FORMAT, fileFormat);
              } catch (Exception e1) {
                callback.internalDone(new AVException(e1));
              }
              callback.internalDone(null);
            }

            @Override
            public void onFailure(Throwable error, String content) {
              callback.internalDone(new AVException(error));
            }
          }));
    } else {
      callback.internalDone(null);
    }
  }
}
