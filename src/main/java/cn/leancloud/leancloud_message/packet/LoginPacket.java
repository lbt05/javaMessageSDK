package cn.leancloud.leancloud_message.packet;

public class LoginPacket extends CommandPacket {

  public LoginPacket() {
    this.setCmd("login");
  }

}
