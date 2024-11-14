// PowerSpectrum.java

package snded;

import hedoluna.FFTbase;

import java.util.Arrays;


// Spectrum of relative power per frequency.
//
// Given an audio clip (a sequence of samples) and a window size of N
// samples, this class computes and records the power associated with
// N/2 frequencies, ranging from 0 (constant signal) to N/2 cycles per
// whatever period N samples represents.  For example, if the sample
// rate is 48kHz and the window size is 1024, then spectrum element 1
// represents 1 cycle per 1024 samples or 1/1024*48kHz = 46.875 Hz, and
// spectrum element 512 represents 512/1024*48kHz = 24 kHz.
//
// The power is expressed as decibels relative to the power that a
// constant sinusoid would have when measured at its frequency (that is,
// such a signal would have 0 dB at that frequency).  Since that is the
// maximum, all decibel values in this spectrum are non-positive.
//
// Measurement is performed by summing the results of measuring multiple
// window-sized chunks.  This class uses a Hann (cosine) window.
//
public class PowerSpectrum {
  // -------------------------- Private data ---------------------------
  // Number of samples in a measurement window.  Increasing this value
  // yields greater frequency resolution (since the spectrum has more
  // elements) but less temporal resolution (since the spectrum is taken
  // over a larger number of samples).
  //
  // Additionally, larger windows require more time to analyze.  The FFT
  // algorithm used here (like virtually all others) is O(N log N),
  // where N is the window size, so increasing the window size will
  // increase the spectral analysis running time logarithmically
  // (considering that a larger window will be applied proportionately
  // fewer times).
  //
  // This must be at least 2, and a power of 2 (the latter is a
  // requirement of the FFT algorithm used here; other FFT algorithms
  // can handle more diverse window sizes, although there are always
  // restrictions).
  //
  // A typical value is 1024.
  //
  private int m_windowSize;

  // For each of `m_windowSize/2` elements, the relative power of the
  // signal at the associated frequency.  The minimum relative power is
  // set at -100 dB, somewhat arbitrarily.
  //
  // It may be that this array contains all -100 dB values, either
  // because the original signal was a constant zero, or because the
  // clip is shorter than the window size.
  //
  // Note: Adding these values together (after linearization) does *not*
  // yield the total signal power (nor anything proportional to it).
  // Fourier decomposition yields complex numbers that have been reduced
  // to their magnitude as part of computing this spectrum.  The full
  // decomposition has some mutual destructive interference that reduces
  // the total signal power, but information about that interference
  // (carried in the phase of the coefficients) is not preserved in this
  // spectrum.
  //
  // One should regard this array as (like the input) consisting of a
  // sequence of *samples* of an underlying continuous function (namely,
  // the square of the magnitude of the continuous Fourier Transform of
  // the continuous counterpart of the input signal).  Consequently, to
  // get the power at a frequency not explicitly measured, one can (as a
  // good approximation) interpolate the values of nearby samples.
  //
  private double[] m_decibelsPerElement;

  // Nominal number of samples per second within a channel, e.g., 48000
  // for 48 kHz.  This is copied from the audio clip so we can report
  // data in units of time (rather than frames) without asking for the
  // audio clip each time.
  private float m_frameRate;

  // ------------------------- Public methods --------------------------
  // Calculate the spectrum of `audio` using `windowSize`.
  public PowerSpectrum(AudioClip audio, int windowSize)
  {
    this(
      audio,
      windowSize,
      audio.getFirstFrameIndex(),
      audio.getLastFrameIndex());
  }

  // Measure `audio` within the specified inclusive range.
  public PowerSpectrum(
    AudioClip audio,
    int windowSize,
    long startFrame,
    long endFrame)
  {
    assert(audio != null);
    assert(windowSize >= 2);

    m_windowSize = windowSize;
    m_decibelsPerElement = new double[windowSize / 2];
    m_frameRate = audio.getFrameRate();

    computeSpectrum(audio, startFrame, endFrame);
  }

  // Number of elements in the spectrum.
  public int numElements()
  {
    return m_decibelsPerElement.length;
  }

  // Return the relative power of the given element, in decibels.
  public double getDecibels(int elementIndex)
  {
    return m_decibelsPerElement[elementIndex];
  }

  // Return the frequency associated with spectrum element
  // `elementIndex`.
  public double getFrequency(int elementIndex)
  {
    // In a Fourier decomposition spectrum, each spectrum element is a
    // coefficient of a sinusoid with the following frequency, which
    // when all are combined recreates the original signal.
    return (double)elementIndex / (double)m_windowSize * m_frameRate;
  }

