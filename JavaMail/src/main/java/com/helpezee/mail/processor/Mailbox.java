package com.helpezee.mail.processor;

/*
 * Copyright (C) 2009 JackW
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this library.
 * If not, see .
 */

import java.io.Serializable;

public class Mailbox implements Serializable {
	private static final long serialVersionUID = 826439429623556631L;

	private final String userId;
	private final String userPswd;
	private final String host;

	private int port;
	private String protocol;
	private String folderName;
	private int messagesPerRead;
	private boolean useSsl = true;
	private int maxRetries;
	private int minimumWait; // in seconds
	private boolean isExchange;
	private String proccessedFolderName;

	public Mailbox(String userId, String userPswd, String host, int port, String protocol, String folderName,
			int messagesPerRead, boolean useSsl, int maxRetries, int minimumWait, boolean isExchange,
			String proccessedFolderName) {
		super();
		this.userId = userId;
		this.userPswd = userPswd;
		this.host = host;
		this.port = port;
		this.protocol = protocol;
		this.folderName = folderName;
		this.messagesPerRead = messagesPerRead;
		this.useSsl = useSsl;
		this.maxRetries = maxRetries;
		this.minimumWait = minimumWait;
		this.isExchange = isExchange;
		this.proccessedFolderName = proccessedFolderName;
	}

	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(String folderName) {
		this.folderName = folderName;
	}

	public String getHost() {
		return host;
	}

	public int getMinimumWait() {
		return minimumWait;
	}

	public void setMinimumWait(int minimumWait) {
		this.minimumWait = minimumWait;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public int getMessagesPerRead() {
		return messagesPerRead;
	}

	public void setMessagesPerRead(int readPerPass) {
		this.messagesPerRead = readPerPass;
	}

	public int getMaxRetries() {
		return maxRetries;
	}

	public void setMaxRetries(int retryMax) {
		this.maxRetries = retryMax;
	}

	public String getUserId() {
		return userId;
	}

	public String getUserPswd() {
		return userPswd;
	}

	public boolean isUseSsl() {
		return useSsl;
	}

	public void setUseSsl(boolean useSsl) {
		this.useSsl = useSsl;
	}

	public boolean isExchange() {
		return isExchange;
	}

	public void setExchange(boolean isExchange) {
		this.isExchange = isExchange;
	}

	public String getProccessedFolderName() {
		return proccessedFolderName;
	}

	public void setProccessedFolerName(String proccessedFolderName) {
		this.proccessedFolderName = proccessedFolderName;
	}

}
