package com.helpezee.mail.processor;

/*
 * blog/javaclue/javamail/MailProcessor.java
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpezee.mail.bean.MessageBean;
import com.helpezee.mail.bean.MessageBeanUtil;
import com.helpezee.mail.bounceemail.BounceFinder;

/**
 * process email's handed over by MailReader class.
 * 
 * @author JackW
 */
public class MailProcessor {

	public static Logger logger = LoggerFactory.getLogger(MailProcessor.class);

	protected final String LF = System.getProperty("line.separator", "\n");
	private final Mailbox mailbox;

	private static final int MAX_BODY_SIZE = 150 * 1024; // 150KB
	private static final int MAX_CMPT_SIZE = 1024 * 1024; // 1MB
	private static final int MAX_TOTAL_SIZE = 10 * 1024 * 1024; // 10MB

	public MailProcessor(Mailbox mailbox) {
		this.mailbox = mailbox;
	}

	/**
	 * process messages.
	 * 
	 * @param msgs
	 *            - array of Messages.
	 * @throws MessagingException
	 * @throws IOException
	 */
	public void process(Message[] msgs, Folder folder, Folder processedFolder) throws MessagingException, IOException {

		//logger.debug("Entering process() method...");
		List<Message> tempList = new ArrayList<Message>();
		for (int i = 0; i < msgs.length; i++) { // msgs.length;
			MessageBean bean = processPart(msgs[i]);
			//logger.debug("Message Processed ----" + i + "---->" + bean.isMessageProcessed());
			if (bean.isMessageProcessed())
				tempList.add(msgs[i]);
		}
		/// message has been processed, move message to other folder
/*		Message[] messages = tempList.toArray(new Message[tempList.size()]);
		folder.copyMessages(messages, processedFolder);
		folder.setFlags(messages, new Flags(Flags.Flag.DELETED), true);*/
	}

	/**
	 * process message part
	 * 
	 * @param p
	 *            - part
	 * @throws MessagingException
	 * @throws IOException
	 */
	MessageBean processPart(Part p) throws IOException, MessagingException {
		Date start_tms = new Date();

		// parse the MimeMessage to MessageBean
		MessageBean msgBean = MessageBeanUtil.mimeToBean(p);

		// MailBox Host Address
		msgBean.setMailboxHost(mailbox.getHost());
		// MailBox User Id
		msgBean.setMailboxUser(mailbox.getUserId());

		// get message body
		String body = msgBean.getBody();

		// check message body and component size
		boolean msgSizeTooLarge = false;
		if (body.length() > MAX_BODY_SIZE) {
			msgSizeTooLarge = true;
			logger.debug("Message body size exceeded limit: " + body.length());
		}
		int totalSize = body.length();
		if (!msgSizeTooLarge && msgBean.getComponentsSize().size() > 0) {
			for (int i = 0; i < msgBean.getComponentsSize().size(); i++) {
				Integer objSize = (Integer) msgBean.getComponentsSize().get(i);
				if (objSize.intValue() > MAX_CMPT_SIZE) {
					msgSizeTooLarge = true;
					logger.debug("Message component(" + i + ") exceeded limit: " + objSize.intValue());
					break;
				}
				totalSize += objSize;
			}
		}
		if (!msgSizeTooLarge && totalSize > MAX_TOTAL_SIZE) {
			logger.debug("Message total size exceeded limit: " + totalSize);
			msgSizeTooLarge = true;
		}

		if (msgSizeTooLarge) {
			logger.debug("The email message has been rejected due to its size");
			// XXX - add your code here to deal with it
		} else { // email size within the limit
			if (msgBean.getSmtpMessageId() == null) {
				logger.debug("SMTP Message-Id is null, FROM Address = " + msgBean.getFromAsString());
			}

			// logger.debug("Message read..." + LF + msgBean);
			// XXX: Add you code here to process the message ...
		}
		if (msgBean.getAttachCount() > 0)
			logger.debug("Number of attachments receibved: " + msgBean.getAttachCount());

		long time_spent = new Date().getTime() - start_tms.getTime();

		//logger.debug("Msg from " + msgBean.getFromAsString() + " processed, " + time_spent);

		try {
			// Checking mail is bounced mail or not
			BounceFinder parser = new BounceFinder();
			String bType = parser.parse(msgBean);
			logger.debug("**********************************");
			logger.debug("Email Subject   : " + msgBean.getSubject());			
			if (bType != null) {
				logger.debug("Bounced Email,  Reason for failure is   : " + bType);			
			}else{
				logger.debug("Non Bounced Mail");
			}
			logger.debug("**********************************");
			msgBean.setMessageProcessed(true);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return msgBean;
	}
}