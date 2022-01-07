package com.helpezee.mail.bean;

/*
 * blog/javaclue/javamail/MessageBean.java
 * 
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
 * If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.mail.Address;

import com.helpezee.mail.bean.MessageNode;
import com.helpezee.mail.bean.MsgHeader;

//import org.apache.log4j.Logger;

/**
 * A portable class that holds properties of an email mine message.
 * 
 * @author JackW
 */
public final class MessageBean extends BodypartBean implements java.io.Serializable {
	private static final long serialVersionUID = -7651754840464120630L;

	// static final Logger logger = Logger.getLogger(MessageBean.class);

	// static final boolean isDebugEnabled = logger.isDebugEnabled();

	private Address[] from, to, cc, bcc, replyto, forward;

	private Address[] toEnvelope;

	private String returnPath;

	private String[] xmailer, priority;

	private String smtpMessageId;

	private String subject;

	private java.util.Date sentDate;

	private int attachCount = 0;

	// to be set by BodypartUtil.retrieveAttachments()
	private MessageNode rfc822, report;

	private List<MessageNode> attachments; // list of MessageNode
	// end

	// to be set by MessageParser.parse()
	private String origRcpt, finalRcpt, dsnAction, dsnStatus;

	private String origSubject, diagnosticCode, rfcMessageId;

	private String dsnRfc822, dsnText, dsnDlvrStat;
	// end

	// properties about the mailbox
	private String mailboxHost, mailboxUser;

	// for incoming only, stores sizes of each attachment.
	private final List<Integer> componentsSize;

	// name used to store message body to the hashMap
	public static final String MSG_BODY_TEXT = "msg_body_text";

	final static String LF = System.getProperty("line.separator", "\n");

	private boolean messageProcessed = false;

	/**
	 * default constructor
	 */
	public MessageBean() {
		componentsSize = new ArrayList<Integer>();
	}

	private String addrToString(Address[] addr) {
		if (addr == null || addr.length == 0)
			return null;

		String str = addr[0].toString();
		for (int i = 1; i < addr.length; i++) {
			str = str + "," + addr[i].toString();
		}
		return str;
	}

	/* getters and setters start from here */
	/**
	 * @return from address as an Address arrayNode
	 */
	public Address[] getFrom() {
		return this.from;
	}

	/**
	 * @return from address as a string
	 */
	public String getFromAsString() {
		return addrToString(this.from);
	}

	/**
	 * @return to address as an Address arrayNode
	 */
	public Address[] getTo() {
		return this.to;
	}

	/**
	 * @return to address as a string
	 */
	public String getToAsString() {
		return addrToString(this.to);
	}

	/**
	 * @return cc address as an Address arrayNode
	 */
	public Address[] getCc() {
		return this.cc;
	}

	/**
	 * @return cc address as a string
	 */
	public String getCcAsString() {
		return addrToString(this.cc);
	}

	/**
	 * @return bcc address as an Address arrayNode
	 */
	public Address[] getBcc() {
		return this.bcc;
	}

	/**
	 * @return bcc address as a string
	 */
	public String getBccAsString() {
		return addrToString(this.bcc);
	}

	/**
	 * @return replyto address as an Address arrayNode
	 */
	public Address[] getReplyto() {
		return this.replyto;
	}

	/**
	 * @return replyto address as a string
	 */
	public String getReplytoAsString() {
		return addrToString(this.replyto);
	}

	/**
	 * @return forward address as an Address arrayNode
	 */
	public Address[] getForward() {
		return this.forward;
	}

	/**
	 * @return forward address as a string
	 */
	public String getForwardAsString() {
		return addrToString(this.forward);
	}

	public String getReturnPath() {
		return returnPath;
	}

	public Address[] getToEnvelope() {
		return this.toEnvelope;
	}

	String getToEnvelopeAsString() {
		return addrToString(this.toEnvelope);
	}

