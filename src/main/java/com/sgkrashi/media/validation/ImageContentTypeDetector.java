package com.sgkrashi.media.validation;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Reads an uploaded file's actual magic bytes and returns the image content
 * type they indicate, independent of whatever Content-Type the client
 * declared. Extracted from {@code MediaServiceImpl} (Module 5) so any module
 * validating image uploads (Media, AI Crop Doctor) can require the declared
 * and detected types to agree, rather than trusting the declared type alone.
 */
public final class ImageContentTypeDetector {

    private ImageContentTypeDetector() {
    }

    public static String detect(MultipartFile file) {
        byte[] header = new byte[12];
        int bytesRead;
        try {
            bytesRead = file.getInputStream().read(header);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded file", ex);
        }

        if (bytesRead >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytesRead >= 4
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "image/png";
        }
        if (bytesRead >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
