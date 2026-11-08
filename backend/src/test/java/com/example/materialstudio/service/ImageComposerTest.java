package com.example.materialstudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.materialstudio.model.AssetRow;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageComposerTest {
    @TempDir Path tempDir;

    @Test
    void producesPreviewablePngFromBothUploadedImages() throws Exception {
        StorageService storage = new StorageService(tempDir.toString());
        AssetRow red = sample(storage, "red.png", Color.RED);
        AssetRow blue = sample(storage, "blue.png", Color.BLUE);

        Path resultPath = new ImageComposer(storage).compose("task-example", List.of(red, blue));

        assertTrue(Files.isRegularFile(resultPath));
        BufferedImage result = ImageIO.read(resultPath.toFile());
        assertEquals(1200, result.getWidth());
        assertEquals(900, result.getHeight());
        assertEquals(Color.RED.getRGB(), result.getRGB(150, 150));
        assertEquals(Color.BLUE.getRGB(), result.getRGB(750, 150));
    }

    private AssetRow sample(StorageService storage, String name, Color color) throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) image.setRGB(x, y, color.getRGB());
        }
        AssetRow asset = new AssetRow();
        asset.storedPath = "uploads/" + name;
        ImageIO.write(image, "png", storage.resolve(asset.storedPath).toFile());
        return asset;
    }
}
