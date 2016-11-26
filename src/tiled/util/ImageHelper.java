/*-
 * #%L
 * This file is part of libtiled-java.
 * %%
 * Copyright (C) 2004 - 2016 Thorbj?rn Lindeijer <thorbjorn@lindeijer.nl>
 * Copyright (C) 2004 - 2016 Adam Turk <aturk@biggeruniverse.com>
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package tiled.util;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * This class provides functions to help out with saving/loading images.
 *
 * @author Thorbj?rn Lindeijer
 * @author Adam Turk
 * @version 0.17
 */
public class ImageHelper {

    /**
     * Converts an image to a PNG stored in a byte array.
     *
     * @param image a {@link java.awt.Image} object.
     * @return a byte array with the PNG data
     */
    static public byte[] imageToPNG(Image image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            BufferedImage buffer = new BufferedImage(
                    image.getWidth(null), image.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB);

            buffer.createGraphics().drawImage(image, 0, 0, null);
            ImageIO.write(buffer, "PNG", baos);
            baos.close();
        } catch (IOException e) {
            // todo: log this instead
            e.printStackTrace();
        }

        return baos.toByteArray();
    }

    /**
     * Converts a byte array into an image. The byte array must include the
     * image header, so that a decision about format can be made.
     *
     * @param imageData The byte array of the data to convert.
     * @return Image The image instance created from the byte array
     * @throws java.io.IOException if any.
     * @see java.awt.Toolkit#createImage(byte[] imagedata)
     */
    static public BufferedImage bytesToImage(byte[] imageData) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(imageData));
    }
}