package com.mediacheck.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Transparent perceptual image fingerprinting combining 64-bit Difference Hash (dHash)
 * and 64-bit Discrete Cosine Transform Perceptual Hash (pHash).
 */
public final class ImageFingerprint {
  private static final int PHASH_SIZE = 32;
  private static final int PHASH_REDUCED_SIZE = 8;

  private ImageFingerprint() {}

  /**
   * 64-bit difference hash (dHash) based on horizontal gradient.
   * Resizes to 9x8 grayscale and sets bit (x, y) if pixel(x) > pixel(x + 1).
   */
  public static String differenceHash(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return null;
    try {
      BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
      if (source == null) return null;
      BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
      Graphics2D graphics = scaled.createGraphics();
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(source, 0, 0, 9, 8, null);
      graphics.dispose();

      long hash = 0L;
      for (int y = 0; y < 8; y++) {
        for (int x = 0; x < 8; x++) {
          int left = new Color(scaled.getRGB(x, y)).getRed();
          int right = new Color(scaled.getRGB(x + 1, y)).getRed();
          if (left > right) {
            hash |= 1L << (y * 8 + x);
          }
        }
      }
      return String.format("%016x", hash);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * 64-bit DCT perceptual hash (pHash) based on low-frequency Discrete Cosine Transform.
   * Resizes to 32x32, performs 2D-DCT, takes top-left 8x8 coefficients, and sets bits relative to median.
   */
  public static String perceptualHash(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return null;
    try {
      BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
      if (source == null) return null;
      BufferedImage scaled = new BufferedImage(PHASH_SIZE, PHASH_SIZE, BufferedImage.TYPE_BYTE_GRAY);
      Graphics2D graphics = scaled.createGraphics();
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(source, 0, 0, PHASH_SIZE, PHASH_SIZE, null);
      graphics.dispose();

      double[][] vals = new double[PHASH_SIZE][PHASH_SIZE];
      for (int y = 0; y < PHASH_SIZE; y++) {
        for (int x = 0; x < PHASH_SIZE; x++) {
          vals[y][x] = new Color(scaled.getRGB(x, y)).getRed();
        }
      }

      // Compute 2D Discrete Cosine Transform (DCT)
      double[][] dct = computeDct(vals, PHASH_SIZE);

      // Extract 8x8 low frequency matrix (excluding DC term at [0][0])
      double[] lowFreq = new double[PHASH_REDUCED_SIZE * PHASH_REDUCED_SIZE - 1];
      int idx = 0;
      double sum = 0.0;
      for (int u = 0; u < PHASH_REDUCED_SIZE; u++) {
        for (int v = 0; v < PHASH_REDUCED_SIZE; v++) {
          if (u == 0 && v == 0) continue;
          lowFreq[idx] = dct[u][v];
          sum += dct[u][v];
          idx++;
        }
      }
      double median = sum / lowFreq.length;

      // Construct 64-bit hash
      long hash = 0L;
      int bitIdx = 0;
      for (int u = 0; u < PHASH_REDUCED_SIZE; u++) {
        for (int v = 0; v < PHASH_REDUCED_SIZE; v++) {
          if (dct[u][v] > median) {
            hash |= 1L << bitIdx;
          }
          bitIdx++;
        }
      }
      return String.format("%016x", hash);
    } catch (IOException e) {
      return null;
    }
  }

  private static double[][] computeDct(double[][] f, int n) {
    double[][] fDct = new double[n][n];
    double c0 = 1.0 / Math.sqrt(2.0);
    double factor = Math.PI / (2.0 * n);

    for (int u = 0; u < n; u++) {
      double cu = (u == 0) ? c0 : 1.0;
      for (int v = 0; v < n; v++) {
        double cv = (v == 0) ? c0 : 1.0;
        double sum = 0.0;
        for (int x = 0; x < n; x++) {
          double cosX = Math.cos((2 * x + 1) * u * factor);
          for (int y = 0; y < n; y++) {
            sum += f[x][y] * cosX * Math.cos((2 * y + 1) * v * factor);
          }
        }
        fDct[u][v] = 0.25 * cu * cv * sum;
      }
    }
    return fDct;
  }

  /**
   * Hamming distance between two 16-character hexadecimal 64-bit hashes.
   */
  public static int distance(String first, String second) {
    if (first == null || second == null || first.length() != 16 || second.length() != 16) {
      return Integer.MAX_VALUE;
    }
    try {
      long h1 = Long.parseUnsignedLong(first, 16);
      long h2 = Long.parseUnsignedLong(second, 16);
      return Long.bitCount(h1 ^ h2);
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  /**
   * Normalized similarity score between 0.0 (no similarity) and 1.0 (identical hash).
   */
  public static double similarity(int distance) {
    if (distance >= 64 || distance < 0) return 0.0;
    return Math.round((1.0 - ((double) distance / 64.0)) * 1000.0) / 1000.0;
  }
}
