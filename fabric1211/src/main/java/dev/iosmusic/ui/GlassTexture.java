package dev.iosmusic.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/** Generates the tiny noise tile layered over glass to avoid a plastic, flat look. */
public final class GlassTexture {
    private static ResourceLocation noise;
    private static boolean attempted;

    private GlassTexture() {
    }

    public static ResourceLocation noise() {
        if (!attempted) {
            attempted = true;
            try {
                int size = 96;
                BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Random random = new Random(0x10A55);
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        int alpha = random.nextInt(18);
                        int value = random.nextBoolean() ? 255 : 0;
                        image.setRGB(x, y, (alpha << 24) | (value << 16) | (value << 8) | value);
                    }
                }
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(image, "png", png);
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
                DynamicTexture texture = new DynamicTexture(nativeImage);
                noise = Minecraft.getInstance().getTextureManager()
                        .register("iosmusic_noise", texture);
            } catch (Throwable ignored) {
            }
        }
        return noise;
    }
}
