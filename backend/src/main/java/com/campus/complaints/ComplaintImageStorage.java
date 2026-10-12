package com.campus.complaints;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class ComplaintImageStorage {

    private static final Logger logger = LoggerFactory.getLogger(ComplaintImageStorage.class);
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;

    private final Path storageDirectory;

    public ComplaintImageStorage(@Value("${app.upload.directory:./uploads}") String directory) {
        storageDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void createStorageDirectory() {
        try {
            Files.createDirectories(storageDirectory);
            logger.info("Complaint image storage directory: {}", storageDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Complaint image storage is unavailable.", exception);
        }
    }

    public String store(MultipartFile file) {
        return store(file, false);
    }

    public String storeResolutionProof(MultipartFile file) {
        return store(file, true);
    }

    private String store(MultipartFile file, boolean requireMatchingExtension) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected image is empty.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Images must be 5 MB or smaller.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected image could not be read.");
        }

        ImageFormat format = identifyFormat(content);
        if (requireMatchingExtension && !hasMatchingImageExtension(file.getOriginalFilename(), format)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Use a JPG, JPEG, PNG, or WEBP image with a matching file extension.");
        }
        validateImage(content, format);
        String reference = UUID.randomUUID() + "." + format.extension();
        Path destination = resolveReference(reference);
        try {
            Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The image could not be stored. Please try again.");
        }
        return reference;
    }

    public StoredComplaintImage read(String reference) {
        Path imagePath = resolveReference(reference);
        if (!Files.isRegularFile(imagePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The saved complaint image is missing from upload storage.");
        }
        try {
            return new StoredComplaintImage(reference, contentTypeFor(reference), Files.readAllBytes(imagePath));
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "The saved complaint image is missing from upload storage.");
        } catch (IOException exception) {
            logger.error("A stored complaint image could not be read from upload storage.", exception);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "The saved complaint image could not be read from upload storage.");
        }
    }

    public void delete(String reference) {
        if (reference == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolveReference(reference));
        } catch (IOException exception) {
            logger.error("Failed to remove an unreferenced complaint image after a database error.");
        }
    }

    private Path resolveReference(String reference) {
        if (reference == null || !reference.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The complaint image is not available.");
        }
        Path resolved = storageDirectory.resolve(reference).normalize();
        if (!resolved.getParent().equals(storageDirectory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The complaint image is not available.");
        }
        return resolved;
    }

    private ImageFormat identifyFormat(byte[] bytes) {
        if (startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return ImageFormat.JPEG;
        }
        if (startsWith(bytes, new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) {
            return ImageFormat.PNG;
        }
        if (bytes.length >= 12
                && asciiAt(bytes, 0, "RIFF")
                && asciiAt(bytes, 8, "WEBP")) {
            return ImageFormat.WEBP;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Only valid JPG, PNG, and WEBP images are supported.");
    }

    private void validateImage(byte[] content, ImageFormat format) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw new IOException("Image reader is unavailable.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            ImageReader reader = null;
            while (readers.hasNext()) {
                ImageReader candidate = readers.next();
                if (candidate.getFormatName().toLowerCase(Locale.ROOT).contains(format.readerName())) {
                    reader = candidate;
                    break;
                }
                candidate.dispose();
            }
            if (reader == null) {
                throw new IOException("Image content does not match its supported format.");
            }

            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IOException("Image dimensions are invalid.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw new IOException("Image data is corrupt.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The selected image is invalid or corrupt.");
        }
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiAt(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private String contentTypeFor(String reference) {
        if (reference.endsWith(".jpg")) {
            return "image/jpeg";
        }
        if (reference.endsWith(".png")) {
            return "image/png";
        }
        if (reference.endsWith(".webp")) {
            return "image/webp";
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The complaint image is not available.");
    }

    private boolean hasMatchingImageExtension(String filename, ImageFormat format) {
        if (filename == null) {
            return false;
        }
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String basename = filename.substring(separator + 1);
        int extensionSeparator = basename.lastIndexOf('.');
        if (extensionSeparator < 0) {
            return false;
        }
        String extension = basename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        return switch (format) {
            case JPEG -> extension.equals("jpg") || extension.equals("jpeg");
            case PNG -> extension.equals("png");
            case WEBP -> extension.equals("webp");
        };
    }

    private enum ImageFormat {
        JPEG("jpg", "jpeg"),
        PNG("png", "png"),
        WEBP("webp", "webp");

        private final String extension;
        private final String readerName;

        ImageFormat(String extension, String readerName) {
            this.extension = extension;
            this.readerName = readerName;
        }

        String extension() {
            return extension;
        }

        String readerName() {
            return readerName;
        }
    }
}