	/**
	 * @return x_mailer as a string arrayNode
	 */
	public String[] getXmailer() {
		return this.xmailer;
	}

	/**
	 * @return x-priority as a string arrayNode
	 */
	public String[] getPriority() {
		return this.priority;
	}

	/**
	 * @return SMTP message id as a string
	 */
	public String getSmtpMessageId() {
		return this.smtpMessageId;
	}

	/**
	 * @return subject as a string
	 */
	public String getSubject() {
		return subject;
	}

	/**
	 * @return send DateField
	 */
	public java.util.Date getSentDate() {
		return sentDate;
	}

	/**
	 * Get Body Content Type
	 * 
	 * @return content type of the body
	 */
	public String getBodyContentType() {
		String type = getBodyContentType(0);
		if (type == null || type.trim().length() == 0) {
			type = DEFAULT_CONTENT_TYPE;
		}
		return type;
	}

	/**
	 * get email message body.
	 * 
	 * @return the email body
	 */
	public String getBody() {
		String msgBody = getBody(0);

		return msgBody;
	}

	/**
	 * get a BodypartBean that holds message body data
	 * 
	 * @return a BodypartBean
	 */
	public BodypartBean getBodyNode() {
		return getBodyNode(0);
	}

	/**
	 * @return the number of attachments.
	 */
	public int getAttachCount() {
		return attachCount;
	}

	/**
	 * @return the mailbox host name
	 */
	public String getMailboxHost() {
		return mailboxHost;
	}

	/**
	 * @return the mailbox user id
	 */
	public String getMailboxUser() {
		return mailboxUser;
	}

	/**
	 * @return rfc822
	 */
	public MessageNode getRfc822() {
		return rfc822;
	}

	/**
	 * @return report
	 */
	public MessageNode getReport() {
		return report;
	}

	/**
	 * @return attachments
	 */
	public List<MessageNode> getAttachments() {
		return attachments;
	}

	public String getRfcMessageId() {
		return rfcMessageId;
	}

	public List<Integer> getComponentsSize() {
		return componentsSize;
	}

	/* all setters start from here */

	/**
	 * set from address from an Address arrayNode
	 * 
	 * @param from
	 */
	public void setFrom(Address[] from) {
		this.from = from;
	}

	/**
	 * set to address from an Address arrayNode
	 * 
	 * @param to
	 */
	public void setTo(Address[] to) {
		this.to = to;
	}

	/**
	 * set cc address from an Address arrayNode
	 * 
	 * @param cc
	 */
	public void setCc(Address[] cc) {
		this.cc = cc;
	}

	/**
	 * set bcc address from an Address arrayNode
	 * 
	 * @param bcc
	 */
	public void setBcc(Address[] bcc) {
		this.bcc = bcc;
	}

	/**
	 * set replyto address from an Address arrayNode
	 * 
	 * @param replyto
	 */
	public void setReplyto(Address[] replyto) {
		this.replyto = replyto;
	}

	/**
	 * set forward address from an Address arrayNode
	 * 
	 * @param forward
	 */
	public void setForward(Address[] forward) {
		this.forward = forward;
	}

	public void setReturnPath(String returnPath) {
		this.returnPath = returnPath;
	}

	/**
	 * set x-mailer from a string arrayNode
	 * 
	 * @param xmailer
	 */
	public void setXmailer(String[] xmailer) {
		this.xmailer = xmailer;
	}

	/**
	 * set x-priority from a string arrayNode
	 * 
	 * @param priority
	 */
	public void setPriority(String[] priority) {
		this.priority = priority;
	}

	/**
	 * set SMTP message id
	 * 
	 * @param smtpMessageId
	 */
	public void setSmtpMessageId(String smtpMessageId) {
		this.smtpMessageId = smtpMessageId;
	}

	/**
	 * set subject from a string
	 * 
	 * @param subject
	 */
	public void setSubject(String subject) {
		this.subject = subject;
	}

