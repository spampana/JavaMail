package com.helpezee.mail.bounceemail;

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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helpezee.mail.bean.StringUtil;

public final class BounceAddressFinder {

	public static Logger logger = LoggerFactory.getLogger(BounceAddressFinder.class);

 private final List<MyPattern> patternList = new ArrayList<MyPattern>();
 private static BounceAddressFinder addressFinder = null;
 
 private BounceAddressFinder() {
  if (patternList.isEmpty()) {
   loadPatterns();
  }
 }
 
 public static synchronized BounceAddressFinder getInstance() {
  if (addressFinder == null) {
   addressFinder = new BounceAddressFinder();
  }
  return addressFinder;
 }
 
 public String find(String body) {
  if (body != null && body.trim().length() > 0) {
   for (MyPattern myPattern : patternList) {
    Matcher m = myPattern.getPattern().matcher(body);
    if (m.find()) {

      for (int i = 1; i <= m.groupCount(); i++) {
       logger.info(myPattern.getPatternName() + ", group(" + i + ") - " + m.group(i));
      
     }
     return m.group(m.groupCount());
    }
   }
  }
  return null;
 }
 
 private static final class MyPattern {
  private final String patternName;
  private final String patternRegex;
  private final Pattern pattern;
  MyPattern(String name, String value) {
   this.patternName = name;
   this.patternRegex = value;
   pattern = Pattern.compile(patternRegex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  }
  
  public Pattern getPattern() {
   return pattern;
  }
  public String getPatternName() {
   return patternName;
  }
  public String getPatternRegex() {
   return patternRegex;
  }
 }
 
 private final void loadPatterns() {
  String bodyGmail = 
   "Delivery .{4,10} following recipient(?:s)? failed[\\.|\\s](?:permanently:)?\\s+" +
   "<?(" + StringUtil.getEmailRegex() + ")>?\\s+";
  patternList.add(new MyPattern("Gmail",bodyGmail));
  
  String bodyAol = 
   "\\-{3,6} The following address(?:es|\\(es\\))? had (?:permanent fatal errors|delivery problems) \\-{3,6}\\s+" +
   "<?(" + StringUtil.getEmailRegex() + ")>?(?:\\s|;)";
  patternList.add(new MyPattern("AOL",bodyAol));
  
  String bodyYahoo = 
   "This .{1,10} permanent error.\\s+I(?:'ve| have) given up\\. Sorry it did(?:n't| not) work out\\.\\s+" +
   "<?(" + StringUtil.getEmailRegex() + ")>?";
  patternList.add(new MyPattern("Yahoo",bodyYahoo));
  
  String bodyPostfix = 
   "message\\s.*could\\s+not\\s+be\\s+.{0,10}delivered\\s+to\\s.*(?:recipient(?:s)?|destination(?:s)?)" +
   ".{80,180}\\sinclude\\s+this\\s+problem\\s+report.{60,120}" +
   "\\s+<(" + StringUtil.getEmailRegex() + ")>";
  patternList.add(new MyPattern("Postfix",bodyPostfix));
  
  String bodyFailed = 
   "Failed\\s+to\\s+deliver\\s+to\\s+\\'(" + StringUtil.getEmailRegex() + ")\\'" +
   ".{1,20}\\smodule.{5,100}\\sreports";
  patternList.add(new MyPattern("Failed",bodyFailed));
  
  String bodyFirewall = 
   "Your\\s+message\\s+to:\\s+(" + StringUtil.getEmailRegex() + ")\\s+" +
   ".{1,10}\\sblocked\\s+by\\s.{1,20}\\sSpam\\s+Firewall";
  patternList.add(new MyPattern("SpamFirewall",bodyFirewall));
  
  String bodyFailure = 
   "message\\s.{8,20}\\scould\\s+not\\s+be\\s+delivered\\s.{10,40}\\srecipients" +
   ".{6,20}\\spermanent\\s+error.{10,20}\\saddress(?:\\(es\\))?\\s+failed:" +
   "\\s+(" + StringUtil.getEmailRegex() + ")\\s";
  patternList.add(new MyPattern("Failure",bodyFailure));
  
  String bodyUnable = 
   "Unable to deliver message to the following address(?:\\(es\\))?.{0,5}" +
   "\\s+<(" + StringUtil.getEmailRegex() + ")>";
  patternList.add(new MyPattern("Unable",bodyUnable));
  
  String bodyEtrust = 
   "\\scould not deliver the e(?:\\-)?mail below because\\s.{10,20}\\srecipient(?:s)?\\s.{1,10}\\srejected"+
   ".{60,200}\\s(" + StringUtil.getEmailRegex() + ")";
  patternList.add(new MyPattern("eTrust",bodyEtrust));
  
  String bodyReport = 
   "\\scollection of report(?:s)? about email delivery\\s.+\\sFAILED:\\s.{1,1000}" +
   "Final Recipient:.{0,20};\\s*(" + StringUtil.getEmailRegex() + ")";
  patternList.add(new MyPattern("Report",bodyReport));
  
  String bodyNotReach = 
   "Your message.{1,400}did not reach the following recipient(?:\\(s\\))?:" +
   "\\s+(" + StringUtil.getEmailRegex() + ")";
  patternList.add(new MyPattern("NotReach",bodyNotReach));
  
  String bodyFailed2 = 
   "Could not deliver message to the following recipient(?:\\(s\\))?:" +
   "\\s+Failed Recipient:\\s+(" + StringUtil.getEmailRegex() + ")\\s";
  patternList.add(new MyPattern("Failed2",bodyFailed2));
  
  String bodyExceeds = 
   "User(?:'s)?\\s+mailbox\\s+exceeds\\s+allowed\\s+size:\\s+" +
   "(" + StringUtil.getEmailRegex() + ")\\s+";
  patternList.add(new MyPattern("Exceeds",bodyExceeds));
  
  String bodyDelayed = 
   "Message\\s+delivery\\s+to\\s+\\'(" + StringUtil.getEmailRegex() + ")\\'" +
   "\\s+delayed.{1,20}\\smodule.{5,100}\\sreports";
  patternList.add(new MyPattern("Delayed",bodyDelayed));
  
  String bodyInvalid = 
   "Invalid\\s+Address(?:es)?.{1,20}\\b(?:TO|addr)\\b.{1,20}\\s+<?(" + StringUtil.getEmailRegex() + ")>?\\s+";
  patternList.add(new MyPattern("Invalid",bodyInvalid));
 }
}