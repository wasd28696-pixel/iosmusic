package dev.iosmusic.ui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/** Renders a login URL into a rounded QR card texture with ZXing. */
public final class QrCode {
    private QrCode() {
    }

    public static ResourceLocation create(String text) {
        try {
            int size = 320;
            QRCodeWriter writer = new QRCodeWriter();
            var matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1));
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(255, 255, 255, 255));
            graphics.fill(new RoundRectangle2D.Float(0, 0, size, size, 64, 64));
            graphics.setColor(new Color(9, 11, 16, 255));
            int quiet = 18;
            int module = Math.max(1, (size - quiet * 2) / matrix.getWidth());
            int drawn = module * matrix.getWidth();
            int offset = (size - drawn) / 2;
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        graphics.fillRect(offset + x * module, offset + y * module, module, module);
                    }
                }
            }
            graphics.dispose();
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
            DynamicTexture texture = new DynamicTexture(nativeImage);
            return Minecraft.getInstance().getTextureManager()
                    .register("iosmusic_qr_" + System.nanoTime(), texture);
        } catch (Throwable t) {
            return null;
        }
    }
}
