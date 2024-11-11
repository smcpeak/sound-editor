// SoundEdit.java
// Simple sound editor.

package snded;

import static util.Util.getExceptionMessage;

//import javax.sound.sampled.*;

public class SoundEdit {
  public static void main(String args[])
  {
    try {
      if (args.length < 1) {
        System.err.println("usage: snded <file.wav>");
        System.exit(2);
      }

      String fname = args[0];
      System.out.println("fname: " + fname);

      //try (File wavFile = new File(fname)) {
      //}
    }
    catch (Exception e) {
      System.out.println(getExceptionMessage(e));
    }
  }
}

// EOF
