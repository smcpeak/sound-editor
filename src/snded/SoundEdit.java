// SoundEdit.java

package snded;

import util.StringUtil;
import util.Util;

import hedoluna.FFTbase;

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
  public void printBytes(AudioInputStream audio, int maxBytes)
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

  public void printInfo(AudioClip audio)
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

  public void printSamples(AudioClip audio, int maxSamples)
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
  public void copyToFile(AudioClip audio, String outFname)
    throws IOException
  {
    audio.writeToFile(outFname);
    System.out.println("wrote " + outFname);
  }

  // Attempt to identify discrete sounds in the input.
  public List<Sound> findSounds(
    // Audio to process.
    AudioClip audio,

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
  {
    // This code is intended to work correctly with multi-channel data,
    // but I haven't actually tested with more than one.
    float frameRate = audio.getFrameRate();
    int numChannels = audio.numChannels();
    long numFrames = audio.numFrames();

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
      double dB;
      {
        dB = audio.getFCDecibels(frameNum, 0);
        for (int c=1; c < numChannels; ++c) {
          dB = Math.max(dB, audio.getFCDecibels(frameNum, c));
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
    AudioClip audio,
    float loudnessThreshold_dB,
    float closenessThreshold_s,
    float durationThreshold_s)
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
  public void declick(
    AudioClip audio,
    String outFname,
    float loudnessThreshold_dB,
    float closenessThreshold_s,
    float durationThreshold_s)
      throws IOException
  {
    int closenessThreshold_frames =
      (int)(closenessThreshold_s * audio.getFrameRate());

    List<Sound> sounds = findSounds(audio,
      loudnessThreshold_dB,
      closenessThreshold_s,
      durationThreshold_s);

    Iterator<Sound> soundIter = sounds.iterator();

    // Sound we are closest to, or null if none.
    Sound curSound = (soundIter.hasNext()? soundIter.next() : null);

    // Sound after that one, if any.
    Sound nextSound = (soundIter.hasNext()? soundIter.next() : null);

    // Process all the frames in the clip.
    for (long frameNum=0; frameNum < audio.numFrames(); ++frameNum) {
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

  public void frequencyAnalysis(AudioClip audio, int windowSize)
  {
    // Compute the decibel spectrum.  The output has `windowSize/2`
    // elements.
    double[] decibels = powerSpectrumDecibels(audio, windowSize);

    // Print the frequency spectrum.
    System.out.printf("  freq       dB  dB stars\n");
    System.out.printf("------  -------  ----------\n");

    for (int i=0; i < decibels.length; ++i) {
      double elementFreq = spectrumElementFrequency(audio, i, windowSize);
      double dB = decibels[i];

      // Graphically represent the dB level as a number of stars,
      // where <= -100 is 0, (-100,-90] is 1, etc.
      int numStars = (int)Math.floor((dB + 110.0) / 10.0);
      String stars = (
        numStars > 0?
          "  " + "*".repeat(numStars) :
          ""
      );

      System.out.printf("%1$6.0f  %2$7.2f%3$s\n",
        elementFreq,
        dB,
        stars);
    }
  }

  public void frequencyAnalysisBins(AudioClip audio, int windowSize)
  {
    double[] decibels = powerSpectrumDecibels(audio, windowSize);

    // Find the maximum power within a logarithmic set of bins, one for
    // every factor of 10 Hz.
    int numFrequencyBins = 5;
    double[] maxDecibelsForBin = new double[numFrequencyBins];
    Arrays.fill(maxDecibelsForBin, -100.0);
    for (int i=0; i < decibels.length; ++i) {
      double elementFreq = spectrumElementFrequency(audio, i, windowSize);
      double dB = decibels[i];

      // Bin the frequencies as follows:
      //
      //   [    0,     10)   -> 0
      //   [   10,    100)   -> 1
      //   [  100,   1000)   -> 2
      //   [ 1000,  10000)   -> 3
      //   [10000, 100000)   -> 4
      //   other             -> 5 or more (discarded)
      //
      int binIndex = (int)Math.max(0, Math.floor(Math.log10(elementFreq)));
      if (binIndex < numFrequencyBins) {
        maxDecibelsForBin[binIndex] =
          Math.max(maxDecibelsForBin[binIndex], dB);
      }
    }

    // Print the resulting frequency distribution.
    System.out.println("binned frequency distribution:");
    for (int fbin=0; fbin < numFrequencyBins; ++fbin) {
      double upperFreq = Math.pow(10, fbin+1);

      System.out.printf("  up to %1$6.0f Hz: %2$8.3f dB max\n",
        upperFreq, maxDecibelsForBin[fbin]);
    }
  }

  // Return the frequency associated with spectrum element
  // `elementIndex` within a window of size `windowSize`.
  public double spectrumElementFrequency(
    AudioClip audio,
    int elementIndex,
    int windowSize)
  {
    // In a Fourier decomposition spectrum, each spectrum element is a
    // coefficient of a sinusoid with the following frequency, which
    // when all are combined recreates the original signal.
    return (double)elementIndex / (double)windowSize * audio.getFrameRate();
  }

  // Return an array of power values expressed linearly.  Element `i`
  // describes the power of the fourier component with frequency
  // `(i/windowSize)*frameRate`.
  //
  // `windowSize` must be a power of two.
  //
  // If the clip has fewer than `windowSize` samples, the output is all
  // zeroes.
  //
  // I'm not sure what units would be sensible here.  Power is energy
  // per time.  Time is abstracted away as the frame rate (which this
  // calculation does not depend on).  Energy is somehow abstracted away
  // as the unspecified units of the input samples.  So, I treat these
  // values as merely comparable as ratios to some other nominal
  // "maximum" power.
  //
  public double[] powerSpectrumLinear(AudioClip audio, int windowSize)
  {
    assert(windowSize > 1);

    double[] inputReal = new double[windowSize];
    double[] inputImag = new double[windowSize];     // All zeroes.

    // Initially all zeroes.
    double[] power = new double[windowSize / 2];

    // Work our way through the clip, analyzing `windowSize`-sized
    // chunks at a time, overlapping adjacent windows by half a window,
    // and accumulating the results in `power`.
    int numWindowEvaluations = 0;
    for (long startFrameNum = 0;
         startFrameNum + windowSize <= audio.numFrames();
         startFrameNum += windowSize / 2) {
      for (int channel = 0; channel < audio.numChannels(); ++channel) {
        // Copy the audio samples into `inputReal`.
        for (int i=0; i < windowSize; ++i) {
          long frameNum = startFrameNum + i;

          inputReal[i] = audio.getFCSample(frameNum, channel) *
                         windowFunction(i, windowSize);
        }

        // Apply FFT.  The result contains (real, imag) pairs
        // interleaved.
        //
        // TODO: Modify `fft` to not allocate a new output array on
        // every call.
        //
        double[] output = FFTbase.fft(inputReal, inputImag, true /*direct*/);

        // Accumulate the output power.
        for (int i=0; i < power.length; ++i) {
          // The power is computed as the square of the magnitude of the
          // amplitude.  (I'm not sure what the mathematical
          // justification for this is.)
          power[i] += complexMagnitudeSquared(output[i*2], output[i*2 + 1]);
        }

        ++numWindowEvaluations;
      }
    }

    if (numWindowEvaluations > 0) {
      // Divide by the value that a constant 1.0 input signal would have
      // after multiplying by the window function and adding all of the
      // resulting elements.  (The division itself happens inside
      // `windowScaleFactor`, so in this function, we treat it as a
      // multiplier.)
      double scale = windowScaleFactor(windowSize);

      // Divide by the number of window evaluations used because each
      // one contributed additively to the combined `power` array.
      scale /= numWindowEvaluations;

      // Apply the scale factor to the entire output array.
      for (int i=0; i < power.length; ++i) {
        power[i] *= scale;
      }
    }

    return power;
  }

  // Compute a frequency power spectrum with the results expressed in
  // decibels.
  public double[] powerSpectrumDecibels(AudioClip audio, int windowSize)
  {
    // Compute the power spectrum.  The output has `windowSize/2`
    // elements.
    double[] power = powerSpectrumLinear(audio, windowSize);

    // Convert power to decibels.
    double[] decibels = new double[power.length];
    {
      for (int i=0; i < power.length; ++i) {
        decibels[i] = AudioClip.linearPowerToDecibels(power[i]);
      }
    }

    return decibels;
  }

  // Return a factor by which to scale a sample depending on where it is
  // in the window to be analyzed.
  private double windowFunction(int frameNum, int windowSize)
  {
    // Hann window, which is one cycle of cosine, shifted so it just
    // meets zero at the endpoints, and peaks at 1 in the middle of the
    // window.
    return 0.5 * (1 - Math.cos(2 * Math.PI * frameNum / windowSize));
  }

  // Return a number we can multiply by the FFT-computed power output
  // values to normalize them such that an input amplitude of 1.0 would
  // be reported as 0 dB.  That is, this returns what we think the power
  // of a 1.0 signal would be.
  private double windowScaleFactor(int windowSize)
  {
    // Sum of all window factors, i.e., what the sum would be of a 1.0
    // signal multiplied by the window.  FFT is computing an analogous
    // sum internally for each frequency.
    double sumOfWindowFactors = 0;
    for (int i=0; i < windowSize; ++i) {
      sumOfWindowFactors += windowFunction(i, windowSize);
    }

    // Second part of Audacity's mysterious scaling factor `wss`.
    double totalWindowScaleFactor = 1;
    if (sumOfWindowFactors > 0) {
      // Squaring the sum makes sense because we compute the square of
      // the magnitude to get power.
      //
      // I think the 4.0 arises because we ignore the upper half of the
      // output array, meaning we only get half of the total amplitude,
      // and hence a quarter of the power, that we would if we used the
      // entire output.
      //
      totalWindowScaleFactor = 4.0 / (sumOfWindowFactors * sumOfWindowFactors);
    }

    return totalWindowScaleFactor;
  }

  // Return the squared magnitude of complex number (R,I).
  //
  // I was using the square root here, but the Wikipedia article on
  // short-time Fourier transform seems to indicate I want the square of
  // the magnitude.
  //
  private double complexMagnitudeSquared(double R, double I)
  {
    return R*R + I*I;
  }

  public void parseCommand(AudioClip audio, String command, String[] args)
    throws IOException
  {
    switch (command) {
      case "info":
        printInfo(audio);
        break;

      case "samples":
        requireArgs(args, 1);
        printSamples(audio, Integer.valueOf(args[0]));
        break;

      case "copy":
        requireArgs(args, 1);
        copyToFile(audio, args[0]);
        break;

      case "sounds":
        requireArgs(args, 3);
        printSounds(audio,
          Float.valueOf(args[0]),
          Float.valueOf(args[1]),
          Float.valueOf(args[2]));
        break;

      case "declick":
        requireArgs(args, 4);
        declick(audio,
          args[0],
          Float.valueOf(args[1]),
          Float.valueOf(args[2]),
          Float.valueOf(args[3]));
        break;

      case "freq":
        frequencyAnalysis(audio, 1024);
        break;

      case "freqBins":
        frequencyAnalysisBins(audio, 1024);
        break;

      default:
        throw new RuntimeException(
          "Unknown command: " + StringUtil.doubleQuote(command));
    }
  }

  // If `args` has fewer than `numRequired` elements, throw a
  // `RuntimeException`.
  public static void requireArgs(String args[], int numRequired)
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

            declick <outFname> <loud_dB> <close_s> <duration_s>
              Silence everything that "sounds" does not report.

            freq
              Print frequency spectrum.

            freqBins
              Bin the frequency spectrum at 10x logarithmic intervals.

          """);
        System.exit(2);
      }

      String fname = args[0];
      String command = args[1];
      String[] cmdArgs = Arrays.copyOfRange(args, 2, args.length);

      try (AudioInputStream ais = AudioSystem.getAudioInputStream(new File(fname))) {
        // The "bytes" command is special because it operates on the
        // stream directly.
        if (command.equals("bytes")) {
          requireArgs(cmdArgs, 1);
          se.printBytes(ais, Integer.valueOf(cmdArgs[0]));
        }
        else {
          // All other commands operate on the clip.
          AudioClip audio = new AudioClip(ais);
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
