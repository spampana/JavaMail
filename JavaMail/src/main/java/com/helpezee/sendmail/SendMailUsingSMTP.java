package com.helpezee.sendmail;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class SendMailUsingSMTP {
	public static void main(String[] args) {

		String host = "smtp.gmail.com";
		final String user = "abc@gmail.com";// change accordingly
		final String password = "password";// change accordingly

		String to = "xyz@gmail.com";// change accordingly

		Properties props = new Properties();

		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.port", 587);
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.starttls.required", "true");

		Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(user, password);
			}
		});
		session.setDebug(true);

		// Compose the message
		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(user));
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
			message.setSubject("TEST MAIL USING SMTP");
			message.setText("HELLO");

			// send the message
			Transport.send(message);
			System.out.println("message sent successfully using smpt");

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}