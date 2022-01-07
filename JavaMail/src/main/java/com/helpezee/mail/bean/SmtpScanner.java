package com.helpezee.mail.bean;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scan input string for RFC1893/RFC2821 mail status code
 * 
 * @author jackw
 */
public final class SmtpScanner {

	public static Logger logger = LoggerFactory.getLogger(SmtpScanner.class);

	final int maxLenToScan = 8192 * 4; // scan up to 32k

	private static final HashMap<String, String> RFC1893_STATUS_CODE = new HashMap<String, String>();

	public static enum BOUNCETYPE {
		GENERIC
	}; // default bounce type - not a bounce.

	public static enum BOUNCE_TYPES {
		HARD_BOUNCE, // Hard bounce - suspend,notify,close
		SOFT_BOUNCE, // Soft bounce - bounce++,close
		MAILBOX_FULL, // mailbox full, can be treated as Soft Bounce
		CC_USER, // Mail received as a Carbon Copy
		MDN_RECEIPT, // MDN - read receipt
	}

	private static SmtpScanner smtpCodeScan = null;

	/**
	 * default constructor
	 */
	private SmtpScanner() throws IOException {
		loadRfc1893StatusCode();
	}

	public static SmtpScanner getInstance() throws IOException {
		if (smtpCodeScan == null) {
			smtpCodeScan = new SmtpScanner();
		}
		return smtpCodeScan;
	}

	private static Pattern pattern1 = Pattern.compile("\\s([245]\\.\\d{1,3}\\.\\d{1,3})\\s", Pattern.DOTALL);

	/**
	 * <ul>
	 * <li>first pass: check if it contains a RFC1893 code. RFC1893 codes are
	 * from 5 to 9 bytes long (x.x.x -> x.xxx.xxx) and start with 2.x.x or 4.x.x
	 * or 5.x.x
	 * <li>second pass: check if it contains a 3 digit numeric number: 2xx, 4xx
	 * or 5xx.
	 * </ul>
	 * 
	 * @param str
	 *            - message body
	 * @param pass
	 *            - 1) first pass: look for RFC1893 token (x.x.x). 2) second
	 *            pass: look for RFC2821 token (xxx), must also match reply
	 *            text.
	 * @return bounce type or null if no RFC code is found.
	 */
	public String scanBody(String body) {
		if (StringUtil.isEmpty(body)) { // sanity check
			return null;
		}

		Matcher m = pattern1.matcher(StringUtil.cut(body, maxLenToScan));
		if (m.find()) { // only one time
			String token = m.group(m.groupCount());
			System.out.println("examineBody(): RFC1893 token found: " + token);
			return searchRfc1893CodeTable(token);
		}
		return null;
	}

	private String searchRfc1893CodeTable(String token) {
		return searchRfcCodeTable(token, RFC1893_STATUS_CODE);
	}

	/**
	 * search smtp code table by RFC token.
	 * 
	 * @param token
	 *            - DSN status token, for example: 5.0.0, or 500 depending on
	 *            the map used
	 * @param map
	 *            - either RFC1893_STATUS_CODE or RFC2821_STATUS_CODE
	 * @return message id of the token
	 */
	private String searchRfcCodeTable(String token, HashMap<String, String> map) {
		String type = map.get(token);
		return type;
	}

	/**
	 * load the rfc1893 code table, from Rfc1893.properties file, into memory.
	 * 
	 * @throws IOException
	 */
	private void loadRfc1893StatusCode() throws IOException {
		ClassLoader loader = this.getClass().getClassLoader();
		try {
			// read in RFC1893 status code file and store it in two property
			// objects
			InputStream is = loader.getResourceAsStream("Rfc1893.properties");
			BufferedReader fr = new BufferedReader(new InputStreamReader(is));
			String inStr = null;// , code=null
			while ((inStr = fr.readLine()) != null) {
				if (!inStr.startsWith("#")) {
					// split the line by :
					String[] parts = inStr.split("=");

					// first part is code, second is desc
					String code = parts[0].trim();
					String desc = parts[1].trim();

					// put name, number in HashMap if they are not empty
					if (!code.equals("") && !desc.equals(""))
						RFC1893_STATUS_CODE.put(code, desc);

				}
			}
			fr.close();
		} catch (FileNotFoundException ex) {
			logger.error("file Rfc1893.properties does not exist", ex);
			throw ex;
		} catch (IOException ex) {
			logger.error("IOException caught during loading statcode.conf", ex);
			throw ex;
		}
	}

	private String getMatchingRegex(String desc) throws IOException {
		int left = desc.indexOf("{");
		if (left < 0) {
			return null;
		}
		Stack<Integer> stack = new Stack<Integer>();
		stack.push(Integer.valueOf(left));
		int nextPos = left;
		while (stack.size() > 0) {
			int leftPos = desc.indexOf("{", nextPos + 1);
			int rightPos = desc.indexOf("}", nextPos + 1);
			if (leftPos > rightPos) {
				if (rightPos > 0) {
					stack.pop();
					nextPos = rightPos;
				}
			} else if (leftPos < rightPos) {
				if (leftPos > 0) {
					nextPos = leftPos;
					stack.push(Integer.valueOf(leftPos));
				} else if (rightPos > 0) {
					stack.pop();
					nextPos = rightPos;
				}
			} else {
				break;
			}
		}
		if (nextPos > left) {
			if (stack.size() == 0) {
				return desc.substring(left + 1, nextPos);
			} else {
				logger.error("getMatchingRegex() - missing close curly brace: " + desc);
				throw new IOException("Missing close curly brace: " + desc);
			}
		}
		return null;
	}

	public static void main(String[] args) {
		try {
			SmtpScanner scan = SmtpScanner.getInstance();
			String bounceType = scan.scanBody("aaaaab\n5.0.0\nefg ");
			logger.debug("BounceType: " + bounceType);
			bounceType = scan.scanBody("aaa 201 aab\n422\naccount is full ");
			logger.debug("BounceType: " + bounceType);
			bounceType = scan.scanBody("aaaaab\n400\ntemporary failure ");
			logger.debug("BounceType: " + bounceType);
			logger.debug(scan.getMatchingRegex("{(?:mailbox|account).{0,180}(?:storage|full|limit|quota)}"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
