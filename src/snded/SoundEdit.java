// SoundEdit.java
// Simple sound editor.

package snded;

import util.StringUtil;
import util.Util;

import mcve.audio.SimpleAudioConversion;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class SoundEdit {
  public void printInfo(AudioInputStream audio) throws IOException
  {
    AudioFormat fmt = audio.getFormat();
    System.out.println("format: " + fmt);
    System.out.println("channels: " + fmt.getChannels());
    System.out.println("frame rate (fr/s): " + fmt.getFrameRate());
    System.out.println("frame size (bytes): " + fmt.getFrameSize());
    System.out.println("sample rate (Hz): " + fmt.getSampleRate());
    System.out.println("sample size in bits: " + fmt.getSampleSizeInBits());
    System.out.println("is big endian: " + fmt.isBigEndian());
    System.out.println("properties: " + fmt.properties());

    int bytesPerSample = SimpleAudioConversion.bytesPerSample(fmt.getSampleSizeInBits());
    System.out.println("bytes per sample: " + bytesPerSample);
    assert(bytesPerSample > 0);

    System.out.println("available: " + audio.available());
    System.out.println("frame length: " + audio.getFrameLength());

    int numBytesAvail = audio.available();
    byte[] bytes = new byte[numBytesAvail];
    int numBytesRead = audio.read(bytes);

    System.out.println("read " + numBytesRead + " bytes; first 10:");

    for (int i=0; i < 10 && i < bytes.length; ++i) {
      System.out.println("  byte " + i + ": " + bytes[i]);
    }

    int numSamples = numBytesRead / bytesPerSample;
    System.out.println("num samples: " + numSamples);

    // Note: For stereo audio, the samples are interleaved as (L,R)
    // pairs.
    float[] samples = new float[numSamples];
    int numConvertedSamples = SimpleAudioConversion.decode(
      bytes,
      samples,
      numBytesRead,
      fmt);
    System.out.println("converted samples: " + numConvertedSamples);

    for (int i=0;
         i < 10 && i < samples.length && i < numConvertedSamples;
         ++i) {
      float f = samples[i];

      // Express the sample as decibels relative to 1.0.  This mimics
      // the calculation done by Audacity.
      //
      // Also I can't use the "?:" operator here because of a bug in
      // the java compiled that issues a spurious warning.
      //
      float decibels;
      if (f == 0.0f) {
        decibels = -100.0f;
      }
      else {
        decibels = (float)(20.0 * Math.log10(Math.abs(f)));
      }

      System.out.println("  sample " + i + ": " + f + "  \t" +
                         decibels + " dB");
    }
  }

  public void parseCommand(AudioInputStream audio, String command, String[] args)
    throws IOException
  {
    switch (command) {
      case "info":
        printInfo(audio);
        break;

      default:
        throw new RuntimeException(
          "Unknown command: " + StringUtil.doubleQuote(command));
    }
  }

  public static void main(String args[])
  {
    SoundEdit se = new SoundEdit();
    try {
      if (args.length < 2) {
        System.err.print(
          """
          usage: snded <file.wav> <command> [<args>]

          commands:
            info
              Print some details about the given file.
          """);
        System.exit(2);
      }

      String fname = args[0];
      String command = args[1];
      String[] cmdArgs = Arrays.copyOfRange(args, 2, args.length);

      try (AudioInputStream audio = AudioSystem.getAudioInputStream(new File(fname))) {
        se.parseCommand(audio, command, cmdArgs);
      }
    }
    catch (Exception e) {
      System.out.println(Util.getExceptionMessage(e));
      System.exit(2);
    }
  }
}

// EOF
