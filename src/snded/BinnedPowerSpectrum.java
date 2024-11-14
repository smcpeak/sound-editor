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
