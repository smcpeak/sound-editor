// SoundClassifier.java

package snded;


// Class to heuristically decide whether a given sound should be kept or
// discarded as part of click removal.
public class SoundClassifier {
  // --------------------------- Public data ---------------------------
  // A sound must contain two loud samples at least this far apart.
  public float m_minDuration_s = 0.09f;

  // Any sound with loud samples this far apart is not a click, simply
  // due to its duration.
  public float m_maxClickDuration_s = 0.2f;

  // ------------------------- Public methods --------------------------
  // Initialize to defaults.
  public SoundClassifier()
  {}

  // Initialize to defaults, as overridden by `argMap`.
  public SoundClassifier(ArgMap argMap)
  {
    m_minDuration_s = argMap.getFloat("duration_s",
      m_minDuration_s);

    m_maxClickDuration_s = argMap.getFloat("maxClick_s",
      m_maxClickDuration_s);
  }

  // Should we retain `sound`?  Returns false for sounds that are deemed
  // to be clicks.  Takes the spectrum into account if `useSpectrum` is
  // true and a spectrum has already been computed.
  public boolean shouldRetain(
    Sound sound, double frameRate, boolean useSpectrum)
  {
    assert(sound != null);

    double dur_s = sound.timeDuration(frameRate);
    if (dur_s < m_minDuration_s) {
      // Too short.
      return false;
    }

    if (dur_s >= m_maxClickDuration_s) {
      // Long enough that we want to retain regardless of spectrum.
      return true;
    }

    if (useSpectrum && sound.m_binnedPowerSpectrum != null) {
      return !sound.m_binnedPowerSpectrum.getLikelyClick();
    }
    else {
      // With no spectrum to use, err on the side of retention.
      return true;
    }
  }
}

// EOF
