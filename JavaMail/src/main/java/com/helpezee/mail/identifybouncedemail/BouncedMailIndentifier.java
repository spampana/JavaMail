package com.helpezee.mail.identifybouncedemail;

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
 * If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.util.Date;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.ConnectionEvent;
import javax.mail.event.ConnectionListener;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.StoreEvent;
import javax.mail.event.StoreListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpezee.mail.processor.MailProcessor;
import com.helpezee.mail.processor.Mailbox;

/**
 * This class provides methods to read e-mails from a mailbox.
 */
public class BouncedMailIndentifier implements ConnectionListener, StoreListener {
	private static final long serialVersionUID = -9061869821061961065L;

	public static Logger logger = LoggerFactory.getLogger(BouncedMailIndentifier.class);

	protected final String LF = System.getProperty("line.separator", "\n");
	private final boolean debugSession = false;

	private final Session session;
	private final Mailbox mailbox;
	private final MailProcessor processor;

	private Store store = null;
	private Folder folder = null;
	private Folder processedFolder = null;

	private static final int MAX_WAIT = 120 * 1000; // up to two minutes

	private final int msgsPerRead;
	private final int pollingFreq;
	private int messagesProcessed = 0;

	private static final int[] RetryFreqs = { 5, 10, 10, 20, 20, 20, 30, 30, 30, 30, 60, 60, 60, 60, 60 }; // in
																											// seconds
	private static final int RETRY_FREQ = 120; // in seconds

