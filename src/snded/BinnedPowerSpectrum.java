// BinnedPowerSpectrum.java

package snded;

import java.util.Arrays;


// Store the result of binning power samples and taking the maximum
// within each bin.
public class BinnedPowerSpectrum {
  // -------------------------- Private data ---------------------------
  // Number of bins.
  private int m_numBins;

  // Array of `m_numBins` elements containing the maximum decibels of
  // the power of frequencies within the associated range.  The minimum
  // value is somewhat arbitrarily -100 dB.
  private double[] m_maxDecibelsForBin;

  // Ratio of max power in [100 Hz, 1000 Hz] to max power in [1000 Hz,
  // 10000 Hz].
  private double m_excessLow_dB;

  // True if we heuristically think this spectrum corresponds to a
  // click rather than voice.  This heuristic only works for very
  // short sounds (less than 0.2 s).
  private boolean m_likelyClick;

  // ------------------------- Public methods --------------------------
  // Bin the results from `ps`.
  public BinnedPowerSpectrum(PowerSpectrum ps)
  {
    m_numBins = 5;

    m_maxDecibelsForBin = new double[m_numBins];
    Arrays.fill(m_maxDecibelsForBin, -100.0);

    for (int i=0; i < ps.numElements(); ++i) {
      double freq = ps.getFrequency(i);
      double dB = ps.getDecibels(i);

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
      if (binIndex < m_numBins) {
        m_maxDecibelsForBin[binIndex] =
          Math.max(m_maxDecibelsForBin[binIndex], dB);
      }
    }

    // below 1000 Hz
    double lowFreqMax_dB = getMaxForBin(2);

    // above 1000 Hz
    double highFreqMax_dB = getMaxForBin(3);

    // Ratio of those two.  My hypothesis is that, for very short
    // sounds (less than 0.2 s), I can use this to distinguish between
    // voice and clicks, the latter having a negative value due to
    // more high-frequency power.
    m_excessLow_dB = lowFreqMax_dB - highFreqMax_dB;

    m_likelyClick = (m_excessLow_dB < 0);
  }

  // Return the measured maximum decibels for `bin`.
  public double getMaxForBin(int bin)
  {
    return m_maxDecibelsForBin[bin];
  }

  public double getExcessLow_dB()
  {
    return m_excessLow_dB;
  }

  public boolean getLikelyClick()
  {
    return m_likelyClick;
  }

  // Print the computed bins to stdout.
  public void printBins()
  {
    // Print the resulting frequency distribution.
    System.out.println("  binned frequency distribution:");
    for (int fbin=0; fbin < m_numBins; ++fbin) {
      double upperFreq = Math.pow(10, fbin+1);

      System.out.printf("    up to %1$6.0f Hz: %2$8.3f dB max\n",
        upperFreq, m_maxDecibelsForBin[fbin]);
    }
  }
}


// EOF
