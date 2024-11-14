// SoundPartitionParams.java

package snded;


// Class with the parameters used to partition an input audio clip into
// discrete sounds that can then be individually assessed as part of
// click removal.
public class SoundPartitionParams {
  // --------------------------- Public data ---------------------------
  // A sound must contain at least one sample whose amplitude (relative
  // to 1.0) is at least this threshold.  We call such a sample "loud".
  public float m_loudnessThreshold_dB = -40.0f;

  // If two loud samples are at least this close together in time, then
  // they will be regarded as part of the same sound.
  //
  // This value is also used when performing click reduction, as the
  // "attack" and "decay" periods of volume fade-in and fade-out are
  // half this length, and inside those, we have another period of
  // full-volume that is half this length surrounding the first and
  // last loud frame.
  //
  // TODO: Attack/decay time should probably be split out.
  //
  public float m_closenessThreshold_s = 0.2f;

  // ------------------------- Public methods --------------------------
  // Initialize to defaults.
  public SoundPartitionParams()
  {}

  // Initialize to defaults as overridden by what is in `argMap`.
  public SoundPartitionParams(ArgMap argMap)
  {
    m_loudnessThreshold_dB = argMap.getFloat("loud_dB",
      m_loudnessThreshold_dB);

    m_closenessThreshold_s = argMap.getFloat("close_s",
      m_closenessThreshold_s);
  }
}


// EOF
