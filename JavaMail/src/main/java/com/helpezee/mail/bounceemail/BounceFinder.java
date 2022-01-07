package com.helpezee.mail.bounceemail;

/*
 * blog/javaclue/javamail/BounceFinder.java
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.StringTokenizer;

import javax.mail.Address;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpezee.mail.bean.BodypartBean;
import com.helpezee.mail.bean.BodypartUtil;
import com.helpezee.mail.bean.MessageBean;
import com.helpezee.mail.bean.MessageNode;
import com.helpezee.mail.bean.MsgHeader;
import com.helpezee.mail.bean.SmtpScanner;
import com.helpezee.mail.bean.SmtpScanner.BOUNCE_TYPES;
import com.helpezee.mail.bean.StringUtil;

/**
 * Scan email header and body, and match rules to determine the bounce type.
 * 
 * @author jackw
 */
public final class BounceFinder {

	public static Logger logger = LoggerFactory.getLogger(BounceFinder.class);

	private final SmtpScanner rfcScan;

	static final String TEN_DASHES = "----------";
	static final String ORIGMSG_SEPARATOR = "-----Original Message-----";
	static final String REPLY_SEPARATOR = "---------Reply Separator---------";
	static final String LF = System.getProperty("line.separator", "\n");

	public final static String VERP_BOUNCE_ADDR_XHEADER = "X-VERP_Bounce_Addr";

	/**
	 * default constructor
	 */
	public BounceFinder() throws IOException {
		rfcScan = SmtpScanner.getInstance();
	}

	/**
	 * Scans email properties to find out the bounce type. It also checks VERP
	 * headers to get original recipient.
	 * 
	 * @param msgBean
	 *            a MessageBean instance
	 */
	public String parse(MessageBean msgBean) {
		//logger.debug("Entering parse() method...");
		String bounceType = null;

		// retrieve attachments into an array, it also gathers rfc822/Delivery
		// Status.
		BodypartUtil.retrieveAttachments(msgBean);

		// scan message for Enhanced Mail System Status Code (rfc1893/rfc3464)
		BodypartBean aNode = null;
		if (msgBean.getReport() != null) {
			/*
			 * multipart/report mime type is present, retrieve DSN/MDN report.
			 */
			MessageNode mNode = msgBean.getReport();
			// locate message/delivery-status section
			aNode = BodypartUtil.retrieveDlvrStatus(mNode.getBodypartNode(), mNode.getLevel());
			if (aNode != null) {
				// first scan message/delivery-status
				byte[] attchValue = (byte[]) aNode.getValue();
				if (attchValue != null) {
				//	logger.debug("parse() - scan message/report status -----<" + LF + new String(attchValue) + ">-----");

					if (bounceType == null) {
						bounceType = rfcScan.scanBody(new String(attchValue));
					}
					parseDsn(attchValue, msgBean);
					msgBean.setDsnDlvrStat(new String(attchValue));
				}
			} else if ((aNode = BodypartUtil.retrieveMDNReceipt(mNode.getBodypartNode(), mNode.getLevel())) != null) {
				// got message/disposition-notification
				byte[] attchValue = (byte[]) aNode.getValue();
				if (attchValue != null) {
					//logger.debug(	"parse() - display message/report status -----<" + LF + new String(attchValue) + ">-----");

					if (bounceType == null) {
						bounceType = BOUNCE_TYPES.MDN_RECEIPT.toString();
					}
					// MDN comes with original and final recipients
					parseDsn(attchValue, msgBean);
					msgBean.setDsnDlvrStat(new String(attchValue));
				}
			} else {
				// missing message/* section, try text/plain
				List<BodypartBean> nodes = BodypartUtil.retrieveReportText(mNode.getBodypartNode(), mNode.getLevel());
				if (!nodes.isEmpty()) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					for (BodypartBean bodyPart : nodes) {
						byte[] attchValue = (byte[]) bodyPart.getValue();
						try {
							baos.write(attchValue);
						} catch (IOException e) {
							logger.error("IOException caught", e);
						}
					}
					try {
						baos.close();
					} catch (IOException e) {
					}
					byte[] attchValue = baos.toByteArray();
					if (attchValue != null) {
					//	logger.debug("parse() - scan message/report text -----<" + LF + new String(attchValue) + ">-----");
						if (bounceType == null) {
							bounceType = rfcScan.scanBody(new String(attchValue));
						}
						parseDsn(attchValue, msgBean);
						msgBean.setDsnText(new String(attchValue));
					}
				}
			}
			// locate possible message/rfc822 section under multipart/report
			aNode = BodypartUtil.retrieveMessageRfc822(mNode.getBodypartNode(), mNode.getLevel());
			if (aNode != null && msgBean.getRfc822() == null) {
				msgBean.setRfc822(new MessageNode(aNode, mNode.getLevel()));
			}
			// locate possible text/rfc822-headers section under
			// multipart/report
			aNode = BodypartUtil.retrieveRfc822Headers(mNode.getBodypartNode(), mNode.getLevel());
			if (aNode != null && msgBean.getRfc822() == null) {
				msgBean.setRfc822(new MessageNode(aNode, mNode.getLevel()));
			}
		}

