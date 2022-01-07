package com.helpezee.searchmail;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.SearchTerm;

import com.sun.mail.imap.IMAPFolder.FetchProfileItem;

/**
 * This program demonstrates how to search for e-mail messages which satisfy a
 * search criterion.
 * 
 * @author www.codejava.net
 *
 */
public class EmailSearcher {

	/**
	 * Searches for e-mail messages containing the specified keyword in Subject
	 * field.
	 * 
	 * @param host
	 * @param port
	 * @param userName
	 * @param password
	 * @param keyword
	 * @throws InterruptedException
	 */
	public void searchEmail(String host, String port, String userName, String password, final String keyword)
			throws InterruptedException {
		Properties properties = new Properties();

		// server setting
		properties.put("mail.imap.host", host);
		properties.put("mail.imap.port", port);
		properties.setProperty("mail.imap.partialfetch","false");
		properties.setProperty("mail.imap.fetchsize", "100000");

		// SSL setting
		properties.setProperty("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		properties.setProperty("mail.imap.socketFactory.fallback", "false");
		properties.setProperty("mail.imap.socketFactory.port", String.valueOf(port));

		Session session = Session.getDefaultInstance(properties);

		try {
			// connects to the message store
			Store store = session.getStore("imap");
			store.connect(userName, password);

			// opens the inbox folder
			Folder folderInbox = store.getFolder("INBOX");
			// Create TEST Folder in mailbox
			Folder destinationFolder = store.getFolder("TEST");
			folderInbox.open(Folder.READ_WRITE);
			destinationFolder.open(Folder.READ_WRITE);

			// creates a subject search criterion
			SearchTerm subjectSearchCondition = new SearchTerm() {

				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				@Override
				public boolean match(Message message) {
					try {
						if (message != null && message.getSubject() != null && message.getSubject().contains(keyword)) {
							return true;
						}
					} catch (MessagingException ex) {
						ex.printStackTrace();
					}
					return false;
				}
			};

			//https://stackoverflow.com/questions/20237801/reading-from-javamail-takes-a-long-time
			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add(FetchProfileItem.FLAGS);
			fp.add(FetchProfileItem.CONTENT_INFO);
			fp.add("X-mailer");

			// performs search through the folder
			Message[] foundMessages = folderInbox.search(subjectSearchCondition);
			System.out.println("Message Length ----" + foundMessages.length);

			List<Message> tempList = new ArrayList<Message>();
			for (int i = 0; i < foundMessages.length; i++) {
				Message message = foundMessages[i];
				String subject = message.getSubject();
				System.out.println("Found message #" + i + ": " + subject);
				System.out.println("Moving message #" + i + " to WVCP Folder");
				tempList.add(message);
				// TODO Some Processing logic

			}
			// moveMessage(tempList, folderInbox, destinationFolder, 2000);
			Message[] messages = tempList.toArray(new Message[tempList.size()]);
			folderInbox.copyMessages(messages, destinationFolder);
			folderInbox.setFlags(messages, new Flags(Flags.Flag.DELETED), true);
			folderInbox.expunge();

			// disconnect
			folderInbox.close(true);
			destinationFolder.close(false);
			store.close();
		} catch (NoSuchProviderException ex) {
			System.out.println("No provider.");
			ex.printStackTrace();
		} catch (MessagingException ex) {
			System.out.println("Could not connect to the message store.");
			ex.printStackTrace();
		}
	}

	
	/**
	 * Test this program with a Gmail's account
	 * 
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		String host = "imap.gmail.com";
		String port = "993";
		String username = "abc@gmail.com";// change accordingly
		String password = "password";// change accordingly
		EmailSearcher searcher = new EmailSearcher();
		String keyword = "Test";
		searcher.searchEmail(host, port, username, password, keyword);
	}

}