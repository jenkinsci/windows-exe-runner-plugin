package org.jenkinsci.plugins.windows_exe_runner.util;

import java.util.List;

public abstract class StringUtil {

    private StringUtil() {}

   /**
    *
    * @param value
    * @return
    */
   public static String appendQuote(String value) {
       return String.format("\"%s\"", value);
   }

   /**
    * Null or Space
    * @param value
    * @return
    */
   public static boolean isNullOrSpace(String value) {
       return (value == null || value.trim().length() == 0);
   }

   /**
    *
    * @param args
    * @return
    */
   public static String concatString(List<String> args) {
       StringBuilder buf = new StringBuilder();
       for (String arg : args) {
           if(buf.length() > 0)  buf.append(' ');
           buf.append(arg);
       }
       return buf.toString();
   }
}
