package cn.leancloud.leancloud_message;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface AVIMMessageType {
  public int type();

  static final int TEXT_MESSAGE_TYPE = -1;
  static final int IMAGE_MESSAGE_TYPE = -2;
  static final int AUDIO_MESSAGE_TYPE = -3;
  static final int VIDEO_MESSAGE_TYPE = -4;
  static final int LOCATION_MESSAGE_TYPE = -5;
  static final int FILE_MESSAGE_TYPE = -6;
}
