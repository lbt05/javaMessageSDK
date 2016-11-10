package cn.leancloud.leancloud_message;

import java.util.HashMap;
import java.util.Map;


class Intent {
  String action;
  Map<String, Object> extras = new HashMap<String, Object>();

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public void putExtra(String key, Object value) {
    extras.put(key, value);
  }

  public Object getExtra(String key) {
    return extras.get(key);
  }

  public Map<String, Object> getExtras() {
    return extras;
  }
}
