package com.helpezee.mail.bean;

/*
 * blog/javaclue/javamail/MessageBeanUtil.java
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MailDateFormat;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageBeanUtil {

	public static Logger logger = LoggerFactory.getLogger(MessageBeanUtil.class);

	final static String LF = System.getProperty("line.separator", "\n");

	static boolean debugSession = false;
	private static String hostName = null;

	public static final String RETURN_PATH = "Return-Path";
	public static final String XHEADER_PRIORITY = "X-Priority";
	public static final String XHEADER_MAILER = "X-Mailer";
	public static final String MESSAGE_TRUNCATED = "=== message truncated ===";

	private MessageBeanUtil() {
	}

	/**
	 * convert JavaMail MimeMessage to message bean
	 * 
	 * @param p
	 *            - part
	 * @throws MessagingException
	 * @throws IOException
	 */
	public static MessageBean mimeToBean(Part p) throws IOException, MessagingException {
		// make sure it's a message
		if (!(p instanceof Message) && !(p instanceof MimeMessage)) {
			// not a known message type
			try {
				logger.error("mimeToBean() - Unknown Part: " + p.getContentType());
				logger.error("mimeToBean() - ---------------------------");
			} catch (Exception e) {
				logger.error("Exception caught", e);
			}
			throw new MessagingException("Part was not a MimeMessage as expected");
		}

		if (hostName == null) {
			try {
				hostName = java.net.InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				logger.warn("mimeToBean() - UnknownHostException caught, default to localhost", e);
				hostName = "localhost";
			}
		}

		MessageBean msgBean = new MessageBean();
		msgBean.clearParameters();

		processEnvelope((Message) p, msgBean);

		processAttachment((BodypartBean) msgBean, p, msgBean, 0);

		return msgBean;
	}

	/*
	 * process message envelope and headers
	 * 
	 * @param msg - a MimeMessage instance
	 * 
	 * @param msgBean - a MessageBean instance
	 * 
	 * @return - SMTP message id, or null if not found
	 * 
	 * @throws AddressException
	 */
	private static String processEnvelope(Message msg, MessageBean msgBean) throws AddressException {
		Address[] from = null, received_to = null, envelope_to = null, cc = null, bcc = null, replyto = null;
		String[] xmailer = null;
		String subject = null;
		String messageId = null;
		java.util.Date receivedTime = null;

		// Received Date
		try {
			receivedTime = msg.getReceivedDate();
			if (receivedTime != null) {
				msgBean.setSentDate(receivedTime);
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught during getReceivedDate()", e);
		}

		// retrieve Message-Id, Return-Path and Received Time from headers
		try {
			Enumeration<?> enu = ((MimeMessage) msg).getAllHeaders();
			while (enu.hasMoreElements()) {
				Header hdr = (Header) enu.nextElement();
				String name = hdr.getName();
				//logger.debug("processEnvelope() - header line: " + name + ": " + hdr.getValue());
				if ("Message-ID".equalsIgnoreCase(name)) {
					messageId = hdr.getValue();
					//logger.info("processEnvelope() - >>>>>Message-ID retrieved: " + messageId);
					msgBean.setSmtpMessageId(messageId);
				}
				if (RETURN_PATH.equalsIgnoreCase(name)) {
					msgBean.setReturnPath(hdr.getValue());
				}
				if ("Date".equals(name) && receivedTime == null) {
					receivedTime = getHeaderDate(hdr.getValue()); // SMTP Date
				}
			}
		} catch (Exception e) {
			logger.error("Exception caught from getAllHeaderLines()", e);
		}

		Calendar rightNow = Calendar.getInstance();
		// If Received DateTime not found from envelope, use current time
		if (receivedTime == null) {
			msgBean.setSentDate(rightNow.getTime());
		}
		// display Received Date Time
		//logger.info("processEnvelope() - Email Received Time: " (receivedTime != null ? receivedTime.toString() : "UNKNOWN") + ", SERVER-TIME: "+ rightNow.getTime().toString());

		String[] received = null;
		try {
			received = ((MimeMessage) msg).getHeader("Received");
		} catch (MessagingException e) {
			logger.error("MessagingException caught from getHeader(Received)", e);
		}

		// retrieve TO address from "Received" Headers.
		String real_to = "";
		if (received != null) { // sanity check, should never be null
			// scan received headers for "for" address, starting from the bottom
			// (the oldest) and going up until an email address is found.
			int i;
			String tmp_to = null;
			for (i = received.length - 1; i >= 0; i--) {
				//logger.debug("processEnvelope() - Received: " + received[i]);

				if ((tmp_to = analyzeReceived(received[i])) != null) {
					real_to = tmp_to;
					//logger.info("processEnvelope() - found \"for\" in Received Headers: " + real_to);
					break; // exit loop
				}
			}
		}

		Address[] addr;
		// get FROM from envelope or Return-Path
		try {
			String[] _froms = null;
			if ((addr = msg.getFrom()) != null && addr.length > 0) {
				String addrStr = checkAddr(addr[0].toString());
				for (int j = 1; j < addr.length; j++) {
					addrStr += "," + checkAddr(addr[j].toString());
				}
				from = InternetAddress.parse(addrStr);
			} else if ((_froms = msg.getHeader(RETURN_PATH)) != null && _froms.length > 0) {
				logger.warn("processEnvelope() - FROM is missing from envelope, use Return-Path.");
				String addrStr = checkAddr(_froms[0]);
				for (int j = 1; j < _froms.length; j++) {
					addrStr += "," + checkAddr(_froms[j]);
				}
				from = InternetAddress.parse(addrStr);
			} else {
				// FROM is empty from envelope and Return-Path
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught from getFrom()", e);
		}
		msgBean.setFrom(from);
		//logger.debug("processEnvelope() - Email From Address: " + msgBean.getFromAsString());

		// get TO from Received Headers
		if (real_to != null && real_to.trim().length() > 0) {
			// found TO address from header
			try {
				received_to = InternetAddress.parse(real_to);
			} catch (javax.mail.internet.AddressException e) {
				logger.error("Warning!!! AddressException caught from parsing " + real_to, e);
			}
		}

		// get TO from envelope
		try {
			if ((addr = msg.getRecipients(RecipientType.TO)) != null && addr.length > 0) {
				String addrStr = checkAddr(addr[0].toString());
				for (int j = 1; j < addr.length; j++) {
					addrStr += "," + checkAddr(addr[j].toString());
				}
				envelope_to = InternetAddress.parse(addrStr);
			} else {
				// TO is empty from envelope
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught from getRecipients(TO)", e);
		}
		msgBean.setToEnvelope(envelope_to);

		// TO from "Delivered-To" header
		Address[] delivered_to = null;
		try {
			String[] dlvrTo = msg.getHeader("Delivered-To");
			if (dlvrTo != null && dlvrTo.length > 0) {
				String addrStr = checkAddr(dlvrTo[0]);
				for (int j = 1; j < dlvrTo.length; j++) {
					addrStr += "," + checkAddr(dlvrTo[j]);
				}
				//logger.info("processEnvelope() - \"Delivered-To\" found from header: " + addrStr);
				delivered_to = InternetAddress.parse(addrStr);
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught from msg.getHeader(\"Delivered-To\")", e);
		}

		// TO: Received (non-VERP) > Delivered-To > Received (VERP) > Envelope
		if (received_to != null && received_to.length > 0) {
			String dest = received_to[0] == null ? null : received_to[0].toString();
			if (!StringUtil.isEmpty(dest) && !StringUtil.isVERPAddress(dest)) {
				msgBean.setTo(received_to);
			}
		}
		if (msgBean.getTo() == null) {
			if (delivered_to != null && delivered_to.length > 0) {
				// The real mailbox address this email is delivered to. If the
				// email
				// address is a VERP address, the original address is restored
				// from
				// the VERP address and is assigned to "Delivered-To" header.
				msgBean.setTo(delivered_to);
			} else if (received_to != null && received_to.length > 0) {
				// Email address extracted from "Received" header is the real
				// email
				// address. But when VERP is enabled, since the Email Id is
				// embedded
				// in the VERP address, every email received will have its own
				// VERP
				// address. This will cause a disaster to EmailAddr table since
				// all
				// TO addresses are saved to that table.
				String dest = received_to[0] == null ? null : received_to[0].toString();
				if (!StringUtil.isEmpty(dest) && StringUtil.isVERPAddress(dest)) {
					String verpDest = StringUtil.getDestAddrFromVERP(dest);
					try {
						Address[] destAddr = InternetAddress.parse(verpDest);
						msgBean.setTo(destAddr);
					} catch (AddressException e) {
						logger.error("AddressException from Received_To: " + dest);
					}
				}
			}
			if (msgBean.getTo() == null) {
				msgBean.setTo(envelope_to);
			}
		}
		/*logger.info("processEnvelope() - Email To from Delivered-To: " + StringUtil.addrToString(delivered_to, false)
				+ ", from Received Header: " + StringUtil.addrToString(received_to, false) + ", from Envelope: "
				+ StringUtil.addrToString(envelope_to, false));*/

		// CC
		try {
			if ((addr = msg.getRecipients(RecipientType.CC)) != null && addr.length > 0) {
				cc = addr;
				msgBean.setCc(cc);
				logger.debug("processEnvelope() - Email CC Address: " + msgBean.getCcAsString());
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught during getRecipients(CC)", e);
		}

		// BCC
		try {
			if ((addr = msg.getRecipients(RecipientType.BCC)) != null && addr.length > 0) {
				bcc = addr;
				msgBean.setBcc(bcc);
				logger.debug("processEnvelope() - Email BCC Address: " + msgBean.getBccAsString());
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught during getRecipients(BCC)", e);
		}

		// REPLYTO
		try {
			if ((addr = msg.getReplyTo()) != null && addr.length > 0) {
				String addrStr = checkAddr(addr[0].toString());
				for (int j = 1; j < addr.length; j++) {
					addrStr += "," + checkAddr(addr[j].toString());
				}
				replyto = InternetAddress.parse(addrStr);
				msgBean.setReplyto(replyto);
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught during getReplyTo()", e);
		}

		// SUBJECT
		try {
			subject = msg.getSubject();
		} catch (MessagingException e) {
			logger.error("MessagingException caught during getSubject()", e);
		}
		msgBean.setSubject(subject);
		//logger.debug("processEnvelope() - Email Subject: [" + subject + "]");

		// X-MAILER
		try {
			String[] hdrs = msg.getHeader(XHEADER_MAILER);
			if (hdrs != null) {
				xmailer = hdrs;
				msgBean.setXmailer(xmailer);
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught during getHeader(X-Mailer)", e);
		}

		// X-Priority: 1 (High), 2 (Normal), 3 (Low)
		try {
			String[] priority = ((MimeMessage) msg).getHeader(XHEADER_PRIORITY);
			if (priority != null) {
				msgBean.setPriority(priority);
			}
		} catch (MessagingException e) {
			logger.error("MessagingException caught during getHeader(X-Priority)", e);
		}

		return messageId;
	} // end of processEnvelope

	/*
	 * recursively build up an attachment tree structure from a MultiPart
	 * message.
	 * 
	 * @param aNode - current BodypartBean
	 * 
	 * @param p - JavaMail part
	 * 
	 * @param msgBean - root message bean
	 * 
	 * @param level - attachment level
	 */
	private static void processAttachment(BodypartBean aNode, Part p, MessageBean msgBean, int level) {
		String disp = null, desc = null, contentType = "text/plain";
		String dispOrig = null, descOrig = null;
		String fileName = null;
		// initialize part size
		int partSize = 0;
		// get content type
		try {
			contentType = p.getContentType();
		} catch (Exception e) {
			contentType = "text/plain"; // failed to get content type, use
										// default
			logger.error("Exception caught during getContentType()", e);
		}
		// get disposition
		try {
			dispOrig = p.getDisposition();
			/*
			 * disposition may look like: 1) inline 2) attachment 3)
			 * attachment|inline; filename=xxxxx I believe JavaMail is taking
			 * care of this. However the code stays here just for safety.
			 */
			if (dispOrig != null && dispOrig.indexOf(";") > 0) {
				disp = dispOrig.substring(0, dispOrig.indexOf(";"));
			} else {
				disp = dispOrig;
			}
		} catch (Exception e) {
			logger.error("Exception caught during getDisposition()", e);
		}
		// get description
		try {
			descOrig = p.getDescription();
			// to make use of "desc" field by saving attachment file name on it.
			if (descOrig == null) {
				// if null, get attachment filename from content type field
				desc = getFileName(contentType);
				// JavaMail appends file name to content type field if one is
				// found from disposition field
			} else {
				desc = descOrig;
			}
		} catch (Exception e) {
			logger.error("Exception caught during getDescription()", e);
		}
		// get file name
		try {
			fileName = p.getFileName();
		} catch (Exception e) {
			logger.error("Exception caught during getFileName()", e);
		}
		// get part size
		try {
			partSize = p.getSize();
		} catch (Exception e) {
			logger.error("Exception caught during getSize()", e);
		}
		// display some key information
		// if (isDebugEnabled) {
		// System.out.println("processAttachment() - getDisposition(): " +
		// dispOrig);
		// System.out.println("processAttachment() - getDescription(): " +
		// descOrig);
		// System.out.println("processAttachment() - getContentType(): " +
		// contentType + ", level:" + level + ", size:" + partSize);
		// }
		if (fileName != null) {
			System.out.println("processAttachment() - getFileName() = " + fileName);
		}
		// build mime tree
		try {
			aNode.setDisposition(disp);
			aNode.setDescription(desc);
			aNode.setFileName(fileName);
			// update attachment count
			if (Part.ATTACHMENT.equalsIgnoreCase(disp) || (Part.INLINE.equalsIgnoreCase(disp) && desc != null)
					|| getFileName(contentType) != null) {
				// update attachment count
				msgBean.updateAttachCount(1);
			}
			// set content type and header fields
			aNode.setContentType(contentType);
			aNode.setHeaders(p);
			aNode.setSize(partSize);
			/*
			 * Using isMimeType to determine the content type.
			 */
			if (p.isMimeType("text/plain") || p.isMimeType("text/html")) {
				// logger.info("processAttachment(): level " + level + ", text
				// message: " + contentType);
				aNode.setValue((String) p.getContent());
				msgBean.getComponentsSize().add(Integer.valueOf(aNode.getSize()));
			} else if (p.isMimeType("multipart/*")) {
				// System.out.println("processAttachment(): level " + level + ",
				// Recursive Multipart: " + contentType);
				Multipart mp = (Multipart) p.getContent();
				int count = mp.getCount();
				for (int i = 0; i < count; i++) {
					Part p1 = mp.getBodyPart(i);
					// call itself to build up a child attachment tree
					if (p1 != null) {
						BodypartBean subNode = new BodypartBean();
						processAttachment(subNode, p1, msgBean, level + 1);
						aNode.put(subNode);
					}
				}
			} else if (p.isMimeType("message/rfc822")) {
				// nested message type
				// logger.info("processAttachment(): level " + level + ", RFC822
				// Message: " + contentType);
				Part p1 = (Part) p.getContent();
				if (p1 != null) {
					BodypartBean subNode = new BodypartBean();
					processAttachment(subNode, p1, msgBean, level + 1);
					aNode.put(subNode);
				}
			} else {
				/*
				 * other mime type. could be application, image, audio, video,
				 * message, etc.
				 */
				Object o = p.getContent();
				/*
				 * unknown mine type section. check its java type.
				 */
				if (o instanceof String) {
					// text type of section
					// logger.info("processAttachment(): level " + level + ",
					// String Content " + contentType);
					aNode.setValue((String) o);
					if (aNode.getValue() != null) {
						msgBean.getComponentsSize().add(Integer.valueOf(((byte[]) aNode.getValue()).length));
					}
				} else if (o instanceof InputStream) {
					// stream type of section
					// logger.info("processAttachment(): level " + level + ",
					// InputStream Content " + contentType);
					InputStream is = (InputStream) o;
					aNode.setValue((InputStream) is);
					if (aNode.getValue() != null) {
						msgBean.getComponentsSize().add(Integer.valueOf(((byte[]) aNode.getValue()).length));
					}
				} else if (o != null) {
					// unknown Java type, write it out as a string anyway.
					// logger.error("processAttachment(): level " + level + ",
					// Unknown type: " + o.toString());
					aNode.setValue((String) o.toString());
					if (aNode.getValue() != null) {
						msgBean.getComponentsSize().add(Integer.valueOf(((byte[]) aNode.getValue()).length));
					}
				} else {
					// no content
					// logger.error("processAttachment(): level " + level + ",
					// Content is null");
					aNode.setValue((Object) null);
				}
			}
		} // end of the try block
		catch (IndexOutOfBoundsException e) {
			/* thrown from mp.getBodyPart(i), should never happen */
			// logger.error("processAttachment(): IndexOutOfBoundsException
			// caught: " + contentType);
			// logger.error("IndexOutOfBoundsException caught", e);
			aNode.setValue("001: IndexOutOfBoundsException caught during process.");
			BodypartBean subNode = new BodypartBean("text/plain");
			aNode.put(subNode);
			setAnodeValue(subNode, p, "002: IndexOutOfBoundsException thrown from mp.getBodyPart(i).");
			if (subNode.getValue() != null) {
				msgBean.getComponentsSize().add(Integer.valueOf(((byte[]) subNode.getValue()).length));
			}
			subNode.setDisposition(aNode.getDisposition());
			subNode.setDescription(aNode.getDescription());
		} catch (MessagingException e) {
			/*
			 * JavaMail failed to read the message body, use its raw data
			 * instead
			 */
			logger.error("processAttachment(): MessagingException caught: " + contentType);
			logger.error("MessagingException caught", e);
			if (contentType.trim().toLowerCase().startsWith("multipart/")
					|| contentType.trim().toLowerCase().startsWith("message/rfc822")) {
				aNode.setValue("003: MessagingException caught during process.");
				BodypartBean subNode = new BodypartBean("text/plain");
				aNode.put(subNode);
				setAnodeValue(subNode, p);
				if (subNode.getValue() != null) {
					msgBean.getComponentsSize().add(Integer.valueOf(((byte[]) subNode.getValue()).length));
				}
				subNode.setDisposition(aNode.getDisposition());
				subNode.setDescription(aNode.getDescription());
			} else {
				setAnodeValue(aNode, p);
				if (aNode.getValue() != null) {
					msgBean.getComponentsSize().add(Integer.valueOf(((byte[]) aNode.getValue()).length));
				}
			}
		} catch (UnsupportedEncodingException e) {
			/* unsupported encoding found, use its raw data instead */
			logger.error("processAttachment(): UnsupportedEncodingException caught: " + contentType);
			logger.error("UnsupportedEncodingException caught", e);
			if (contentType.trim().toLowerCase().startsWith("multipart/")
					|| contentType.trim().toLowerCase().startsWith("message/rfc822")) {
				aNode.setValue("004: UnsupportedEncodingException caught during process.");
				BodypartBean subNode = new BodypartBean("text/plain");
				aNode.put(subNode);
				setAnodeValue(subNode, p);
				if (subNode.getValue() != null) {
					msgBean.getComponentsSize().add(Integer.valueOf(((byte[]) subNode.getValue()).length));
				}
				subNode.setDisposition(aNode.getDisposition());
				subNode.setDescription(aNode.getDescription());
			} else {
				setAnodeValue(aNode, p);
				if (aNode.getValue() != null) {
					msgBean.getComponentsSize().add(Integer.valueOf(((byte[]) aNode.getValue()).length));
				}
			}
		} catch (IOException e) {
			/*
			 * IOException caught during decoding, couldn't read the message
			 * body. Use "-- Message body has been omitted --" as body text
			 */
			logger.error("processAttachment(): IOException caught: " + contentType);
			logger.error("IOException caught", e);
			if (contentType.trim().toLowerCase().startsWith("multipart/")
					|| contentType.trim().toLowerCase().startsWith("message/rfc822")) {
				aNode.setValue("005: IOException caught during process.");
				BodypartBean subNode = new BodypartBean("text/plain");
				aNode.put(subNode);
				subNode.setValue("-- Message body has been omitted --");
				subNode.setDisposition(aNode.getDisposition());
				subNode.setDescription(aNode.getDescription());
			} else {
				aNode.setValue("-- Message body has been omitted --");
			}
		} catch (Exception e) {
			/* all other unchecked exceptions */
			logger.error("processAttachment(): Exception caught: " + contentType);
			logger.error("Exception caught", e);
			if (contentType.trim().toLowerCase().startsWith("multipart/")
					|| contentType.trim().toLowerCase().startsWith("message/rfc822")) {
				aNode.setValue("006: Exception caught during process.");
				BodypartBean subNode = new BodypartBean("text/plain");
				aNode.put(subNode);
				setAnodeValue(subNode, p, "Unchecked Exception caught: " + e.toString());
				subNode.setDisposition(aNode.getDisposition());
				subNode.setDescription(aNode.getDescription());
			} else {
				setAnodeValue(aNode, p, "Unchecked Exception caught: " + e.toString());
			}
		}
	} // end of processAttachment

	private static void setAnodeValue(BodypartBean anode, Part p) {
		setAnodeValue(anode, p, "-- Message body has been omitted. Exception thrown from p.getInputStream() --");
	}

	private static void setAnodeValue(BodypartBean anode, Part p, String errmsg) {
		try {
			anode.setValue((InputStream) p.getInputStream());
		} catch (Exception e) {
			anode.setValue(errmsg);
		}
	}

	final static MailDateFormat mailDateFormat = new MailDateFormat();

	private static java.util.Date getHeaderDate(String text) {
		if (StringUtil.isEmpty(text))
			return null;
		try {
			java.util.Date date = mailDateFormat.parse(text);
			return date;
		} catch (ParseException e) {
			logger.warn("getHeaderDate() - ParseException caught parsing: " + text);
		}
		return null;
	}

	/*
	 * check and reformat email address
	 * 
	 * @param s - email address
	 * 
	 * @return reformatted address
	 */
	private static String checkAddr(String s) {
		// do not append domain name by default
		return checkAddr(s, false);
	}

	/*
	 * check and reformat email address
	 * 
	 * @param s - email address
	 * 
	 * @param needDomain - true requires domain
	 * 
	 * @return reformatted address
	 */
	private static String checkAddr(String s, boolean needDomain) {
		if (s == null || s.trim().length() == 0) {
			return s;
		}
		try {
			InternetAddress.parse(s);
		} catch (javax.mail.internet.AddressException e) {
			logger.error("AddressException caught during parsing", e);
			return null;
		}

		String addr = s;
		// is this a name only address?
		if (needDomain && addr.indexOf("@") < 0) {
			int pos;
			if ((pos = addr.indexOf(">")) < 0) {
				// does it look like <user>? no - append default domain name
				addr += "@" + hostName;
			} else {
				addr = addr.substring(0, pos) + "@" + hostName + ">";
			}
		}
		return addr;
	}

	/*
	 * analyze "Received" header and retrieve address from the header
	 * 
	 * @param received - header
	 * 
	 * @return "for" address if found, null otherwise.
	 */
	private static String analyzeReceived(String received) {
		int semicolon_pos = -1;
		if (received != null && (semicolon_pos = received.indexOf(";")) > 0) {
			received = received.substring(0, semicolon_pos);
			received = received.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');

			// required fields
			int from_pos = received.indexOf("from ");
			int by_pos = received.indexOf(" by ", from_pos + 1);
			int low_pos = Math.min(from_pos, by_pos);
			int high_pos = Math.max(from_pos, by_pos);
			int max_pos = high_pos;

			// check optional fields
			int via_pos = received.indexOf(" via ", max_pos + 1);
			max_pos = Math.max(max_pos, via_pos);
			int with_pos = received.indexOf(" with ", max_pos + 1);
			max_pos = Math.max(max_pos, with_pos);
			int id_pos = received.indexOf(" id ", max_pos + 1);
			max_pos = Math.max(max_pos, id_pos);

			int for_pos = received.indexOf(" for ", max_pos + 1);
			if (low_pos >= 0 && for_pos > high_pos) {
				return received.substring(for_pos + 4);
			} else if (by_pos >= 0 && with_pos > by_pos && for_pos > with_pos) {
				// AOL or Google - "received" could only contain "by" and
				// "with", but no "from"
				return received.substring(for_pos + 4);
			} else if (low_pos >= 0 && max_pos > high_pos) {
				// found optional field
				// address may have a display name and the display name may
				// contain one of the search keys used by the optional fields
				// (indexOf())
				for_pos = received.lastIndexOf(" for ");
				if (for_pos > high_pos) {
					return received.substring(for_pos + 4);
				}
			}
		}

		return null;
	}

	/*
	 * locate the file name from content-type.
	 * 
	 * @param ctype - content type
	 * 
	 * @return file name extracted from the content type
	 */
	static String getFileName(String ctype) {
		String desc = null;
		if (ctype != null && ctype.indexOf("name=") >= 0) {
			desc = ctype.substring(ctype.indexOf("name=") + 5);
			if (desc != null && desc.indexOf(";") > 0)
				desc = desc.substring(0, desc.indexOf(";"));
		}
		return desc;
	}

	/**
	 * convert MessageBean to JavaMail MimeMessage
	 * 
	 * @param msgBean
	 *            - a MessageBean object
	 * @return JavaMail Message
	 * @throws MessagingException
	 * @throws IOException
	 */
	public static Message beanToMime(MessageBean msgBean) throws MessagingException, IOException {
		javax.mail.Session session = Session.getDefaultInstance(System.getProperties());
		if (debugSession)
			session.setDebug(true);
		Message msg = new MimeMessage(session);

		// First Set All Headers from a header List
		List<MsgHeader> headers = msgBean.getHeaders();
		if (headers != null) {
			for (int i = 0; i < headers.size(); i++) {
				MsgHeader header = headers.get(i);
				if (!getReservedHeaders().contains(header.getName())) {
					msg.setHeader(header.getName(), header.getValue());
				}
				logger.debug("beanToMime() - Header Line - " + header.getName() + ": " + header.getValue());

			}
		}

		// override certain headers with the data from MesssageBean
		if (msgBean.getFrom() != null) {
			for (int i = 0; i < msgBean.getFrom().length; i++) {
				// just for safety
				if (msgBean.getFrom()[i] != null) {
					msg.setFrom(msgBean.getFrom()[i]);
					break;
				}
			}
		} else {
			logger.warn("beanToMime() - MessageBean.getFrom() returns a null");
			msg.setFrom();
		}
		if (msgBean.getTo() != null) {
			msg.setRecipients(Message.RecipientType.TO, msgBean.getTo());
		} else {
			logger.warn("beanToMime() - MessageBean.getTo() returns a null");
		}
		if (msgBean.getCc() != null) {
			msg.setRecipients(Message.RecipientType.CC, msgBean.getCc());
		}
		if (msgBean.getBcc() != null) {
			msg.setRecipients(Message.RecipientType.BCC, msgBean.getBcc());
		}
		if (msgBean.getReplyto() != null) {
			msg.setReplyTo(msgBean.getReplyto());
		}

		if (msgBean.getReturnPath() != null && msgBean.getReturnPath().trim().length() > 0) {
			msg.setHeader(RETURN_PATH, msgBean.getReturnPath());
		}
		msg.setHeader(XHEADER_PRIORITY, getMsgPriority(msgBean.getPriority()));
		if (msgBean.getXmailer() != null && msgBean.getXmailer().length > 0) {
			msg.setHeader(XHEADER_MAILER, msgBean.getXmailer()[0]);
		}
		msg.setSentDate(new Date());

		msg.setSubject(msgBean.getSubject() == null ? "" : msgBean.getSubject());

		// construct message body part
		List<BodypartBean> aNodes = msgBean.getNodes();
		if (msgBean.getMimeType().startsWith("multipart")) {
			Multipart mp = new MimeMultipart(msgBean.getMimeSubType());
			msg.setContent(mp);
			constructMultiPart(mp, (BodypartBean) msgBean, 0);
		} else if (aNodes != null && aNodes.size() > 0) {
			Multipart mp = new MimeMultipart("mixed"); // make up a default
			msg.setContent(mp);
			if (msgBean.getValue() != null) {
				BodyPart bp = new MimeBodyPart();
				mp.addBodyPart(bp);
				constructSinglePart(bp, (BodypartBean) msgBean, 0);
			}
			constructMultiPart(mp, (BodypartBean) msgBean, 0);
		} else {
			constructSinglePart(msg, (BodypartBean) msgBean, 0);
		}
		msg.saveChanges(); // please remember to save the message

		return msg;
	}

	/**
	 * create MessageBean from SMTP raw stream
	 * 
	 * @param mailStream
	 * @return a MessageBean
	 * @throws MessagingException
	 */
	public static MessageBean createMessageBeanFromStream(byte[] mailStream) throws MessagingException {
		Message msg = createMimeMessageFromStream(mailStream);
		try {
			MessageBean msgBean = mimeToBean(msg);
			return msgBean;
		} catch (IOException e) {
			logger.error("IOException caught", e);
			throw new MessagingException(e.toString());
		}
	}

	/**
	 * create JavaMail Message from SMTP raw stream
	 * 
	 * @param mailStream
	 * @return a JavaMail Message
	 * @throws MessagingException
	 */
	public static Message createMimeMessageFromStream(byte[] mailStream) throws MessagingException {
		javax.mail.Session session = Session.getDefaultInstance(System.getProperties());
		session.setDebug(true);
		ByteArrayInputStream bais = new ByteArrayInputStream(mailStream);
		Message msg = new MimeMessage(session, bais);
		msg.saveChanges();
		session.setDebug(debugSession);
		return msg;
	}

	private static void constructMultiPart(Multipart mp, BodypartBean aNode, int level)
			throws MessagingException, IOException {

		logger.debug("constructMultiPart() - MultipartHL - " + StringUtil.getPeriods(level) + "Content Type: "
				+ mp.getContentType());

		List<BodypartBean> aNodes = aNode.getNodes();
		for (int i = 0; aNodes != null && i < aNodes.size(); i++) {
			BodypartBean subNode = aNodes.get(i);
			if (subNode.getMimeType().startsWith("multipart")) {
				Multipart subMp = new MimeMultipart(subNode.getMimeSubType());
				BodyPart multiBody = new MimeBodyPart();
				multiBody.setContent(subMp);
				mp.addBodyPart(multiBody);
				constructMultiPart(subMp, subNode, level + 1);
			} else {
				BodyPart bodyPart = new MimeBodyPart();
				mp.addBodyPart(bodyPart);
				constructSinglePart(bodyPart, subNode, level + 1);
			}
		}
	}

	private static final Set<String> reservedHeaders = new HashSet<String>();

	private static Set<String> getReservedHeaders() {
		if (reservedHeaders.isEmpty()) {
			reservedHeaders.add("Delivered-To");
			reservedHeaders.add("Received");
			reservedHeaders.add("Message-ID");
			reservedHeaders.add("Subject");
			reservedHeaders.add("Return-Path");
			// reservedHeaders.add("User-Agent");
		}
		return reservedHeaders;
	}

	private static void constructSinglePart(Part part, BodypartBean aNode, int level)
			throws MessagingException, IOException {
		// Set All Headers
		List<MsgHeader> headers = aNode.getHeaders();
		if (headers != null) {
			for (int i = 0; i < headers.size(); i++) {
				MsgHeader header = headers.get(i);
				if (!getReservedHeaders().contains(header.getName())) {
					part.setHeader(header.getName(), header.getValue());
				}

				logger.debug("constructSinglePart() - Header Line - " + StringUtil.getPeriods(level) + header.getName()
						+ ": " + header.getValue());

			}
		}

		part.setDisposition(aNode.getDisposition());
		part.setDescription(aNode.getDescription());

		if (aNode.getMimeType().startsWith("text")) {
			part.setContent(new String(aNode.getValue()), aNode.getContentType());
			if (aNode.getMimeType().startsWith("text/html")) {
				if (aNode.getDisposition() == null) {
					// part.setDisposition(Part.INLINE);
					/*
					 * Do not uncomment above line, as some SMTP server will
					 * insert "-----Inline Attachment Follows-----" at the
					 * beginning of the message.
					 */
				}
			}
		} else {
			if (aNode.getDescription() == null) {
				// not sure why do this, consistency?
				part.setDescription(getFileName(aNode.getContentType()));
			}
			ByteArrayDataSource bads = new ByteArrayDataSource(aNode.getValue(), aNode.getContentType());
			part.setDataHandler(new DataHandler(bads));
		}
	}

	private static String getMsgPriority(String[] priority) {
		String outPriority = "2 (Normal)";
		if (priority != null && priority[0] != null) {
			String in_p = priority[0].trim();
			if (in_p.equalsIgnoreCase("HIGH"))
				outPriority = "1 (High)";
			else if (in_p.equalsIgnoreCase("NORM"))
				outPriority = "2 (Normal)";
			else if (in_p.equalsIgnoreCase("LOW"))
				outPriority = "3 (Low)";
		}
		return (outPriority);
	}

	private static List<String> getMessageBeanMethodNames() {
		Method methods[] = MessageBean.class.getMethods();
		List<String> methodNameList = new ArrayList<String>();

		for (int i = 0; i < methods.length; i++) {
			Method method = (Method) methods[i];
			Class<?> parmTypes[] = method.getParameterTypes();
			int mod = method.getModifiers();
			if (Modifier.isPublic(mod) && !Modifier.isAbstract(mod) && !Modifier.isStatic(mod)) {
				if (method.getName().length() > 3 && method.getName().startsWith("get") && parmTypes.length == 0) {
					String name = method.getName().substring(3);

					if (method.getReturnType().getName().equals("java.lang.String")
							|| method.getReturnType().getName().equals("java.lang.Long")) {

						// ignore following methods
						if ("BodyContentType".equals(name))
							continue;
						if (name.startsWith("Dsn"))
							continue;
						if (name.startsWith("Des"))
							continue;
						if (name.startsWith("Dia"))
							continue;
						if (name.startsWith("Dis"))
							continue;
						if (name.endsWith("AsString"))
							continue;
						// end of ignore

						methodNameList.add(name);
					} else if (method.getReturnType().getCanonicalName().equals("javax.mail.Address[]")) {
						methodNameList.add(name);
					}
				}
			}
		}
		Collections.sort(methodNameList);
		return methodNameList;
	}

	private static String invokeMethod(MessageBean msgBean, String name) {
		if (msgBean == null || name == null) {
			logger.warn("invokeMethod() - Either msgBean or name is null.");
			return null;
		}

		if (name.startsWith("Email_")) {
			// strip off prefix
			name = name.substring(6);
		}
		name = "get" + name;

		try {

			logger.debug("invoking method: " + name + "()");
			Method method = msgBean.getClass().getMethod(name, (Class[]) null);
			Object obj = method.invoke(msgBean, (Object[]) null);
			if (obj instanceof String) {
				return (String) obj;
			} else if (obj instanceof Long) {
				return ((Long) obj).toString();
			} else if (obj instanceof Address[]) {
				return StringUtil.addrToString((Address[]) obj);
			} else if (obj != null) {
				logger.warn("invokeMethod() - invalid return type: " + obj.getClass().getName());
			}
		} catch (Exception e) {
			logger.error("invokeMethod() - Exception caught", e);
		}
		return null;
	}

	public static void main(String[] args) {
		List<String> methodNameList = getMessageBeanMethodNames();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < methodNameList.size(); i++) {
			sb.append(methodNameList.get(i) + LF);
		}
		System.out.println(sb.toString());

		MessageBean msgBean = new MessageBean();
		msgBean.setSubject("test subject");
		msgBean.setBody("test body text");
		System.out.println("Invoke getBody(): " + invokeMethod(msgBean, "Body"));
		System.out.println("Invoke getSubject(): " + invokeMethod(msgBean, "Subject"));
		try {
			Message msg = beanToMime(msgBean);
			MessageBean bean = mimeToBean(msg);
			System.out.println("########## MessageBean Before:");
			System.out.println(msgBean);
			System.out.println("########## MessageBean After:");
			System.out.println(bean);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}