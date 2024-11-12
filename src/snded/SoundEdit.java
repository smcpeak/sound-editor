// SoundEdit.java
// Simple sound editor.

package snded;

import util.StringUtil;
import util.Util;

import mcve.audio.SimpleAudioConversion;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SoundEdit {
  public static int getBytesPerSample(AudioInputStream audio)
  {
    AudioFormat fmt = audio.getFormat();
    int ret = SimpleAudioConversion.bytesPerSample(fmt.getSampleSizeInBits());
    assert(ret > 0);
    return ret;
  }

  // Read all of the samples from `audio` into an array of `float`.
  //
  // For stereo audio, the samples are interleaved as (L,R) pairs.
  //
  public float[] getSamples(AudioInputStream audio) throws IOException
  {
    AudioFormat fmt = audio.getFormat();
    int bytesPerSample = getBytesPerSample(audio);

    int numBytesAvail = audio.available();
    byte[] bytes = new byte[numBytesAvail];
    int numBytesRead = audio.read(bytes);

    int numSamples = numBytesRead / bytesPerSample;

    float[] samples = new float[numSamples];
    int numConvertedSamples = SimpleAudioConversion.decode(
      bytes,
      samples,
      numBytesRead,
      fmt);
    assert(numConvertedSamples == numSamples);

    return samples;
  }

  public void printBytes(AudioInputStream audio, int maxBytes) throws IOException
  {
    AudioFormat fmt = audio.getFormat();

    int numBytesAvail = audio.available();
    byte[] bytes = new byte[numBytesAvail];
    int numBytesRead = audio.read(bytes);

    System.out.println("read " + numBytesRead + " bytes:");

    for (int i=0; i < maxBytes && i < bytes.length; ++i) {
      System.out.println("  byte " + i + ": " + bytes[i]);
    }
  }

  public void printInfo(AudioInputStream audio) throws IOException
  {
    AudioFormat fmt = audio.getFormat();
    System.out.println("format: " + fmt);
    System.out.println("channels: " + fmt.getChannels());
    System.out.println("frame rate (Hz): " + fmt.getFrameRate());
    System.out.println("frame size (bytes): " + fmt.getFrameSize());
    System.out.println("sample rate (Hz): " + fmt.getSampleRate());
    System.out.println("sample size in bits: " + fmt.getSampleSizeInBits());
    System.out.println("is big endian: " + fmt.isBigEndian());
    System.out.println("properties: " + fmt.properties());

    int bytesPerSample = getBytesPerSample(audio);
    System.out.println("bytes per sample: " + bytesPerSample);

    System.out.println("available: " + audio.available());
    System.out.println("frame length: " + audio.getFrameLength());
  }

  // Convert `sample`, nominally in [-1,1], to the "decibel" measure
  // that Audacity uses.  Note that this does not preserve information
  // because the output does not indicate the sign of the input.
  public float linearToDecibels(float sample)
  {
    if (sample == 0.0f) {
      // `log10` is not defined on zero.  Use a very negative number of
      // decibels.  I've seen at least one place in Audacity that uses
      // the same value for a similar purpose.
      return -100.0f;
    }
    else {
      // Decibels are defined using the log of a ratio to a reference
      // level.  Here, the reference level is 1.
      //
      // Multiplying by 20 (rather than 10 as the name would suggest) is
      // related to the distinction between power and amplitude, but I
      // don't know the details.
      //
      return (float)(20.0 * Math.log10(Math.abs(sample)));
    }
  }

  public void printSamples(AudioInputStream audio, int maxSamples)
    throws IOException
  {
    float[] samples = getSamples(audio);

    System.out.println("read " + samples.length + " samples:");

    for (int i=0; i < maxSamples && i < samples.length; ++i) {
      float f = samples[i];
      float decibels = linearToDecibels(f);

      System.out.println("  sample " + i + ": " + f + "  \t" +
                         decibels + " dB");
    }
  }

  // Read the data into an array of samples, then write them back out
  // without any intervening processing.  This is meant, in part, to
  // check that doing no-op processing preserves the information.
  public void readWrite(AudioInputStream audio, String outFname)
    throws IOException
  {
    AudioFormat fmt = audio.getFormat();

    // Convert bytes into floats.
    float[] samples = getSamples(audio);

    // Convert floats back into bytes.
    int bytesPerSample = getBytesPerSample(audio);
    int numBytes = samples.length * bytesPerSample;
    byte[] bytes = new byte[numBytes];
    int numConvertedBytes =
      SimpleAudioConversion.encode(samples, bytes, samples.length, fmt);
    assert(numConvertedBytes == numBytes);

    // Wrap the bytes in streams to provide them.
    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
      try (AudioInputStream ais = new AudioInputStream(bais, fmt, bytes.length)) {

        // Write the output file.
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(outFname));
        System.out.println("wrote " + outFname);
      }
    }
  }

  public int secondsToSamples(AudioInputStream audio, float seconds)
  {
    AudioFormat fmt = audio.getFormat();

    return (int)(seconds * fmt.getSampleRate());
  }

  public float samplesToSeconds(AudioInputStream audio, int samples)
  {
    AudioFormat fmt = audio.getFormat();

    return (float)samples / fmt.getSampleRate();
  }

  // Attempt to identify discrete sounds in the input.
  public List<Sound> findSounds(
    // Audio to process.
    AudioInputStream audio,

    // Any sample this loud or louder (measured in decibels) will be
    // considered loud enough to anchor a sound.
    float loudnessThreshold_dB,

    // If two loud samples are this close (measured in seconds), then
    // they are considered to be part of the same sound.
    float closenessThreshold_s,

    // If a sound would be created but it is shorter than this duration
    // (measured in seconds), discard it.  If this is zero, then nothing
    // is discarded.
    float durationThreshold_s)

    throws IOException
  {
    float[] samples = getSamples(audio);

    // This code is intended to work correctly with multi-channel data,
    // but I haven't actually tested with more than one.
    AudioFormat fmt = audio.getFormat();
    float frameRate = fmt.getFrameRate();
    int numChannels = fmt.getChannels();
    int numFrames = samples.length / numChannels;

    int closenessThreshold_frames =
      (int)(closenessThreshold_s * frameRate);
    int durationThreshold_frames =
      (int)(durationThreshold_s * frameRate);

    // List of all discovered sounds.
    List<Sound> sounds = new ArrayList<Sound>();

    // Non-null if we have a current sound being accumulated.
    Sound curSound = null;

    for (int frameNum=0; frameNum < numFrames; ++frameNum) {
      // Get maximum loudness over all channels.
      float dB;
      {
        int sampleNum = frameNum * numChannels;
        dB = linearToDecibels(samples[sampleNum]);
        for (int c=1; c < numChannels; ++c) {
          dB = Math.max(dB, linearToDecibels(samples[sampleNum + c]));
        }
      }

      if (dB > loudnessThreshold_dB) {
        // Continue the current sound?
        if ((curSound != null) &&
            frameNum - curSound.m_endFrame <= closenessThreshold_frames) {
          curSound.extend(frameNum, dB);
        }

        else {
          if (curSound != null &&
              curSound.frameDuration() >= durationThreshold_frames) {
            // Emit the current sound.
            sounds.add(curSound);
          }

          // Start a new sound.
          curSound = new Sound(frameNum, frameNum, dB);
        }
      }
    }

    if (curSound != null &&
        curSound.frameDuration() >= durationThreshold_frames) {
      // Emit the final sound.
      sounds.add(curSound);
    }

    return sounds;
  }

  // Print the sounds that `findSounds` finds.
  public void printSounds(
    AudioInputStream audio,
    float loudnessThreshold_dB,
    float closenessThreshold_s,
    float durationThreshold_s) throws IOException
  {
    List<Sound> sounds = findSounds(audio,
      loudnessThreshold_dB,
      closenessThreshold_s,
      durationThreshold_s);

    for (Sound s : sounds) {
      s.printWithDuration(audio.getFormat().getFrameRate());
    }
  }

  // Silence everything but identified sounds that are at least
  // `durationThreshold_s` seconds long.
  public void declick(
    AudioInputStream audio,
    float loudnessThreshold_dB,
    float closenessThreshold_s,
    float durationThreshold_s)

    throws IOException
  {
    List<Sound> sounds = findSounds(audio,
      loudnessThreshold_dB,
      closenessThreshold_s,
      durationThreshold_s);

    // TODO
  }

  public void parseCommand(AudioInputStream audio, String command, String[] args)
    throws IOException
  {
    switch (command) {
      case "info":
        printInfo(audio);
        break;

      case "bytes":
        requireArgs(args, 1);
        printBytes(audio, Integer.valueOf(args[0]));
        break;

      case "samples":
        requireArgs(args, 1);
        printSamples(audio, Integer.valueOf(args[0]));
        break;

      case "copy":
        requireArgs(args, 1);
        readWrite(audio, args[0]);
        break;

      case "sounds":
        requireArgs(args, 3);
        printSounds(audio,
          Float.valueOf(args[0]),
          Float.valueOf(args[1]),
          Float.valueOf(args[2]));
        break;

      default:
        throw new RuntimeException(
          "Unknown command: " + StringUtil.doubleQuote(command));
    }
  }

  // If `args` has fewer than `numRequired` elements, throw a
  // `RuntimeException`.
  public void requireArgs(String args[], int numRequired)
  {
    if (args.length < numRequired) {
      throw new RuntimeException(
        "Command requires at least " + numRequired + " arguments.");
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

            bytes <N>
              Print up to N bytes of sample data.

            samples <N>
              Print up to N samples.

            copy <outFname>
              Copy the file by decoding then re-encoding the samples.

            sounds <loud_dB> <close_s> <duration_s>
              Report on the set of discrete sounds, where a "sound" has
              samples louder than <loud_dB> that are within <close_s>
              seconds of each other, and a total duration at least
              <duration_s> seconds.
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
