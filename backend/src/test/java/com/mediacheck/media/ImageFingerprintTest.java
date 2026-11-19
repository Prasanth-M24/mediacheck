package com.mediacheck.media;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

public class ImageFingerprintTest {

  @Test
  void exactMatchProducesIdenticalHashes() {
    byte[] image = createGradientImage(200, 200, Color.BLUE, Color.RED);
    String d1 = ImageFingerprint.differenceHash(image);
    String d2 = ImageFingerprint.differenceHash(image);
    String p1 = ImageFingerprint.perceptualHash(image);
    String p2 = ImageFingerprint.perceptualHash(image);

    assertThat(d1).isEqualTo(d2);
    assertThat(p1).isEqualTo(p2);
    assertThat(ImageFingerprint.distance(d1, d2)).isEqualTo(0);
    assertThat(ImageFingerprint.similarity(0)).isEqualTo(1.0);
  }

  @Test
  void resizedImageProducesNearIdenticalHashes() {
    byte[] original = createGradientImage(400, 400, Color.BLACK, Color.WHITE);
    byte[] resized = createGradientImage(100, 100, Color.BLACK, Color.WHITE);

    String d1 = ImageFingerprint.differenceHash(original);
    String d2 = ImageFingerprint.differenceHash(resized);
    String p1 = ImageFingerprint.perceptualHash(original);
    String p2 = ImageFingerprint.perceptualHash(resized);

    int dDist = ImageFingerprint.distance(d1, d2);
    int pDist = ImageFingerprint.distance(p1, p2);

    // Resized same content should have very low bit distance (<= 4 bits)
    assertThat(dDist).isLessThanOrEqualTo(4);
    assertThat(pDist).isLessThanOrEqualTo(4);
    assertThat(ImageFingerprint.similarity(dDist)).isGreaterThanOrEqualTo(0.93);
  }

  @Test
  void recompressedImageRetainsHighSimilarity() {
    byte[] highQuality = createPatternImage(300, 300, 8);
    byte[] lowQuality = recompressJpeg(highQuality, 0.4f);

    String p1 = ImageFingerprint.perceptualHash(highQuality);
    String p2 = ImageFingerprint.perceptualHash(lowQuality);

    int dist = ImageFingerprint.distance(p1, p2);
    assertThat(dist).isLessThanOrEqualTo(6);
    assertThat(ImageFingerprint.similarity(dist)).isGreaterThanOrEqualTo(0.90);
  }

  @Test
  void completelyDifferentImagesProduceHighDistance() {
    byte[] horizontal = createGradientImage(200, 200, Color.WHITE, Color.BLACK);
    byte[] vertical = createVerticalGradientImage(200, 200, Color.WHITE, Color.BLACK);

    String d1 = ImageFingerprint.differenceHash(horizontal);
    String d2 = ImageFingerprint.differenceHash(vertical);

    int dist = ImageFingerprint.distance(d1, d2);
    // Orthogonal gradients have high Hamming distance
    assertThat(dist).isGreaterThan(16);
    assertThat(ImageFingerprint.similarity(dist)).isLessThan(0.75);
  }

  @Test
  void handlesCorruptOrNullBytesGracefully() {
    assertThat(ImageFingerprint.differenceHash(null)).isNull();
    assertThat(ImageFingerprint.differenceHash(new byte[] { 1, 2, 3 })).isNull();
    assertThat(ImageFingerprint.perceptualHash(null)).isNull();
    assertThat(ImageFingerprint.distance(null, "0123456789abcdef")).isEqualTo(Integer.MAX_VALUE);
    assertThat(ImageFingerprint.distance("short", "0123456789abcdef")).isEqualTo(Integer.MAX_VALUE);
  }

  private static byte[] createGradientImage(int w, int h, Color left, Color right) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    for (int x = 0; x < w; x++) {
      float ratio = (float) x / (float) w;
      int r = (int) (left.getRed() * (1 - ratio) + right.getRed() * ratio);
      int gr = (int) (left.getGreen() * (1 - ratio) + right.getGreen() * ratio);
      int b = (int) (left.getBlue() * (1 - ratio) + right.getBlue() * ratio);
      g.setColor(new Color(r, gr, b));
      g.drawLine(x, 0, x, h);
    }
    g.dispose();
    return toJpeg(img);
  }

  private static byte[] createVerticalGradientImage(int w, int h, Color top, Color bottom) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    for (int y = 0; y < h; y++) {
      float ratio = (float) y / (float) h;
      int r = (int) (top.getRed() * (1 - ratio) + bottom.getRed() * ratio);
      int gr = (int) (top.getGreen() * (1 - ratio) + bottom.getGreen() * ratio);
      int b = (int) (top.getBlue() * (1 - ratio) + bottom.getBlue() * ratio);
      g.setColor(new Color(r, gr, b));
      g.drawLine(0, y, w, y);
    }
    g.dispose();
    return toJpeg(img);
  }

  private static byte[] createPatternImage(int w, int h, int grid) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    for (int x = 0; x < w; x += grid) {
      for (int y = 0; y < h; y += grid) {
        if ((x / grid + y / grid) % 2 == 0) {
          g.setColor(Color.DARK_GRAY);
        } else {
          g.setColor(Color.LIGHT_GRAY);
        }
        g.fillRect(x, y, grid, grid);
      }
    }
    g.dispose();
    return toJpeg(img);
  }

  private static byte[] recompressJpeg(byte[] orig, float quality) {
    try {
      BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(orig));
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      var writers = ImageIO.getImageWritersByFormatName("jpg");
      var writer = writers.next();
      var param = writer.getDefaultWriteParam();
      param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
      var ios = ImageIO.createImageOutputStream(baos);
      writer.setOutput(ios);
      writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
      writer.dispose();
      ios.close();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] toJpeg(BufferedImage img) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(img, "jpg", baos);
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
