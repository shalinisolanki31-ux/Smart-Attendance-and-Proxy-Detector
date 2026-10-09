package com.azuresight;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Opens the webcam, shows live video on the screen
 * and gives us a JPEG picture when we need to send it to Azure.
 */
public class CameraService {

    private Webcam webcam;
    private volatile boolean running = false;
    private volatile BufferedImage latestFrame;   // newest picture from the camera

    // Starts the camera and shows the video in all the given ImageViews
    public void start(ImageView... views) {
        webcam = Webcam.getDefault();
        if (webcam == null) {
            throw new IllegalStateException("No webcam found. Please connect a camera.");
        }

        // VGA (640x480) works on almost every camera.
        // For a better camera you can try WebcamResolution.HD or FHD (1080p)
        webcam.setViewSize(WebcamResolution.VGA.getSize());
        webcam.open();
        running = true;

        Thread thread = new Thread(() -> {
            while (running) {
                BufferedImage img = webcam.getImage();
                if (img != null) {
                    latestFrame = img;
                    Image fxImage = SwingFXUtils.toFXImage(img, null);
                    Platform.runLater(() -> {
                        for (ImageView v : views) {
                            v.setImage(fxImage);
                        }
                    });
                }
                try {
                    Thread.sleep(40);   // around 25 frames per second
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    // Takes the current frame and compresses it to JPEG (small size = faster upload)
    public byte[] captureJpeg() throws IOException {
        BufferedImage frame = latestFrame;
        if (frame == null) {
            throw new IOException("Camera is not ready yet.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(frame, "jpg", out);
        return out.toByteArray();
    }

    public void stop() {
        running = false;
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }
}
