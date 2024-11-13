// AudioClip.java

package snded;

import mcve.audio.SimpleAudioConversion;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;


// Audio sample data and its format.
//
// This is more convenient than `AudioInputStream` since the latter is
// potentially a single-use stream, whereas this class provides random
// access to the entire array of samples.
//
// It's also nice that `IOException` is something only the ctor can
// call, whereas all consumers of `AudioInputStream` potentially throw
// it.
//
public class AudioClip {
  // ---- private data ----
  // How to interpret the sample data.  Never null.
  private AudioFormat m_format;

  // Sample data as a sequence of frames.  Each frame is a sequence of
  // samples, one per channel.  Stereo data has two channels, left then
  // right.  A sample is a floating-point amplitude, nominally in
  // [-1,1].
  //
  // Although this is currently one array, a Java array is limited in
  // size, so this interface anticipates replacing this with a sequence
  // of arrays at some point.
  //
  private float[] m_samples;

  // ---- public methods ----
  // Read details from `audio`.
  public AudioClip(AudioInputStream audio)
    throws IOException
  {
    m_format = audio.getFormat();
    assert(m_format != null);

    int bytesPerSample = bytesPerSample();

    int numBytesAvail = audio.available();
    byte[] bytes = new byte[numBytesAvail];
    int numBytesRead = audio.read(bytes);
    assert(numBytesRead == numBytesAvail);

    int numSamples = numBytesRead / bytesPerSample;

    m_samples = new float[numSamples];
    int numConvertedSamples = SimpleAudioConversion.decode(
      bytes,
      m_samples,
      numBytesRead,
      m_format);
    assert(numConvertedSamples == numSamples);
  }

  AudioFormat getFormat()
  {
    return m_format;
  }

  public long numSamples()
  {
    return m_samples.length;
  }

  public float getSample(long sampleIndex)
  {
    return m_samples[Math.toIntExact(sampleIndex)];
  }

  public void setSample(long sampleIndex, float newValue)
  {
    m_samples[Math.toIntExact(sampleIndex)] = newValue;
  }

  public int numChannels()
  {
    return m_format.getChannels();
  }

  public long numFrames()
  {
    return numSamples() / numChannels();
  }

  // Get sample by frame and channel.
  public float getFCSample(long frameIndex, int channel)
  {
    assert(0 <= frameIndex && frameIndex < numFrames());
    assert(0 <= channel && channel < numChannels());

    return m_samples[Math.toIntExact(frameIndex * numChannels() + channel)];
  }

  public void setFCSample(long frameIndex, int channel, float newValue)
  {
    assert(0 <= frameIndex && frameIndex < numFrames());
    assert(0 <= channel && channel < numChannels());

    m_samples[Math.toIntExact(frameIndex * numChannels() + channel)] = newValue;
  }

  // Return the decibel level for the given frame and channel.
  public double getFCDecibels(long frameIndex, int channel)
  {
    return linearAmplitudeToDecibels(getFCSample(frameIndex, channel));
  }

  // Bytes for one sample.  For 16-bit audio, which is very common, this
  // is 2.  Always positive.
  public int bytesPerSample()
  {
    int ret = SimpleAudioConversion.bytesPerSample(
      m_format.getSampleSizeInBits());
    assert(ret > 0);
    return ret;
  }

  // Get intended number of frames per second during playback.
  public float getFrameRate()
  {
    return m_format.getFrameRate();
  }

  // Convert `sample`, nominally in [-1,1], to the "decibel" measure
  // that Audacity uses.  Note that this does not preserve information
  // because the output does not indicate the sign of the input.
  public static double linearAmplitudeToDecibels(double sample)
  {
    if (sample == 0) {
      // `log10` is not defined on zero.  Use a very negative number of
      // decibels.  I've seen at least one place in Audacity that uses
      // the same value for a similar purpose.
      return -100.0;
    }
    else {
      // Decibels are defined using the log of a ratio to a reference
      // level.  Here, the reference level is 1.
      //
      // Multiplying by 20 (rather than 10 as the name would suggest) is
      // related to the distinction between power and amplitude, but I
      // don't know the details.
      //
      return 20.0 * Math.log10(Math.abs(sample));
    }
  }

  // Decibel conversion for power.
  public static double linearPowerToDecibels(double sample)
  {
    return linearAmplitudeToDecibels(sample) / 2;
  }

  // Convert floats back into bytes.
  public byte[] encodeSampleBytes()
  {
    int bytesPerSample = bytesPerSample();
    int numBytes = Math.toIntExact(numSamples() * bytesPerSample);
    byte[] bytes = new byte[numBytes];
    int numConvertedBytes = SimpleAudioConversion.encode(
      m_samples,
      bytes,
      Math.toIntExact(numSamples()),
      m_format);
    assert(numConvertedBytes == numBytes);

    return bytes;
  }

  // Write the samples out to a WAV file in `m_format`.
  public void writeToFile(String outFname)
    throws IOException
  {
    // Convert floats back into bytes.
    byte[] bytes = encodeSampleBytes();

    // Wrap the bytes in streams to provide them.
    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
      try (AudioInputStream ais = new AudioInputStream(bais, m_format, bytes.length)) {

        // Write the output file.
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(outFname));
      }
    }
  }


}


// EOF
