package com.truszko1.cs460.cryptocloud.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Created by truszko1 on 5/7/15.
 */
public class UtilityFunctions {
    public static void convertBytesToBitmap(int outputImageHeight, int outputImageWidth, Canvas c, byte[] imageSaltAndIvBytes) {
        int bI = 0;
        Paint p = new Paint();
        int i = 0, j = 0;
        for (i = 0; i < outputImageHeight; i++) {
            for (j = 0; j < outputImageWidth; j++) {
                // store
                int colorInt;
                if (bI >= imageSaltAndIvBytes.length) {
                    Random random = new Random();
                    int randomInt = random.nextInt(255);
                    colorInt = Color.rgb(randomInt, randomInt, randomInt);
                } else {
                    int color = imageSaltAndIvBytes[bI++] + 128;
                    colorInt = Color.rgb(color, color, color);
                }
                // Set the colour on the Paint;
                p.setColor(colorInt);
                // Draw the pixel;
                c.drawPoint(j, i, p);
            }
        }

        embedEncryptedInfoLength(c, p, i, j, imageSaltAndIvBytes);
    }

    private static void embedEncryptedInfoLength(Canvas c, Paint p, int i, int j, byte[] imageSaltAndIvBytes) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(imageSaltAndIvBytes.length);

//        Log.d(TAG, imageSaltAndIvBytes.length + "");

        byte[] result = b.array();
        p.setColor(Color.rgb(result[3] + 128, result[3] + 128, result[3] + 128));
        c.drawPoint(--j, i - 1, p);
        p.setColor(Color.rgb(result[2] + 128, result[2] + 128, result[2] + 128));
        c.drawPoint(--j, i - 1, p);
        p.setColor(Color.rgb(result[1] + 128, result[1] + 128, result[1] + 128));
        c.drawPoint(--j, i - 1, p);
        p.setColor(Color.rgb(result[0] + 128, result[0] + 128, result[0] + 128));
        c.drawPoint(--j, i - 1, p);
    }

    public static ByteArrayOutputStream writeEncryptedInfoToBuffer(byte[][] encryptedInfo) {
        ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
        try {
            bytesStream.write(encryptedInfo[2]); // image bytes
//            Log.d(TAG, "number of encrypted image bytes:" + encryptedInfo[2].length);
            bytesStream.write(encryptedInfo[0]); // salt
            bytesStream.write(encryptedInfo[1]); // iv
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytesStream;
    }
}
