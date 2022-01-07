package com.helpezee.mail.sendmail;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class SendMailUsingSMTPS {
	public static void main(String[] args) {

		String host = "smtp.gmail.com";
		final String user = "abc@gmail.com";// change accordingly
		final String password = "password";// change accordingly

		String to = "xyz@gmail.com";// change accordingly

		Properties props = new Properties();
		props.put("mail.transport.protocol", "smtps");
		props.put("mail.smtps.host", host);
		props.put("mail.smtps.port", 465);
		props.put("mail.smtps.auth", "true");
		props.put("mail.smtps.starttls.enable", "true");
		props.put("mail.smtps.starttls.required", "true");
		Session mailSession = Session.getDefaultInstance(props);
		mailSession.setDebug(true);

		// Compose the message
		try {
			Transport transport = mailSession.getTransport();
			MimeMessage message = new MimeMessage(mailSession);
			message.setFrom(new InternetAddress(user));
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
			message.setSubject("TEST MAIL USING SMTPS");
			message.setText("HELLO");

			transport.connect(host, 465, user, password);

			transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO));

			System.out.println("message sent successfully using smpts");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}