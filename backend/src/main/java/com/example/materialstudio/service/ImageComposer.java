package com.example.materialstudio.service;

import com.example.materialstudio.model.AssetRow;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class ImageComposer {
    private final StorageService storage;

    public ImageComposer(StorageService storage) {
        this.storage = storage;
    }

    public Path compose(String taskId, List<AssetRow> assets) throws IOException {
        if (assets.isEmpty()) throw new IOException("No images to compose");
        BufferedImage output = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(new Color(243, 235, 220));
            g.fillRect(0, 0, 1200, 900);
            int gap = 24;
            int panelWidth = (1100 - gap * (assets.size() - 1)) / assets.size();
            for (int i = 0; i < assets.size(); i++) {
                BufferedImage image = ImageIO.read(storage.resolve(assets.get(i).storedPath).toFile());
                if (image == null) throw new IOException("Uploaded image cannot be decoded");
                int x = 50 + i * (panelWidth + gap);
                int y = 50;
                drawCover(g, image, x, y, panelWidth, 760);
            }
            g.setColor(new Color(43, 58, 69));
            g.fillRect(50, 833, 1100, 5);
            g.setColor(new Color(103, 112, 112));
            g.setFont(g.getFont().deriveFont(22f));
            g.drawString("MATERIAL STUDIO", 50, 872);
        } finally {
            g.dispose();
        }
        Path temporary = storage.resolve("results/" + taskId + ".part.png");
        if (!ImageIO.write(output, "png", temporary.toFile())) {
            throw new IOException("PNG writer unavailable");
        }
        return temporary;
    }

    private void drawCover(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        double ratio = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
        int scaledWidth = (int) Math.ceil(image.getWidth() * ratio);
        int scaledHeight = (int) Math.ceil(image.getHeight() * ratio);
        int offsetX = x - (scaledWidth - width) / 2;
        int offsetY = y - (scaledHeight - height) / 2;
        var oldClip = g.getClip();
        g.setClip(x, y, width, height);
        g.drawImage(image, offsetX, offsetY, scaledWidth, scaledHeight, null);
        g.setClip(oldClip);
    }
}