	/**
	 * set send DateField
	 * 
	 * @param DateField
	 */
	public void setSentDate(java.util.Date date) {
		this.sentDate = date;
	}

	/**
	 * set mailbox host
	 * 
	 * @param mailboxHost
	 */
	public void setMailboxHost(String mailboxHost) {
		this.mailboxHost = mailboxHost;
	}

	/**
	 * set mailbox user id
	 * 
	 * @param mailboxUser
	 */
	public void setMailboxUser(String mailboxUser) {
		this.mailboxUser = mailboxUser;
	}

	/**
	 * set rfc822
	 * 
	 * @param rfc822
	 */
	public void setRfc822(MessageNode rfc822) {
		this.rfc822 = rfc822;
	}

	/**
	 * set report
	 * 
	 * @param report
	 */
	public void setReport(MessageNode report) {
		this.report = report;
	}

	/**
	 * set attachments
	 * 
	 * @param attachments
	 */
	public void setAttachments(List<MessageNode> attachments) {
		this.attachments = attachments;
	}

	/**
	 * set Message Body, a convenient method, not recommended in production
	 * code.
	 * 
	 * @param body
	 */
	public void setBody(String body) {
		setValue(body);
	}

	public String getDiagnosticCode() {
		return diagnosticCode;
	}

	public void setDiagnosticCode(String diagnosticCode) {
		this.diagnosticCode = diagnosticCode;
	}

	public String getDsnAction() {
		return dsnAction;
	}

	public void setDsnAction(String dsnAction) {
		this.dsnAction = dsnAction;
	}

	public String getDsnDlvrStat() {
		return dsnDlvrStat;
	}

	public void setDsnDlvrStat(String dsnDlvrStat) {
		this.dsnDlvrStat = dsnDlvrStat;
	}

	public String getDsnRfc822() {
		return dsnRfc822;
	}

	public void setDsnRfc822(String dsnRfc822) {
		this.dsnRfc822 = dsnRfc822;
	}

	public String getDsnStatus() {
		return dsnStatus;
	}

	public void setDsnStatus(String dsnStatus) {
		this.dsnStatus = dsnStatus;
	}

	public String getDsnText() {
		return dsnText;
	}

	public void setDsnText(String dsnText) {
		this.dsnText = dsnText;
	}

	public String getFinalRcpt() {
		return finalRcpt;
	}

	public void setFinalRcpt(String finalRcpt) {
		this.finalRcpt = finalRcpt;
	}

	public String getOrigRcpt() {
		return origRcpt;
	}

	public void setOrigRcpt(String origRcpt) {
		this.origRcpt = origRcpt;
	}

	public String getOrigSubject() {
		return origSubject;
	}

	public void setOrigSubject(String origSubject) {
		this.origSubject = origSubject;
	}

	public void setToEnvelope(Address[] toEnvelope) {
		this.toEnvelope = toEnvelope;
	}

	public void setRfcMessageId(String dsnMessageId) {
		this.rfcMessageId = dsnMessageId;
	}

	/**
	 * update the counter of attachments
	 * 
	 * @param attachCount
	 */
	public void updateAttachCount(int attachCount) {
		this.attachCount = this.attachCount + attachCount;
	}

	public boolean isMessageProcessed() {
		return messageProcessed;
	}

	public void setMessageProcessed(boolean messageProcessed) {
		this.messageProcessed = messageProcessed;
	}

	/**
	 * clear up all parameters
	 */
	public void clearParameters() {
		super.clearParameters();
		from = null;
		to = null;
		cc = null;
		bcc = null;
		replyto = null;
		forward = null;
		returnPath = null;
		toEnvelope = null;
		xmailer = null;
		priority = null;
		smtpMessageId = null;
		subject = null;

		sentDate = null;
		synchronized (this) {
			attachCount = 0;
		}
		mailboxHost = null;
		mailboxUser = null;

		rfc822 = null;
		report = null;
		attachments = null;
		componentsSize.clear();

		origRcpt = null;
		finalRcpt = null;
		dsnAction = null;
		dsnStatus = null;
		origSubject = null;
		diagnosticCode = null;
		dsnText = null;
		dsnRfc822 = null;
		dsnDlvrStat = null;
		rfcMessageId = null;
		messageProcessed = false;
	}

