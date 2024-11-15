// FFTbase.java
// Originally from https://github.com/hedoluna/fft commit 6f116b1 (2022-12-02).

// Subsequently modified by smcpeak:
//   * Add a few comments.
//   * Add a package declaration.
//   * Add `useLibreOfficeConventions`.

package hedoluna;

/**
* @author Orlando Selenu
* Originally written in the Summer of 2008
* Based on the algorithms originally published by E. Oran Brigham "The Fast Fourier Transform" 1973, in ALGOL60 and FORTRAN
* Released in the Public Domain.
*/
public class FFTbase {

// smcpeak: Enable a couple adjustments to the conventions in order to
// make the output agree with LibreOffice `FOURIER` function so I can
// more easily validate the output.
public static final boolean useLibreOfficeConventions = true;

// Class to contain the working storage that will be reused between
// calls.  The client does not need to clear it between calls.
public static class WorkingStorage {
    // The algorithm populates these two arrays with the output at
    // first.
    /*package*/ double[] xReal;
    /*package*/ double[] xImag;

    // Then it copies the output here, so this is the actual output.
    /*package*/ double[] newArray;

    public WorkingStorage(int n)
    {
        xReal = new double[n];
        xImag = new double[n];
        newArray = new double[n * 2];
    }
}

/**
 * The Fast Fourier Transform (generic version, with NO optimizations).
 *
 * @param inputReal
 *            an array of length n, the real part
 * @param inputImag
 *            an array of length n, the imaginary part
 * @param DIRECT
 *            TRUE = direct transform, FALSE = inverse transform
 * @return a new array of length 2n
 */
public static double[] fft(final double[] inputReal, double[] inputImag, boolean DIRECT) {
    // smcpeak: This function is now a wrapper for `fft_no_alloc`.
    WorkingStorage storage = new WorkingStorage(inputReal.length);

    return fft_no_alloc(
        inputReal,
        inputImag,
        DIRECT,
        storage);
}


// smcpeak: Variant that does not do any allocation.  The array it
// returns is the one contained inside `storage`.  The client must
// copy its values elsewhere before calling this function again with
// the same `storage`.
//
// It turns out that this optimization made no difference!
//
public static double[] fft_no_alloc(
    double[] inputReal,
    double[] inputImag,
    boolean DIRECT,
    WorkingStorage storage)
{
    // - n is the dimension of the problem
    // - nu is its logarithm in base e
    int n = inputReal.length;

    // If n is a power of 2, then ld is an integer (_without_ decimals)
    double ld = Math.log(n) / Math.log(2.0);

    // Here I check if n is a power of 2. If exist decimals in ld, I quit
    // from the function returning null.
    if (((int) ld) - ld != 0) {
        throw new RuntimeException("The number of FFT elements is not a power of 2.");
    }

    // Declaration and initialization of the variables
    // ld should be an integer, actually, so I don't lose any information in
    // the cast
    int nu = (int) ld;
    int n2 = n / 2;
    int nu1 = nu - 1;
    double[] xReal = storage.xReal;
    double[] xImag = storage.xImag;
    double tReal;
    double tImag;
    double p;
    double arg;
    double c;
    double s;

    // Here I check if I'm going to do the direct transform or the inverse
    // transform.
    double constant;
    if (DIRECT)
        constant = -2 * Math.PI;
    else
        constant = 2 * Math.PI;

    if (useLibreOfficeConventions) {
        constant = -constant;
    }

    // I don't want to overwrite the input arrays, so here I copy them. This
    // choice adds \Theta(2n) to the complexity.
    for (int i = 0; i < n; i++) {
        xReal[i] = inputReal[i];
        xImag[i] = inputImag[i];
    }

    // First phase - calculation
    int k = 0;
    for (int l = 1; l <= nu; l++) {
        while (k < n) {
            for (int i = 1; i <= n2; i++) {
                p = bitreverseReference(k >> nu1, nu);
                // direct FFT or inverse FFT
                arg = constant * p / n;
                c = Math.cos(arg);
                s = Math.sin(arg);
                tReal = xReal[k + n2] * c + xImag[k + n2] * s;
                tImag = xImag[k + n2] * c - xReal[k + n2] * s;
                xReal[k + n2] = xReal[k] - tReal;
                xImag[k + n2] = xImag[k] - tImag;
                xReal[k] += tReal;
                xImag[k] += tImag;
                k++;
            }
            k += n2;
        }
        k = 0;
        nu1--;
        n2 /= 2;
    }

    // Second phase - recombination
    k = 0;
    int r;
    while (k < n) {
        r = bitreverseReference(k, nu);
        if (r > k) {
            tReal = xReal[k];
            tImag = xImag[k];
            xReal[k] = xReal[r];
            xImag[k] = xImag[r];
            xReal[r] = tReal;
            xImag[r] = tImag;
        }
        k++;
    }

    // Here I have to mix xReal and xImag to have an array (yes, it should
    // be possible to do this stuff in the earlier parts of the code, but
    // it's here to readibility).
    double[] newArray = storage.newArray;
    double radice = 1 / Math.sqrt(n);
    if (useLibreOfficeConventions) {
        if (DIRECT) {
            radice = 1;
        }
        else {
            radice = 1 / (double)n;
        }
    }
    for (int i = 0; i < newArray.length; i += 2) {
        int i2 = i / 2;
        // I used Stephen Wolfram's Mathematica as a reference so I'm going
        // to normalize the output while I'm copying the elements.
        newArray[i] = xReal[i2] * radice;
        newArray[i + 1] = xImag[i2] * radice;
    }
    return newArray;
}

/**
 * The reference bit reverse function.
 */
private static int bitreverseReference(int j, int nu) {
    int j2;
    int j1 = j;
    int k = 0;
    for (int i = 1; i <= nu; i++) {
        j2 = j1 / 2;
        k = 2 * k + j1 - 2 * j2;
        j1 = j2;
    }
    return k;
  }
}