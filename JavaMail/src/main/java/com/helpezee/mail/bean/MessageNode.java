package com.helpezee.mail.bean;

import java.io.Serializable;

import com.helpezee.mail.bean.BodypartBean;
/**
* define an extended BodypartBean class to store additional data
*/
public final class MessageNode implements Serializable {
private static final long serialVersionUID = 750866110886999439L;

BodypartBean aNode = null;

int level = 0;

public MessageNode(BodypartBean aNode, int level) {
this.aNode = aNode;
this.level = level;
}

public BodypartBean getBodypartNode() {
return aNode;
}

public int getLevel() {
return level;
}
}	