		if (msgBean.getRfc822() != null) {
			/*
			 * message/rfc822 is present, retrieve RFC report.
			 */
			MessageNode mNode = msgBean.getRfc822();
			aNode = BodypartUtil.retrieveRfc822Text(mNode.getBodypartNode(), mNode.getLevel());
			if (aNode != null) {
				StringBuffer sb = new StringBuffer();
				// get original message headers
				List<MsgHeader> vheader = aNode.getHeaders();
				for (int i = 0; vheader != null && i < vheader.size(); i++) {
					MsgHeader header = vheader.get(i);
					sb.append(header.getName() + ": " + header.getValue() + LF);
				}
				boolean foundAll = false;
				String rfcHeaders = sb.toString();
				if (!StringUtil.isEmpty(rfcHeaders)) {
					// rfc822 headers
					//logger.debug("parse() - scan rfc822 headers -----<" + LF + rfcHeaders + ">-----");

					foundAll = parseRfc(rfcHeaders, msgBean);
					msgBean.setDsnRfc822(rfcHeaders);
				}
				byte[] attchValue = (byte[]) aNode.getValue();
				if (attchValue != null) {
					// rfc822 text
					String rfcText = new String(attchValue);
					sb.append(rfcText);
					String mtype = aNode.getMimeType();
					if (mtype.startsWith("text/") || mtype.startsWith("message/")) {
						if (foundAll == false) {
							//logger.debug("parse() - scan rfc822 text -----<" + LF + rfcText + ">-----");

							parseRfc(rfcText, msgBean);
							msgBean.setDsnRfc822(sb.toString());
						}
					}
					if (msgBean.getDsnText() == null) {
						msgBean.setDsnText(rfcText);
					} else {
						msgBean.setDsnText(msgBean.getDsnText() + LF + LF + "RFC822 Text:" + LF + rfcText);
					}
				}
				if (bounceType == null) {
					bounceType = rfcScan.scanBody(sb.toString());
				}
			}
		} // end of RFC Scan

		String body = msgBean.getBody();
		if (msgBean.getRfc822() != null && bounceType == null) {
			// message/rfc822 is present, scan message body for rfc1893 status
			// code
			// TODO: may cause false positives. need to revisit this.
			//logger.debug("parse() - scan body text -----<" + LF + body + ">-----");
			bounceType = rfcScan.scanBody(body);
		}

		// check CC/BCC
		if (bounceType == null) {
			// if the "real_to" address is not found in envelope, but is
			// included in CC or BCC: set bounceType to CC_USER
			for (int i = 0; msgBean.getTo() != null && i < msgBean.getTo().length; i++) {
				Address to = msgBean.getTo()[i];
				if (containsNoAddress(msgBean.getToEnvelope(), to)) {
					if (containsAddress(msgBean.getCc(), to) || containsAddress(msgBean.getBcc(), to)) {
						bounceType = BOUNCE_TYPES.CC_USER.toString();
						break;
					}
				}
			}
		}

		// check VERP bounce address, set bounce type to SOFT_BOUNCE if VERP
		// recipient found
		List<MsgHeader> headers = msgBean.getHeaders();
		for (MsgHeader header : headers) {
			if (VERP_BOUNCE_ADDR_XHEADER.equals(header.getName())) {
				logger.info("parse() - VERP Recipient found: ==>" + header.getValue() + "<==");
				if (msgBean.getOrigRcpt() != null && !StringUtil.isEmpty(header.getValue())
						&& !msgBean.getOrigRcpt().equalsIgnoreCase(header.getValue())) {
					logger.warn("parse() - replace original recipient: " + msgBean.getOrigRcpt()
							+ " with VERP recipient: " + header.getValue());
				}
				if (!StringUtil.isEmpty(header.getValue())) {
					// VERP Bounce - always override
					msgBean.setOrigRcpt(header.getValue());
				} else {
					logger.warn("parse() - " + VERP_BOUNCE_ADDR_XHEADER + " Header found, but it has no value.");
				}
				if (bounceType == null) {
					// a bounced mail shouldn't have Return-Path
					String rPath = msgBean.getReturnPath() == null ? "" : msgBean.getReturnPath();
					if (StringUtil.isEmpty(rPath) || "<>".equals(rPath.trim())) {
						bounceType = BOUNCE_TYPES.SOFT_BOUNCE.toString();
					}
				}
				break;
			}
		}

