// Sound.java

package snded;


// A segment of audio that has sufficient loudness to be considered a
// discrete sound within the context of an analysis and transformation
// that deals with sounds in a longer clip.
public class Sound {
  // ---- public data ----
  // The starting frame number of the sound.  This is the first frame
  // whose loudness exceeds some threshold.
  public long m_startFrame;

  // The ending frame number.  This is the last loud frame (for a
  // while).  It might be the same as `soundStartFrame`.
  public long m_endFrame;

  // The maximum loudness of any frame in the segment, in decibels.
  public double m_maxLoudness_dB;

  // Power per frequency.  Might be null if not computed.
  public PowerSpectrum m_powerSpectrum;

  // ---- public methods ----
  public Sound(long startFrame, long endFrame, double maxLoudness_dB)
  {
    m_startFrame = startFrame;
    m_endFrame = endFrame;
    m_maxLoudness_dB = maxLoudness_dB;
    m_powerSpectrum = null;
  }

  // Number of frames in this sound.  Always positive.
  public long frameDuration()
  {
    return m_endFrame - m_startFrame + 1;
  }

  // Extend the segment to `endFrame`, incorporating `loudness_dB` into
  // the maximum loudness.
  public void extend(long endFrame, double loudness_dB)
  {
    assert(endFrame >= m_endFrame);
    m_endFrame = endFrame;
    m_maxLoudness_dB = Math.max(m_maxLoudness_dB, loudness_dB);
  }

  // Return the distance from `frameNum` to the nearest endpoint of this
  // sound.  Returns 0 if the frame is within the sound bounds.  The
  // result is never negative.
  public long distanceToEndpoint(long frameNum)
  {
    if (frameNum < m_startFrame) {
      return m_startFrame - frameNum;
    }
    else if (frameNum > m_endFrame) {
      return frameNum - m_endFrame;
    }
    else {
      return 0;
    }
  }

  // Print sound details, including duration in seconds.
  public void printWithDuration(float frameRate)
  {
    assert(frameRate > 0);
    float duration_s =
      (float)frameDuration() / frameRate;

    System.out.println("sound{");

    System.out.println(
      "  interval_s: (" + framesToTimeString(m_startFrame, frameRate) +
      " " + framesToTimeString(m_endFrame, frameRate) + ")");

    System.out.println(
      "  duration_s: " + framesToTimeString(frameDuration(), frameRate));

    System.out.format(
      "  maxLoudness_dB: %1$.3f\n", m_maxLoudness_dB);

    if (m_powerSpectrum != null) {
      BinnedPowerSpectrum bps = new BinnedPowerSpectrum(m_powerSpectrum);

      System.out.format(
        "  excessLow_dB: %1$.3f\n", bps.getExcessLow_dB());

      System.out.format(
        "  likelyClick: %1$b\n", bps.getLikelyClick());
    }

    System.out.println("}");
  }

  // Return `frame` expressed as seconds, as a string.
  private String framesToTimeString(long frames, float frameRate)
  {
    // Convert to seconds.
    float duration_s = frames / frameRate;

    return String.format("%1$.3f", duration_s);
  }
}


// EOF