	public static void main(String[] args) {
		int port = 993;
		String protocol = "imap";
		String folderName = "INBOX";
		int messagesPerRead = 10;
		boolean useSsl = true;
		int maxRetries = 1;
		int minimumWait = 5; // in seconds
		boolean isExchange = false;
		//Make sure this proccessedFolder is created in ur mailbox before testing this functionality
		String proccessedFolderName = "TEST";
		Mailbox vo = new Mailbox("abc@gmail.com", "password", "imap.gmail.com", port, protocol, folderName,
				messagesPerRead, useSsl, maxRetries, minimumWait, isExchange, proccessedFolderName);
		BouncedMailIndentifier reader = new BouncedMailIndentifier(vo);
		try {
			reader.readMail();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * create a MailReader instance
	 * 
	 * @param mbox
	 *            - mailbox properties
	 */
	public BouncedMailIndentifier(Mailbox mbox) {
		this.mailbox = mbox;

		// number of e-mails (="msgsPerPass") to read per cycle
		int msgs_per_read = mbox.getMessagesPerRead();
		msgs_per_read = msgs_per_read <= 0 ? 5 : msgs_per_read; // default is 5
		msgsPerRead = msgs_per_read;

		// number of seconds (="pollingFreq") to wait between reads
		int _freq = mbox.getMinimumWait() * 1000 + msgsPerRead * 100;
		pollingFreq = _freq > MAX_WAIT ? MAX_WAIT : _freq; // upper limit is
															// MAX_WAIT

		logger.debug("Wait between reads in milliseconds: " + pollingFreq);

		// to make the reader more tolerable
		System.setProperty("mail.mime.multipart.ignoremissingendboundary", "true");
		System.setProperty("mail.mime.multipart.ignoremissingboundaryparameter", "true");

		Properties m_props = (Properties) System.getProperties().clone();
		m_props.setProperty("mail.debug", "false");
		m_props.setProperty("mail.debug.quote", "true");

		/* IMAP - properties of com.sun.mail.imap */
		// set timeouts in milliseconds. default for both is infinite
		// Socket connection timeout
		m_props.setProperty("mail.imap.connectiontimeout", "900000");
		// Socket I/O timeout
		m_props.setProperty("mail.imap.timeout", "750000");

		// Certain IMAP servers do not implement the IMAP Partial FETCH
		// functionality properly
		// set Partial fetch to false to workaround exchange server 5.5 bug
		m_props.setProperty("mail.imap.partialfetch", "false");

		// Get a Session object
		m_props.setProperty("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		m_props.setProperty("mail.imap.socketFactory.fallback", "false");
		m_props.setProperty("mail.imap.port", mbox.getPort() + "");
		m_props.setProperty("mail.imap.socketFactory.port", mbox.getPort() + "");
		session = Session.getInstance(m_props);
		processor = new MailProcessor(mbox);
	}

	/**
	 * invoke application plug-in to process e-mails.
	 * 
	 * @throws MessagingException
	 * @throws IOException
	 */
	void readMail() throws MessagingException, IOException {
		session.setDebug(false); // DON'T CHANGE THIS
		String protocol = mailbox.getProtocol();
		if (!"imap".equalsIgnoreCase(protocol)) {
			throw new IllegalArgumentException("Invalid protocol " + protocol);
		}
		if (store == null) {
			try {
				// Get a Store object
				store = session.getStore(protocol);
				store.addConnectionListener(this);
				store.addStoreListener(this);
			} catch (NoSuchProviderException pe) {
				logger.error("NoSuchProviderException caught during session.getStore()" , pe);
				throw pe;
			}
		}
		try {
			connect(store, 0, mailbox.getMaxRetries()); // could fail due to
														// authentication error
			folder = getFolder(store, 0, 1); // retry once on folder
			processedFolder = store.getFolder(mailbox.getProccessedFolderName());
			// reset debug mode
			session.setDebug(debugSession);
			if ("imap".equalsIgnoreCase(protocol)) {
				// only IMAP support MessageCountListener
				final String _folder = mailbox.getFolderName();
				// Add messageCountListener to listen to new messages from IMAP
				// server
				addMsgCountListener(folder, _folder);
			}
			readFromImap();

		} catch (InterruptedException e) {
			logger.error("InterruptedException caught, exiting..." , e);
		} finally {
			try {
				if (folder != null && folder.isOpen()) {
					folder.close(false);
				}
				if (processedFolder != null && processedFolder.isOpen()) {
					processedFolder.close(false);
				}
				store.close();
			} catch (Exception e) {
				logger.error("Exception caught" , e);
			}
		}

		logger.debug("MailReader ended");
	}

	private void readFromImap() throws MessagingException, InterruptedException, IOException {
		// boolean keepRunning = true;
		folder.open(Folder.READ_WRITE);
		/*
		 * fix for some IMAP servers: some IMAP servers wouldn't pick up the
		 * existing messages, the MessageCountListener may not be implemented
		 * correctly for those servers.
		 */
		if (folder.getMessageCount() > 0) {
			logger.debug(mailbox.getUserId() + "'s " + mailbox.getFolderName() + " has " + folder.getMessageCount()
					+ " messages.");
			Date start_tms = new Date();
			Message msgs[] = folder.getMessages();
			execute(msgs, folder, processedFolder);
			folder.expunge(); // remove messages marked as DELETED
			logger.debug(msgs.length + " messages have been expunged from imap mailbox.");
			long proc_time = new Date().getTime() - start_tms.getTime();
			logger.debug(msgs.length + " messages read, time taken: " + proc_time);
		}
		/* end of the fix */
	}

	/**
	 * Add messageCountListener to listen to new messages for IMAP.
	 * 
	 * @param folder
	 *            - a Folder object
	 * @param _folder
	 *            - folder name
	 */
	private void addMsgCountListener(final Folder folder, final String _folder) {
		folder.addMessageCountListener(new MessageCountAdapter() {
			// private final Logger logger =
			// Logger.getLogger(MessageCountAdapter.class);
			public void messagesAdded(MessageCountEvent ev) {
				Message[] msgs = ev.getMessages();
				logger.debug("Got " + msgs.length + " new messages from " + _folder);
				Date start_tms = new Date();
				try {
					execute(msgs, folder, processedFolder);
					folder.expunge(); // remove messages marked as DELETED
					logger.debug(msgs.length + " messages have been expunged from imap mailbox.");
					messagesProcessed += msgs.length;
				} catch (MessagingException ex) {
					logger.error("MessagingException caught  " , ex);
					throw new RuntimeException(ex.getMessage());
				} catch (IOException ex) {
					logger.error("IOException caught  " , ex);
					throw new RuntimeException(ex.getMessage());
				} finally {
					long proc_time = new Date().getTime() - start_tms.getTime();
					logger.debug(msgs.length + " messages processed, time taken: " + proc_time);
				}
			}
		}); // end of IMAP folder.addMessageCountListener
	}

	/*
	 * process e-mails.
	 * 
	 * @param msgs - messages to be processed.
	 * 
	 * @throws MessagingException
	 * 
	 * @throws IOException
	 */
	private void execute(Message[] msgs, Folder folder, Folder processesFolder) throws IOException, MessagingException {
		if (msgs == null || msgs.length == 0)
			return;
		processor.process(msgs, folder, processesFolder);
	}

	/**
	 * implement ConnectionListener interface
	 * 
	 * @param e
	 *            - Connection event
	 */
	public void opened(ConnectionEvent e) {
		 logger.debug(">>> ConnectionListener: connection opened()");
	}

	/**
	 * implement ConnectionListener interface
	 * 
	 * @param e
	 *            - Connection event
	 */
	public void disconnected(ConnectionEvent e) {
		logger.debug(">>> ConnectionListener: connection disconnected()");
	}

	/**
	 * implement ConnectionListener interface
	 * 
	 * @param e
	 *            - Connection event
	 */
	public void closed(ConnectionEvent e) {
 logger.debug(">>> ConnectionListener: connection closed()");
	}

	public void notification(StoreEvent e) {
		 logger.debug(">>> StoreListener: notification event: " , e.getMessage());
	}

	/* end of the implementation */

	/**
	 * connect to Store with retry logic.
	 * 
	 * @param store
	 *            Store object
	 * @param retries
	 *            number of retries performed
	 * @param maxRetries
	 *            number of retries to be performed before giving up
	 * @throws MessagingException
	 *             when retries reached the maxRetries
	 * @throws InterruptedException
	 */
	void connect(Store store, int retries, int maxRetries) throws MessagingException, InterruptedException {
		int portnbr = mailbox.getPort();
		// -1 to use the default port
		logger.debug("Port used: " + portnbr);
		if (retries > 0) { // retrying, close store first
			try {
				store.close();
			} catch (MessagingException e) {
				logger.error("MessagingException caught during retry on store.close()" , e);
			}
		}
		try {
			// connect
			store.connect(mailbox.getHost(), portnbr, mailbox.getUserId(), mailbox.getUserPswd());
		} catch (MessagingException me) {
			if (retries < maxRetries || maxRetries < 0) {
				int sleepFor;
				if (retries < RetryFreqs.length) {
					sleepFor = RetryFreqs[retries];
				} else {
					sleepFor = RETRY_FREQ;
				}
				logger.debug("MessagingException caught during store.connect, retry(=" + retries + ") in " + sleepFor
						+ " seconds");
				try {
					Thread.sleep(sleepFor * 1000);
				} catch (InterruptedException e) {
					logger.error("InterruptedException caught" , e);
					throw e;
				}
				connect(store, ++retries, maxRetries);
			} else {
				logger.error("Exception caught during store.connect, all retries failed...");
				throw me;
			}
		}
	}

	/**
	 * retrieve Folder with retry logic.
	 * 
	 * @param store
	 *            Store object
	 * @param retries
	 *            number of retries performed
	 * @param maxRetries
	 *            number of retries to be performed before giving up
	 * @return Folder instance
	 * @throws MessagingException
	 * @throws InterruptedException
	 */
	Folder getFolder(Store store, int retries, int maxRetries) throws MessagingException, InterruptedException {
		try {
			// Open a Folder
			// folder = store.getDefaultFolder();
			folder = store.getFolder(mailbox.getFolderName());

			if (folder == null || !folder.exists()) {
				throw new MessagingException("Invalid folder " + mailbox.getFolderName());
			}
		} catch (MessagingException me) {
			if (retries < maxRetries || maxRetries < 0) {
				int sleepFor;
				if (retries < RetryFreqs.length) {
					sleepFor = RetryFreqs[retries];
				} else {
					sleepFor = RETRY_FREQ;
				}
				logger.debug("MessagingException caught during store.getFolder, retry(=" + retries + ") in " + sleepFor
						+ " seconds");
				try {
					Thread.sleep(sleepFor * 1000);
				} catch (InterruptedException e) {
					logger.error("InterruptedException caught" , e);
					throw e;
				}
				return getFolder(store, ++retries, maxRetries);
			} else {
				logger.error("Exception caught during store.getFolder, all retries failed");
				throw me;
			}
		}
		return folder;
	}
}