		// if it's hard or soft bounce and no final recipient was found, scan
		// message body for final recipient using known patterns.
		if (BOUNCE_TYPES.HARD_BOUNCE.toString().equals(bounceType)
				|| BOUNCE_TYPES.SOFT_BOUNCE.toString().equals(bounceType)) {
			if (StringUtil.isEmpty(msgBean.getFinalRcpt()) && StringUtil.isEmpty(msgBean.getOrigRcpt())) {
				String finalRcpt = BounceAddressFinder.getInstance().find(body);
				if (!StringUtil.isEmpty(finalRcpt)) {
					logger.info("parse() - Final Recipient found from message body: " + finalRcpt);
					msgBean.setFinalRcpt(finalRcpt);
				}
			}
		}

		/*
		 * if (bounceType == null) { // use default bounceType =
		 * SmtpScanner.BOUNCETYPE.GENERIC.toString(); }
		 */

		//logger.info("parse() - bounceType: " + bounceType);

		return bounceType;
	}

	private boolean containsAddress(Address[] addrs, Address to) {
		if (to != null && addrs != null && addrs.length > 0) {
			for (int i = 0; i < addrs.length; i++) {
				if (to.equals(addrs[i])) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean containsNoAddress(Address[] addrs, Address to) {
		if (to != null && addrs != null && addrs.length > 0) {
			for (int i = 0; i < addrs.length; i++) {
				if (to.equals(addrs[i])) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Parse the message/delivery-status to retrieve DSN fields. Also used by
	 * message/disposition-notification to retrieve final recipient.
	 * 
	 * @param attchValue
	 *            - delivery status text
	 * @param msgBean
	 *            - MessageBean object
	 */
	private void parseDsn(byte[] attchValue, MessageBean msgBean) {
		// retrieve Final-Recipient, Action, and Status
		ByteArrayInputStream bais = new ByteArrayInputStream(attchValue);
		BufferedReader br = new BufferedReader(new InputStreamReader(bais));
		String line = null;
		try {
			while ((line = br.readLine()) != null) {
				//logger.debug("parseDsn() - Line: " + line);
				line = line.trim();
				if (line.toLowerCase().startsWith("final-recipient:")) {
					// "Final-Recipient" ":" address-type ";" generic-address
					// address-type = rfc822 / unknown
					StringTokenizer st = new StringTokenizer(line, " ;");
					while (st.hasMoreTokens()) {
						String token = st.nextToken().trim();
						if (token.indexOf("@") > 0) {
							msgBean.setFinalRcpt(token);
							//logger.info("parseDsn() - Final_Recipient found: ==>" + token + "<==");
							break;
						}
					}
				} else if (line.toLowerCase().startsWith("original-recipient:")) {
					// "Original-Recipient" ":" address-type ";" generic-address
					StringTokenizer st = new StringTokenizer(line, " ;");
					while (st.hasMoreTokens()) {
						String token = st.nextToken().trim();
						if (token.indexOf("@") > 0) {
							msgBean.setOrigRcpt(token);
						//	logger.info("parseDsn() - Original_Recipient found: ==>" + token + "<==");
							break;
						}
					}
				} else if (line.toLowerCase().startsWith("action:")) {
					/**
					 * "Action" ":" action-value = 1) failed - could not be
					 * delivered to the recipient. 2) delayed - the reporting
					 * MTA has so far been unable to deliver or relay the
					 * message. 3) delivered - the message was successfully
					 * delivered. 4) relayed - the message has been relayed or
					 * gatewayed. 5) expanded - delivered and forwarded by
					 * reporting MTA to multiple additional recipient addresses.
					 */
					String action = line.substring(7).trim();
					msgBean.setDsnAction(action);
				//	logger.debug("parseDsn() - Action found: ==>" + action + "<==");
				} else if (line.toLowerCase().startsWith("status:")) {
					// "Status" ":" status-code (digit "." 1*3digit "." 1*3
					// digit)
					String status = line.substring(7).trim();
					if (status.indexOf(" ") > 0) {
						status = status.substring(0, status.indexOf(" "));
					}
					msgBean.setDsnStatus(status);
				//	logger.debug("parseDsn() - Status found: ==>" + status + "<==");
				} else if (line.toLowerCase().startsWith("diagnostic-code:")) {
					// "Diagnostic-Code" ":" diagnostic-code
					String diagcode = line.substring(16).trim();
					msgBean.setDiagnosticCode(diagcode);
					//logger.debug("parseDsn() - Diagnostic-Code: found: ==>" + diagcode + "<==");
				}
			}
		} catch (IOException e) {
			logger.error("IOException caught during parseDsn()", e);
		}
	}

	/**
	 * parse message/rfc822 to retrieve original email properties: final
	 * recipient, original subject and original SMTP message-id.
	 * 
	 * @param rfc_text
	 *            - rfc822 text
	 * @param msgBean
	 *            - MessageBean object
	 * @return true if all three properties were found
	 */
	private boolean parseRfc(String rfc_text, MessageBean msgBean) {
		// retrieve original To address
		ByteArrayInputStream bais = new ByteArrayInputStream(rfc_text.getBytes());
		BufferedReader br = new BufferedReader(new InputStreamReader(bais));
		int lineCount = 0;
		boolean gotToAddr = false, gotSubj = false, gotSmtpId = false;
		// allows to quit scan once all three headers are found
		String line = null;
		try {
			while ((line = br.readLine()) != null) {

			//	logger.debug("parseRfc() - Line: " + line);
				line = line.trim();
				if (line.toLowerCase().startsWith("to:")) {
					// "To" ":" generic-address
					String token = line.substring(3).trim();
					if (StringUtil.isEmpty(msgBean.getFinalRcpt())) {
						msgBean.setFinalRcpt(token);
					} else if (StringUtil.compareEmailAddrs(msgBean.getFinalRcpt(), token) != 0) {
						logger.error("parseRfc() - Final_Rcpt from RFC822: " + token + " is different from DSN's: "		+ msgBean.getFinalRcpt());
					}
				//	logger.info("parseRfc() - Final_Recipient(RFC822 To) found: ==>" + token + "<==");
					gotToAddr = true;
				} else if (line.toLowerCase().startsWith("subject:")) {
					// "Subject" ":" subject text
					String token = line.substring(8).trim();
					if (StringUtil.isEmpty(msgBean.getOrigSubject())) {
						msgBean.setOrigSubject(token);
					}
					logger.info("parseRfc() - Original_Subject(RFC822 To) found: ==>" + token + "<==");
					gotSubj = true;
				} else if (line.toLowerCase().startsWith("message-id:")) {
					// "Message-Id" ":" SMTP message id
					String token = line.substring(11).trim();
					if (StringUtil.isEmpty(msgBean.getSmtpMessageId())) {
						msgBean.setRfcMessageId(token);
					}
					logger.info("parseRfc() - Smtp Message-Id(RFC822 To) found: ==>" + token + "<==");
					gotSmtpId = true;
				}
				if (gotToAddr && gotSubj && gotSmtpId) {
					return true;
				}
				if (++lineCount > 100 && line.indexOf(":") < 0) {
					break; // check if it's a header after 100 lines
				}
			} // end of while
		} catch (IOException e) {
			logger.error("IOException caught during parseRfc()", e);
		}
		return false;
	}

	public static void main(String[] args) {
		try {
			BounceFinder parser = new BounceFinder();
			MessageBean mBean = new MessageBean();
			try {
				mBean.setFrom(InternetAddress.parse("satyadoesnotexist@google.com", false));
				mBean.setTo(InternetAddress.parse("satyadoesnotexist@google.com", false));
			} catch (AddressException e) {
				logger.error("AddressException caught", e);
			}
			// mBean.setSubject("A Exception occured");
			mBean.setSubject(" Delivery Status Notification (Failure)");
			// mBean.setValue(new Date()+ " 5.2.2 Invalid user account.");
			mBean.setValue(
					"Your message wasn't delivered to satyadoesnotexist@google.com because the address couldn't be found, or is unable to receive mail.");
			mBean.setMailboxUser("testUser");
			String bType = parser.parse(mBean);
			System.out.println("### Bounce Type: " + bType);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
