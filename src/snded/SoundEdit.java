// SoundEdit.java

package snded;

import util.StringUtil;
import util.Util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


// Simple sound editor.
public class SoundEdit {
  // This does not use the `AudioClip` class because it directly
  // accesses the bytes, not the decoded samples.
  private void printBytes(AudioInputStream audio, int maxBytes)
    throws IOException
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

  private void printInfo(AudioClip audio)
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

    System.out.println("bytes per sample: " + audio.bytesPerSample());

    System.out.println("num frames: " + audio.numFrames());
    System.out.println("num samples: " + audio.numSamples());
  }

  private void printSamples(AudioClip audio, int maxSamples)
  {
    System.out.println("read " + audio.numSamples() + " samples:");

    for (long i=0; i < maxSamples && i < audio.numSamples(); ++i) {
      float f = audio.getSample(i);
      double decibels = AudioClip.linearAmplitudeToDecibels(f);

      System.out.println("  sample " + i + ": " + f + "  \t" +
                         decibels + " dB");
    }
  }

  // Write the samples back out to a WAV file without any intervening
  // processing.  This is meant, in part, to check that doing no-op
  // processing preserves the information.
  private void copyToFile(AudioClip audio, String outFname)
    throws IOException
  {
    progressReport("writing " + outFname);
    audio.writeToFile(outFname);
    System.out.println("wrote " + outFname);
  }

  // Attempt to identify discrete sounds in the input.
  private List<Sound> findSounds(
    AudioClip audio,
    SoundPartitionParams params)
  {
    // This code is intended to work correctly with multi-channel data,
    // but I haven't actually tested with more than one.
    float frameRate = audio.getFrameRate();
    int numChannels = audio.numChannels();
    long numFrames = audio.numFrames();

    int closenessThreshold_frames =
      (int)(params.m_closenessThreshold_s * frameRate);

    // Report progress every this many frames.
    long progressPeriod_frames =
      (long)(60.0 * audio.getFrameRate());

    // List of all discovered sounds.
    List<Sound> sounds = new ArrayList<Sound>();

    // Non-null if we have a current sound being accumulated.
    Sound curSound = null;

    for (int frameNum=0; frameNum < numFrames; ++frameNum) {
      if (frameNum % progressPeriod_frames == 0) {
        progressReport("findSounds: processing frame " + frameNum +
                       " of " + audio.numFrames());
      }

      // Get maximum loudness over all channels.
      double dB;
      {
        dB = audio.getFCDecibels(frameNum, 0);
        for (int c=1; c < numChannels; ++c) {
          dB = Math.max(dB, audio.getFCDecibels(frameNum, c));
        }
      }

      if (dB > params.m_loudnessThreshold_dB) {
        // Continue the current sound?
        if ((curSound != null) &&
            frameNum - curSound.m_endFrame <= closenessThreshold_frames) {
          curSound.extend(frameNum, dB);
        }

        else {
          if (curSound != null) {
            // Emit the current sound.
            sounds.add(curSound);
          }

          // Start a new sound.
          curSound = new Sound(frameNum, frameNum, dB);
        }
      }
    }

    if (curSound != null) {
      // Emit the final sound.
      sounds.add(curSound);
    }

    // Calculate the power spectra.
    {
      int totalSounds = sounds.size();
      int curSoundNum = 0;

      for (Sound s : sounds) {
        if (curSoundNum % 100 == 0) {
          progressReport("spectra: analyzing sound " + curSoundNum +
                         " of " + totalSounds);
        }

        s.m_powerSpectrum =
          new PowerSpectrum(audio, 1024, s.m_startFrame, s.m_endFrame);
        s.m_binnedPowerSpectrum =
          new BinnedPowerSpectrum(s.m_powerSpectrum);

        ++curSoundNum;
      }
    }

    return sounds;
  }

  // Print the sounds that `findSounds` finds.
  private void printSounds(
    AudioClip audio,
    SoundPartitionParams params,
    SoundClassifier classifier)
  {
    List<Sound> sounds = findSounds(audio, params);

    // Here, we do not use the spectrum because I want to see all of
    // the detected sounds (of sufficient duration) and the retention
    // decision in order to evaluate the retention criteria.
    sounds = filterSounds(sounds,
      audio, classifier, false /*useSpectrum*/);

    for (Sound s : sounds) {
      s.printWithDuration(audio.getFormat().getFrameRate(), classifier);
    }
  }

  // Filter `origSounds`, returning only those that `classifier` says
  // to retain.
  private List<Sound> filterSounds(
    List<Sound> origSounds,
    AudioClip audio,
    SoundClassifier classifier,
    boolean useSpectrum)
  {
    List<Sound> ret = new ArrayList<Sound>();

    for (Sound s : origSounds) {
      if (classifier.shouldRetain(s, audio.getFrameRate(), useSpectrum)) {
        ret.add(s);
      }
    }

    return ret;
  }

  // Silence everything but identified sounds that are at least
  // `durationThreshold_s` seconds long.
  //
  // Whenever a frame is within the endpoints of a sound, or within
  // `closenessThreshold_s/2` of an endpoint, it is kept at its full
  // amplitude.
  //
  // Whenever a frame is more than `closenessThreshold_s` away from an
  // endpoint, it is silenced entirely.
  //
  // In between those, meaning the distance to an endpoint is between
  // `closenessThreshold_s` and `closenesThreshold_s/2`, the samples in
  // the frame are scaled linearly.
  //
  private void declick(
    AudioClip audio,
    String outFname,
    SoundPartitionParams params,
    SoundClassifier classifier)
      throws IOException
  {
    int closenessThreshold_frames =
      (int)(params.m_closenessThreshold_s * audio.getFrameRate());

    // Report progress every this many frames.
    long progressPeriod_frames =
      (long)(60.0 * audio.getFrameRate());

    List<Sound> sounds = findSounds(audio, params);

    sounds = filterSounds(sounds,
      audio, classifier, true /*useSpectrum*/);

    Iterator<Sound> soundIter = sounds.iterator();

    // Sound we are closest to, or null if none.
    Sound curSound = (soundIter.hasNext()? soundIter.next() : null);

    // Sound after that one, if any.
    Sound nextSound = (soundIter.hasNext()? soundIter.next() : null);

    // Process all the frames in the clip.
    for (long frameNum=0; frameNum < audio.numFrames(); ++frameNum) {
      if (frameNum % progressPeriod_frames == 0) {
        progressReport("declick: processing frame " + frameNum +
                       " of " + audio.numFrames());
      }

      // Advance to next sound?
      if (nextIsCloser(curSound, nextSound, frameNum)) {
        // Yes.
        curSound = nextSound;
        nextSound = (soundIter.hasNext()? soundIter.next() : null);
      }

      // Amount by which to amplify the samples in this frame.
      float amplification;

      if (curSound != null) {
        long distance = curSound.distanceToEndpoint(frameNum);
        if (distance < closenessThreshold_frames/2) {
          // Retain full amplitude.
          amplification = 1.0f;
        }
        else if (distance > closenessThreshold_frames) {
          // Silence entirely.
          amplification = 0.0f;
        }
        else {
          // Scale linearly.
          amplification =
            ((closenessThreshold_frames - distance) * 2) /
            (float)closenessThreshold_frames;
        }
      }
      else {
        // Not near a sound, silence.
        amplification = 0.0f;
      }

      for (int c=0; c < audio.numChannels(); ++c) {
        audio.setFCSample(frameNum, c,
          audio.getFCSample(frameNum, c) * amplification);
      }
    }

    // Write the result to the specified file.
    copyToFile(audio, outFname);
  }

  // Helper for `declick`.
  private boolean nextIsCloser(Sound curSound, Sound nextSound, long frameNum)
  {
    return
      curSound != null &&
      nextSound != null &&
      curSound.distanceToEndpoint(frameNum) > nextSound.distanceToEndpoint(frameNum);
  }

  private void frequencyAnalysis(AudioClip audio, int windowSize)
  {
    // Compute the power spectrum.
    PowerSpectrum ps = new PowerSpectrum(audio, windowSize);

    // Print the frequency spectrum.
    System.out.printf("  freq       dB  dB stars\n");
    System.out.printf("------  -------  ----------\n");

    for (int i=0; i < ps.numElements(); ++i) {
      double freq = ps.getFrequency(i);
      double dB = ps.getDecibels(i);

      // Graphically represent the dB level as a number of stars,
      // where <= -100 is 0, (-100,-90] is 1, etc.
      int numStars = (int)Math.floor((dB + 110.0) / 10.0);
      String stars = (
        numStars > 0?
          "  " + "*".repeat(numStars) :
          ""
      );

      System.out.printf("%1$6.0f  %2$7.2f%3$s\n",
        freq,
        dB,
        stars);
    }
  }

  private void frequencyAnalysisBins(AudioClip audio, int windowSize)
  {
    PowerSpectrum ps = new PowerSpectrum(audio, windowSize);
    new BinnedPowerSpectrum(ps).printBins();
  }

  // Command line help string.
  private static final String usageString =
    """
    usage: snded <file.wav> <command> [<params>]

    The <params> are a sequence of <name>:<value> pairs in any
    order.  Some have default values, indicated in parentheses,
    making them optional.

    commands:

      info

        Print some details about the given file.

      bytes [max:int(10)]

        Print up to <max> bytes of sample data.

      samples [max:int(10)]

        Print up to <max> samples.

      copy [out:string]

        Copy the file by decoding then re-encoding the samples.

      sounds [loud_dB:float(-40)] [close_s:float(0.2)]
             [duration_s:float(0.09)] [maxClick_s:float(0.2)]

        Report on the set of discrete sounds, where a "sound" has
        samples louder than <loud_dB> that are within <close_s>
        seconds of each other, and a total duration at least
        <duration_s> seconds.

        Furthermore, if the duration is between <duration_s> and
        <maxClick_s>, then heuristically indicate whether to retain
        based on a frequency analysis, discarding high-frequency sounds.

      declick [out:string]
              [loud_dB:float(-40)] [close_s:float(0.2)]
              [duration_s:float(0.09)] [maxClick_s:float(0.2)]

        This is the main capability of this tool.

        Silence everything that "sounds" either does not report, or
        reports with "retain: false".  Write the modified output to
        <out> (a WAV file).

      freq [windowSize:int(1024)]

        Print frequency spectrum.

      freqBins [windowSize:int(1024)]

        Bin the frequency spectrum at 10x logarithmic intervals.

    """;

  private void parseCommand(AudioClip audio, String command, String[] args)
    throws IOException
  {
    // Parse the argument as "<name>:<value>" pairs.
    ArgMap argMap = new ArgMap(args);

    switch (command) {
      case "info":
        printInfo(audio);
        break;

      case "samples":
        printSamples(audio,
          argMap.getInt("max", 10));
        break;

      case "copy":
        copyToFile(audio,
          argMap.getRequiredString("out"));
        break;

      case "sounds":
        printSounds(audio,
          new SoundPartitionParams(argMap),
          new SoundClassifier(argMap));
        break;

      case "declick":
        declick(audio,
          argMap.getRequiredString("out"),
          new SoundPartitionParams(argMap),
          new SoundClassifier(argMap));
        break;

      case "freq":
        frequencyAnalysis(audio,
          argMap.getInt("windowSize", 1024));
        break;

      case "freqBins":
        frequencyAnalysisBins(audio,
          argMap.getInt("windowSize", 1024));
        break;

      default:
        throw new RuntimeException(
          "Unknown command: " + StringUtil.doubleQuote(command));
    }
  }

  // Print a progress report to stderr saying what the program is doing.
  private static void progressReport(String info)
  {
    System.err.println(info);
  }

  public static void main(String args[])
  {
    SoundEdit se = new SoundEdit();
    try {
      if (args.length < 2) {
        System.err.print(usageString);
        System.exit(2);
      }

      String fname = args[0];
      String command = args[1];
      String[] cmdArgs = Arrays.copyOfRange(args, 2, args.length);

      try (AudioInputStream ais = AudioSystem.getAudioInputStream(new File(fname))) {
        // The "bytes" command is special because it operates on the
        // stream directly.
        if (command.equals("bytes")) {
          ArgMap argMap = new ArgMap(cmdArgs);
          se.printBytes(ais,
            argMap.getInt("max", 10));
        }
        else {
          // All other commands operate on the clip.
          progressReport("reading " + fname);
          AudioClip audio = new AudioClip(ais);
          progressReport("finished reading " + fname);
          se.parseCommand(audio, command, cmdArgs);
        }
      }
    }
    catch (Exception e) {
      System.err.println(Util.getExceptionMessage(e));
      System.exit(2);
    }
  }
}

// EOF
