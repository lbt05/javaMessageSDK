package cn.leancloud.leancloud_message;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface AVIMMessageField {
  public String name() default "";
}
