package com.helpezee.mail.bean;

/*
 * blog/javaclue/javamail/StringUtil.java
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StringUtil {

	public static Logger logger = LoggerFactory.getLogger(StringUtil.class);
	public final static String TOKEN_XHDR_BEGIN = "10.";
	public final static String TOKEN_XHDR_END = ".0";

	private StringUtil() {
	}

	public static boolean isEmpty(String str) {
		return (str == null || str.trim().length() == 0);
	}

	/**
	 * trim the input string from the right to the specified length.
	 * 
	 * @param str
	 *            - original string
	 * @param len
	 *            - string size
	 * @return string trimmed to the size of "len"
	 */
	public static String cut(String str, int len) {
		if (str == null || str.length() <= len || len < 0)
			return str;
		else
			return str.substring(0, len);
	}

	/**
	 * trim the input string from the right to the specified length.
	 * 
	 * @param str
	 *            - original string
	 * @param len
	 *            - string size
	 * @return trimmed string plus three dots if its size is over specified
	 *         length.
	 */
	public static String cutWithDots(String str, int len) {
		if (str == null || str.length() <= len || len < 0)
			return str;
		else if (str.length() > len)
			return str.substring(0, len) + "...";
		else
			return str;
	}

	/**
	 * remove double and single quotes from input string.
	 * 
	 * @param data
	 *            - input string
	 * @return string with quotes removed, or null if input is null
	 */
	public static String removeQuotes(String data) {
		if (data == null)
			return data;
		StringTokenizer st = new StringTokenizer(data, "\"\'");
		StringBuffer sb = new StringBuffer();
		while (st.hasMoreTokens())
			sb.append(st.nextToken());

		return sb.toString();
	}

	/**
	 * convert Address array to a string, addresses are comma delimited. Display
	 * names are removed from returned addresses.
	 * 
	 * @param addrs
	 *            - Address array
	 * @return a string of addresses, comma delimited. or null if input is null
	 */
	public static String addrToString(Address[] addrs) {
		return addrToString(addrs, true);
	}

	/**
	 * convert Address array to string, addresses are comma delimited.
	 * 
	 * @param addrs
	 *            - Address array
	 * @param removeDisplayName
	 *            - remove display name from addresses if true
	 * @return a string of addresses, comma delimited. or null if input is null
	 */
	public static String addrToString(Address[] addrs, boolean removeDisplayName) {
		if (addrs == null || addrs.length == 0) {
			return null;
		}
		String str = addrs[0].toString();
		if (removeDisplayName) {
			str = removeDisplayName(str);
		}
		for (int i = 1; i < addrs.length; i++) {
			if (removeDisplayName) {
				str = str + "," + removeDisplayName(addrs[i].toString());
			} else {
				str = str + "," + addrs[i].toString();
			}
		}
		return str;
	}

	/**
	 * remove display name from an email address, and convert all characters to
	 * lower case.
	 * 
	 * @param addr
	 *            - email address
	 * @return email address without display name, or null if input is null.
	 */
	public static String removeDisplayName(String addr) {
		return removeDisplayName(addr, true);
	}

	/**
	 * remove display name from an email address.
	 * 
	 * @param addr
	 *            - email address
	 * @param toLowerCase
	 *            - true to convert characters to lower case
	 * @return email address without display name, or null if input is null.
	 */
	public static String removeDisplayName(String addr, boolean toLowerCase) {
		if (isEmpty(addr)) {
			return addr;
		}
		int at_pos = addr.lastIndexOf("@");
		if (at_pos > 0) {
			int pos1 = addr.lastIndexOf("<", at_pos);
			int pos2 = addr.indexOf(">", at_pos + 1);
			if (pos1 >= 0 && pos2 > pos1) {
				addr = addr.substring(pos1 + 1, pos2);
			}
		}
		if (toLowerCase)
			return addr.toLowerCase();
		else
			return addr;
	}

	/**
	 * check if an email address has a display name.
	 * 
	 * @param addr
	 *            - email address
	 * @return true if it has a display name
	 */
	public static boolean hasDisplayName(String addr) {
		if (isEmpty(addr))
			return false;
		return addr.matches("^\\s*\\S+.{0,250}\\<.+\\>\\s*$");
	}

	/**
	 * return the display name of an email address.
	 * 
	 * @param addr
	 *            - email address
	 * @return - display name of the address, or null if the email address does
	 *         not have a display name.
	 */
	public static String getDisplayName(String addr) {
		if (isEmpty(addr)) {
			return null;
		}
		int at_pos = addr.lastIndexOf("@");
		if (at_pos > 0) {
			int pos1 = addr.lastIndexOf("<", at_pos);
			int pos2 = addr.indexOf(">", at_pos + 1);
			if (pos1 >= 0 && pos2 > pos1) {
				String dispName = addr.substring(0, pos1);
				return dispName.trim();
			}
		}
		return null;
	}

	/**
	 * Compare two email addresses. Email address could be enclosed by angle
	 * brackets and it should still be equal to the one without angle brackets.
	 * 
	 * @param addr1
	 *            - email address 1
	 * @param addr2
	 *            - email address 2
	 * @return 0 if addr1 == addr2, -1 if addr1 < addr2, or 1 if addr1 > addr2.
	 */
	public static int compareEmailAddrs(String addr1, String addr2) {
		if (addr1 == null) {
			if (addr2 != null) {
				return -1;
			} else {
				return 0;
			}
		} else if (addr2 == null) {
			return 1;
		}
		addr1 = removeDisplayName(addr1, true);
		addr2 = removeDisplayName(addr2, true);
		return addr1.compareToIgnoreCase(addr2);
	}

	/**
	 * returns the domain name of an email address.
	 * 
	 * @param addr
	 *            - email address
	 * @return domain name of the address, or null if it's a local address
	 */
	public static String getEmailDomainName(String addr) {
		if (isEmpty(addr)) {
			return null;
		}
		int pos;
		if ((pos = addr.lastIndexOf("@")) > 0) {
			String domain = addr.substring(pos + 1).trim();
			if (domain.endsWith(">")) {
				domain = domain.substring(0, domain.length() - 1);
			}
			return (domain.length() == 0 ? null : domain);
		}
		return null;
	}

	/**
	 * Strip off leading and trailing spaces for all String objects on a list.
	 * 
	 * @param list
	 *            - a list of objects
	 */
	public static void stripAll(ArrayList<Object> list) {
		if (list == null)
			return;
		for (int i = 0; i < list.size(); i++) {
			Object obj = list.get(i);
			if (obj != null && obj instanceof String)
				list.set(i, ((String) obj).trim());
		}
	}

	/**
	 * Strip off leading and trailing spaces for all String objects in an array.
	 * 
	 * @param array
	 *            - an array of objects
	 */
	public static void stripAll(Object[] array) {
		if (array == null)
			return;
		for (int i = 0; i < array.length; i++) {
			Object obj = array[i];
			if (obj != null && obj instanceof String)
				array[i] = ((String) obj).trim();
		}
	}

	/**
	 * For String objects found in the bean class with a getter and a setter,
	 * this method will strip off the leading and trailing spaces of those
	 * string objects.
	 * 
	 * @param obj
	 *            - a bean object
	 */
	public static void stripAll(Object obj) {
		if (obj == null) {
			return;
		}
		Method methods[] = obj.getClass().getDeclaredMethods();
		try {
			Class<?> setParms[] = { Class.forName("java.lang.String") };
			for (int i = 0; i < methods.length; i++) {
				Method method = (Method) methods[i];
				Class<?> parmTypes[] = method.getParameterTypes();
				int mod = method.getModifiers();
				if (Modifier.isPublic(mod) && !Modifier.isAbstract(mod) && !Modifier.isStatic(mod)) {
					if (method.getName().startsWith("get") && parmTypes.length == 0
							&& method.getReturnType().getName().equals("java.lang.String")) {
						// invoke the get method
						String str = (String) method.invoke(obj, (Object[]) parmTypes);
						if (str != null) { // trim the string
							String setMethodName = "set" + method.getName().substring(3);
							try {
								Method setMethod = obj.getClass().getMethod(setMethodName, setParms);
								if (setMethod != null) {
									String strParms[] = { str.trim() };
									setMethod.invoke(obj, (Object[]) strParms);
								}
							} catch (Exception e) {
								// no corresponding set method, ignore.
							}
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("ERROR: Exception caught during reflection - " + e);
			e.printStackTrace();
		}
	}

	/**
	 * replace all occurrences of replFrom with replWith in a string.
	 * 
	 * @param body
	 *            - message text
	 * @param replFrom
	 *            - from string
	 * @param replWith
	 *            - with string
	 * @return new string
	 */
	public static String replaceAll(String body, String replFrom, String replWith) {
		if (body == null || body.trim().length() == 0) {
			return body;
		}
		if (replFrom == null || replWith == null) {
			logger.warn("replaceAll() - either replFrom or replyWith is null.");
			return body;
		}
		StringBuffer sb = new StringBuffer();
		int newpos = 0, pos = 0;
		while ((newpos = body.indexOf(replFrom, pos)) >= 0) {
			sb.append(body.substring(pos, newpos));
			sb.append(replWith);
			pos = newpos + Math.max(1, replFrom.length());
		}
		sb.append(body.substring(pos, body.length()));
		return sb.toString();
	}

	/**
	 * remove the first occurrence of the given string from a string.
	 * 
	 * @param body
	 *            - original body
	 * @param removeStr
	 *            - string to be removed
	 * @return new body
	 */
	public static String removeFirstString(String body, String removeStr) {
		return removeString(body, removeStr, false);
	}

	/**
	 * remove the last occurrence of the given string from a string.
	 * 
	 * @param body
	 *            - original body
	 * @param removeStr
	 *            - string to be removed
	 * @return new body
	 */
	public static String removeLastString(String body, String removeStr) {
		return removeString(body, removeStr, true);
	}

	private static String removeString(String body, String removeStr, boolean removeLast) {
		if (body == null || body.trim().length() == 0) {
			return body;
		}
		if (removeStr == null || removeStr.trim().length() == 0) {
			return body;
		}
		int pos = -1;
		if (removeLast) {
			pos = body.lastIndexOf(removeStr);
		} else {
			pos = body.indexOf(removeStr);
		}
		if (pos >= 0) {
			body = body.substring(0, pos) + body.substring(pos + removeStr.length());
		}
		return body;
	}

	/**
	 * returns a string of periods.
	 * 
	 * @param level
	 *            - number periods to be returned
	 * @return string of periods
	 */
	public static String getPeriods(int level) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < level; i++) {
			sb.append(".");
		}
		return sb.toString();
	}

	public static String removeCRLFTabs(String str) {
		// remove possible CR/LF and tabs, that are inserted by some Email
		// servers, from the Email_ID found in bounced E-mails (MS exchange
		// server for one). MS exchange server inserted "\r\n\t" into the
		// Email_ID string, and it caused "check digit test" error.
		StringTokenizer sTokens = new StringTokenizer(str, "\r\n\t");
		StringBuffer sb = new StringBuffer();
		while (sTokens.hasMoreTokens()) {
			sb.append(sTokens.nextToken());
		}
		return sb.toString();
	}

	private final static String localPart = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*";
	private final static String remotePart = "@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])+";
	private final static String intraPart = "@[a-z0-9](?:[a-z0-9-]*[a-z0-9])+";

	private final static Pattern remotePattern = Pattern.compile("^" + localPart + remotePart + "$",
			Pattern.CASE_INSENSITIVE);
	private final static Pattern intraPattern = Pattern.compile("^" + localPart + intraPart + "$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern localPattern = Pattern.compile("^" + localPart + "$", Pattern.CASE_INSENSITIVE);

	public static String getEmailRegex() {
		return localPart + remotePart;
	}

	/**
	 * Check if the provided string is a valid email address. This conforms to
	 * the RFC822 and RFC1035 specifications. Both local part and remote part
	 * are required.
	 * 
	 * @param string
	 *            The string to be checked.
	 * @return True if string is an valid email address. False if not.
	 */
	public static boolean isRemoteEmailAddress(String string) {
		if (string == null)
			return false;
		Matcher matcher = remotePattern.matcher(string);
		return matcher.matches();
	}

	/**
	 * Check if the provided string is a valid remote or Intranet email address.
	 * An Intranet email address could include only a sub-domain name such as
	 * "bounce" or "localhost" as its remote part.
	 * 
	 * @param string
	 *            The string to be checked.
	 * @return True if string is an valid email address. False if not.
	 */
	public static boolean isRemoteOrIntranetEmailAddress(String string) {
		if (string == null)
			return false;
		if (isRemoteEmailAddress(string))
			return true;
		Matcher matcher = intraPattern.matcher(string);
		return matcher.matches();
	}

	/**
	 * matches any remote or local email addresses like john (without a remote
	 * part) or john@localhost or john@smith.com.
	 * 
	 * @param string
	 *            the email address to be checked
	 * @return true if it's a valid email address
	 */
	public static boolean isRemoteOrLocalEmailAddress(String string) {
		if (string == null)
			return false;
		if (isRemoteOrIntranetEmailAddress(string))
			return true;
		Matcher matcher = localPattern.matcher(string);
		return matcher.matches();
	}

	public static boolean isValidEmailLocalPart(String string) {
		Matcher matcher = localPattern.matcher(string);
		return matcher.matches();
	}

	private static String bounceRegex = (new StringBuilder("\\s*\\W?((\\w+)\\-(")).append(TOKEN_XHDR_BEGIN)
			.append("\\d+").append(TOKEN_XHDR_END).append(")\\-(.+\\=.+)\\@(.+\\w))\\W?\\s*").toString();
	// for ex.: bounce-10.07410251.0-jsmith=test.com@localhost
	private static Pattern bouncePattern = Pattern.compile(bounceRegex);
	private static String removeRegex = "\\s*\\W?((\\w+)\\-(\\w+)\\-(.+\\=.+)\\@(.+\\w))\\W?\\s*";
	// for ex.: remove-testlist-jsmith=test.com@localhost
	private static Pattern removePattern = Pattern.compile(removeRegex);

	public static boolean isVERPAddress(String recipient) {
		if (isEmpty(recipient)) {
			return false;
		}
		Matcher bounceMatcher = bouncePattern.matcher(recipient);
		Matcher removeMatcher = removePattern.matcher(recipient);
		return bounceMatcher.matches() || removeMatcher.matches();
	}

	public static String getDestAddrFromVERP(String verpAddr) {
		Matcher bounceMatcher = bouncePattern.matcher(verpAddr);
		if (bounceMatcher.matches()) {
			if (bounceMatcher.groupCount() >= 5) {
				String destAddr = bounceMatcher.group(2) + "@" + bounceMatcher.group(5);
				return destAddr;
			}
		}
		Matcher removeMatcher = removePattern.matcher(verpAddr);
		if (removeMatcher.matches()) {
			if (removeMatcher.groupCount() >= 5) {
				String destAddr = removeMatcher.group(2) + "@" + removeMatcher.group(5);
				return destAddr;
			}
		}
		return verpAddr;
	}

	public static String getOrigAddrFromVERP(String verpAddr) {
		Matcher bounceMatcher = bouncePattern.matcher(verpAddr);
		if (bounceMatcher.matches()) {
			if (bounceMatcher.groupCount() >= 4) {
				String origAddr = bounceMatcher.group(4).replace('=', '@');
				return origAddr;
			}
		}
		Matcher removeMatcher = removePattern.matcher(verpAddr);
		if (removeMatcher.matches()) {
			if (removeMatcher.groupCount() >= 4) {
				String origAddr = removeMatcher.group(4).replace('=', '@');
				return origAddr;
			}
		}
		return verpAddr;
	}

	public static void main(String[] args) {
		String addr = "\"ORCPT foobar@nc.rr.com\" <foobar@nc.rr.com>";
		addr = "DirectStarTV <fqusoogd.undlwfeteot@chaffingphotosensitive.com>";
		logger.debug(addr + " --> " + removeDisplayName(addr));

		logger.debug(removeFirstString("<pre>12345abcdefklqhdkh</pre>", "<pre>"));
		logger.debug("EmailAddr: " + isRemoteEmailAddress("A!#$%&'*+/=?.^_`{|}~-BC@localhost.us"));
		logger.debug("EmailAddr: " + isRemoteOrLocalEmailAddress("A!#$%&'*+/=?.^_`{|}~-BC"));
		logger.debug(getOrigAddrFromVERP("bounce-10.07410251.0-jsmith=test.com@localhost"));
		logger.debug(getOrigAddrFromVERP("remove-testlist-jsmith=test.com@localhost"));
	}
}