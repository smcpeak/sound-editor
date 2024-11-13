// FFTTest.java

package snded;

import hedoluna.FFTbase;


// Test the FFT algorithm.
public class FFTTest {
  public static void testForward()
  {
    double[] inputReal = new double[] {
      1,
      2,
      0,
      -1
    };

    double[] inputImag = new double[] {
      0,
      -1,
      -1,
      2
    };

    double[] result = FFTbase.fft(inputReal, inputImag, true /*direct*/);

    System.out.println("forward result:");
    for (int i=0; i < result.length; i += 2) {
      System.out.println(
        "  " + (i/2) + ": (" +result[i] + ", " + result[i+1] + ")");
    }
  }

  public static void testInverse()
  {
    // These input values here are what the forward algorithm in
    // `testForward` should produce as output.

    double[] inputReal = new double[] {
      2,
      -2,
      0,
      4
    };

    double[] inputImag = new double[] {
      0,
      -2,
      -2,
      4
    };

    double[] result = FFTbase.fft(inputReal, inputImag, false /*direct*/);

    System.out.println("inverse result:");
    for (int i=0; i < result.length; i += 2) {
      System.out.println(
        "  " + (i/2) + ": (" +result[i] + ", " + result[i+1] + ")");
    }
  }

  public static void main(String args[])
  {
    testForward();
    testInverse();
  }
}


// EOF
