// ArgMap.java

package snded;

import util.StringUtil;

import java.util.HashMap;
import java.util.Map;


// Map of command line arguments, originally expressed as
// "<name>:<value>" pairs.
public class ArgMap {
  // ---- private data ----
  // Map from name to value.
  private Map<String, String> m_argMap = new HashMap<String, String>();

  // ---- methods ----
  public ArgMap(String args[])
  {
    for (String arg : args) {
      int colonIndex = arg.indexOf(':');
      if (colonIndex < 1 || colonIndex == arg.length()-1) {
        throw new RuntimeException(
          "Argument must be of the form \"<name>:<value>\": " +
          StringUtil.doubleQuote(arg));
      }
      String name = arg.substring(0, colonIndex);
      String value = arg.substring(colonIndex+1);
      assert(name.length() > 0);
      assert(value.length() > 0);

      m_argMap.put(name, value);
    }
  }

  public int getInt(String name, int defaultValue)
  {
    if (m_argMap.containsKey(name)) {
      return Integer.valueOf(m_argMap.get(name));
    }
    else {
      System.out.println(
        "using default " + StringUtil.doubleQuote(name) +
        " (int): " + defaultValue);
      return defaultValue;
    }
  }

  public float getFloat(String name, float defaultValue)
  {
    if (m_argMap.containsKey(name)) {
      return Float.valueOf(m_argMap.get(name));
    }
    else {
      System.out.println(
        "using default " + StringUtil.doubleQuote(name) +
        " (float): " + defaultValue);
      return defaultValue;
    }
  }

  public String getRequiredString(String name)
  {
    if (m_argMap.containsKey(name)) {
      return m_argMap.get(name);
    }
    else {
      throw new RuntimeException(
        "Command requires argument " + StringUtil.doubleQuote(name));
    }
  }
}


// EOF
