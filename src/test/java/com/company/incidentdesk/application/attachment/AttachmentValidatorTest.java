package com.company.incidentdesk.application.attachment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.company.incidentdesk.domain.attachment.AttachmentType;
import com.company.incidentdesk.support.AttachmentFixture;

class AttachmentValidatorTest {
    @TempDir Path directory;

    @Test
    void acceptsJpegWithEitherSupportedExtension() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpeg", output));
        image.flush();
        AttachmentValidator validator = new AttachmentValidator(AttachmentLimits.DEFAULT);
        assertEquals(AttachmentType.JPEG, validator.validate(output.toByteArray(), "photo.jpg"));
        assertEquals(AttachmentType.JPEG, validator.validate(output.toByteArray(), "photo.JPEG"));
    }

    @Test
    void detectsContentAndRejectsSpoofedExtensionsEmptyAndMalformedFiles() throws Exception {
        AttachmentValidator validator = new AttachmentValidator(AttachmentLimits.DEFAULT);
        byte[] png = AttachmentFixture.png(1, 1);
        assertEquals(AttachmentType.PNG, validator.validate(png, "test.PNG"));
        assertThrows(AttachmentValidationException.class, () -> validator.validate(png, "fake.mp4"));
        assertThrows(AttachmentValidationException.class, () -> validator.validate(new byte[0], "empty.png"));
        assertThrows(AttachmentValidationException.class, () -> validator.validate(new byte[] { (byte) 0xff, (byte) 0xd8,
                (byte) 0xff }, "bad.jpg"));
        assertThrows(AttachmentValidationException.class, () -> validator.validate("not a movie".getBytes(), "bad.mp4"));
    }

    @Test
    void enforcesExactByteAndDecodedPixelLimits() throws Exception {
        byte[] png = AttachmentFixture.png(2, 2);
        AttachmentValidator exact = new AttachmentValidator(new AttachmentLimits(png.length, 1, png.length, 4));
        assertEquals(AttachmentType.PNG, exact.validate(png, "test.png"));
        AttachmentValidator tooSmall = new AttachmentValidator(new AttachmentLimits(png.length - 1, 1, 100, 4));
        assertThrows(AttachmentValidationException.class, () -> tooSmall.validate(png, "test.png"));
        AttachmentValidator tooManyPixels = new AttachmentValidator(new AttachmentLimits(1000, 1, 1000, 3));
        assertThrows(AttachmentValidationException.class, () -> tooManyPixels.validate(png, "test.png"));
        Path oversized = Files.write(directory.resolve("oversized.png"), new byte[1001]);
        assertThrows(AttachmentValidationException.class, () -> tooManyPixels.readSource(oversized));
    }

    @Test
    void sanitizesPathsAndInvisibleControlCharacters() {
        assertEquals("picture.png", AttachmentValidator.sanitizeName("C:\\Users\\person\\picture.png"));
        assertEquals("image.png", AttachmentValidator.sanitizeName("../../image\u202e\n.png"));
        assertEquals("Attachment", AttachmentValidator.sanitizeName("  "));
    }
}