  // Print to stdout the maximum relateive power within a logarithmic
  // series of frequency bins.
  //
  // TODO: This doesn't belong here.  This is a temporary home.
  //
  public void printBinnedFrequencyMaxima()
  {
    // Find the maximum power within a logarithmic set of bins, one for
    // every factor of 10 Hz.
    int numFrequencyBins = 5;
    double[] maxDecibelsForBin = new double[numFrequencyBins];
    Arrays.fill(maxDecibelsForBin, -100.0);
    for (int i=0; i < numElements(); ++i) {
      double freq = getFrequency(i);
      double dB = getDecibels(i);

      // Bin the frequencies as follows:
      //
      //   [    0,     10)   -> 0
      //   [   10,    100)   -> 1
      //   [  100,   1000)   -> 2
      //   [ 1000,  10000)   -> 3
      //   [10000, 100000)   -> 4
      //   other             -> 5 or more (discarded)
      //
      int binIndex = (int)Math.max(0, Math.floor(Math.log10(freq)));
      if (binIndex < numFrequencyBins) {
        maxDecibelsForBin[binIndex] =
          Math.max(maxDecibelsForBin[binIndex], dB);
      }
    }

    // Print the resulting frequency distribution.
    System.out.println("  binned frequency distribution:");
    for (int fbin=0; fbin < numFrequencyBins; ++fbin) {
      double upperFreq = Math.pow(10, fbin+1);

      System.out.printf("    up to %1$6.0f Hz: %2$8.3f dB max\n",
        upperFreq, maxDecibelsForBin[fbin]);
    }
  }

  // ------------------------- Private methods -------------------------
  // Do the actual work of computing the spectrum.  This is separated
  // from the constructor so that the heavy computation does not clutter
  // the public part of the class.
  private void computeSpectrum(
    AudioClip audio, long startFrameNum, long endFrameNum)
  {
    double[] inputReal = new double[m_windowSize];
    double[] inputImag = new double[m_windowSize];     // All zeroes.

    // Linear relative power.  Initially all zeroes.
    double[] power = new double[m_windowSize / 2];

    // Amount by which to move the window between measurements.
    // Increasing this value may improve accuracy at the cost of
    // additional CPU time.  It also biases the results away from the
    // ends of the sample, since only whole windows can be measured, so
    // relatively more measurements would be taken of the middle
    // sections.
    //
    // Using half of the window size seems to be conventional, and is
    // what Audacity does.
    //
    final int windowIncrement = m_windowSize / 2;

    // Total number of times we measured and accumulated the spectrum of
    // a single window-sized chunk of input.
    int numWindowEvaluations = 0;

    // Work our way through the specified section of the clip, analyzing
    // window-sized chunks at a time, overlapping adjacent windows by
    // `windowIncrement`, and accumulating the results in `power`.
    for (long curFrameNum = startFrameNum;
         curFrameNum + m_windowSize - 1 <= endFrameNum;
         curFrameNum += windowIncrement) {
      // Perform measurements on every channel.
      for (int channel = 0; channel < audio.numChannels(); ++channel) {
        // Copy the audio samples into `inputReal`.
        for (int i=0; i < m_windowSize; ++i) {
          long frameNum = curFrameNum + i;

          inputReal[i] = audio.getFCSample(frameNum, channel) *
                         windowFunction(i);
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
      double scale = windowScaleFactor();

      // Divide by the number of window evaluations used because each
      // one contributed additively to the combined `power` array.
      scale /= numWindowEvaluations;

      // Convert power to decibels.
      for (int i=0; i < power.length; ++i) {
        // Apply the scale factor to every element as we convert.
        m_decibelsPerElement[i] =
          AudioClip.linearPowerToDecibels(power[i] * scale);
      }
    }
  }

  // Return the squared magnitude of complex number (R,I).
  //
  // I was using the square root here, but the Wikipedia article on
  // short-time Fourier transform seems to indicate I want the square of
  // the magnitude.
  //
  private static double complexMagnitudeSquared(double R, double I)
  {
    return R*R + I*I;
  }

  // Return a factor by which to scale a sample depending on where it is
  // in the window to be analyzed.
  //
  // The primary purpose of the window function is to reduce "spectral
  // leakage", a phenomenon where an abrupt cutoff at the ends induces
  // high-frequency components that are not present in the full original
  // signal.  In most practical applications, smoothly tapering the ends
  // eliminates the leakage.
  //
  private double windowFunction(int frameNum)
  {
    // Hann window, which is one cycle of cosine, shifted so it just
    // meets zero at the endpoints, and peaks at 1 in the middle of the
    // window.
    return 0.5 * (1 - Math.cos(2 * Math.PI * frameNum / m_windowSize));
  }

  // Return a number we can multiply by the FFT-computed power output
  // values to normalize them such that an input amplitude of 1.0 would
  // be reported as 0 dB.  That is, this returns what we think the power
  // of a 1.0 signal would be.
  private double windowScaleFactor()
  {
    // Sum of all window factors, i.e., what the sum would be of a 1.0
    // signal multiplied by the window.  FFT is computing an analogous
    // sum internally for each frequency.
    double sumOfWindowFactors = 0;
    for (int i=0; i < m_windowSize; ++i) {
      sumOfWindowFactors += windowFunction(i);
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
}


// EOF