	/**
	 * format the message to a string for printing.
	 * 
	 * @return string
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		if (from != null) {
			sb.append("From: " + getFromAsString() + LF);
		}
		if (to != null) {
			sb.append("To: " + getToAsString() + LF);
		}
		if (toEnvelope != null) {
			sb.append("To from Envelope: " + getToEnvelopeAsString() + LF);
		}
		if (cc != null) {
			sb.append("Cc: " + getCcAsString() + LF);
		}
		if (bcc != null) {
			sb.append("Bcc: " + getBccAsString() + LF);
		}
		if (replyto != null) {
			sb.append("Replyto: " + getReplytoAsString() + LF);
		}
		if (forward != null) {
			sb.append("Forward: " + getForwardAsString() + LF);
		}
		if (returnPath != null) {
			sb.append("Return-Path: " + returnPath + LF);
		}
		if (xmailer != null && xmailer.length > 0) {
			for (int i = 0; i < xmailer.length; i++) {
				sb.append("X-Mailer: " + xmailer[i] + LF);
			}
		}
		if (priority != null && priority.length > 0) {
			for (int i = 0; i < priority.length; i++) {
				sb.append("Priority: " + priority[i] + LF);
			}
		}
		if (smtpMessageId != null) {
			sb.append("SMTP Message Id: " + smtpMessageId + LF);
		}
		sb.append("Subject: " + subject + LF);
		if (sentDate != null) {
			sb.append("Sent Date: " + sentDate + LF);
		}
		if (mailboxHost != null) {
			sb.append("MailBox Host: " + mailboxHost + LF);
		}
		if (mailboxUser != null) {
			sb.append("Mailbox User: " + mailboxUser + LF);
		}
		if (finalRcpt != null) {
			sb.append("Final Recipient: " + finalRcpt + LF);
		}
		if (origRcpt != null) {
			sb.append("Original Recipient: " + origRcpt + LF);
		}
		if (dsnAction != null) {
			sb.append("DSN Action: " + dsnAction + LF);
		}
		if (dsnStatus != null) {
			sb.append("DSN Status: " + dsnStatus + LF);
		}
		if (diagnosticCode != null) {
			sb.append("Diagnostic Code: " + diagnosticCode + LF);
		}
		if (origSubject != null) {
			sb.append("Original Subject: " + origSubject + LF);
		}
		if (rfcMessageId != null) {
			sb.append("RFC MessageId: " + rfcMessageId + LF);
		}
		if (dsnText != null) {
			sb.append(LF + "List DSN Text: " + LF + dsnText + LF);
		}
		if (dsnRfc822 != null) {
			sb.append(LF + "List DSN RFC822: " + LF + dsnRfc822 + LF);
		}
		if (dsnDlvrStat != null) {
			sb.append(LF + "List DSN Delivery Status: " + LF + dsnDlvrStat + LF);
		}
		if (headers.size() > 0) {
			sb.append(LF + "List Header Lines:" + LF);
		}
		for (Iterator<MsgHeader> it = headers.iterator(); it.hasNext();) {
			MsgHeader hdr = it.next();
			sb.append("Header Line - " + hdr.getName() + ": " + hdr.getValue() + LF);
		}
		sb.append(LF + "List Body Parts:" + LF);
		sb.append(super.toString(0));
		if (attachments != null && attachments.size() > 0) {
			sb.append(LF + "List Attachments:" + LF);
			for (Iterator<MessageNode> it = attachments.iterator(); it.hasNext();) {
				MessageNode node = it.next();
				BodypartBean anode = node.getBodypartNode();
				sb.append(anode.toString(node.getLevel()));
			}
		}
		return sb.toString();
	}
}