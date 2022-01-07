package com.helpezee.mail.bean;

/**
* A portable Message Header.
*
* @author JackW
*/
public final class MsgHeader implements java.io.Serializable {
private static final long serialVersionUID = -5722833299002367057L;
private String name;
private String value;

public String getName() {
return name;
}
public void setName(String name) {
this.name = name;
}
public String getValue() {
return value;
}
public void setValue(String value) {
this.value = value;
}
}