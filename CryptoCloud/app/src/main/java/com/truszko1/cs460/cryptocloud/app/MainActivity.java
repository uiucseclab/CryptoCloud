package com.truszko1.cs460.cryptocloud.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class MainActivity extends Activity {

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static final String TAG = MainActivity.class.getSimpleName();
    private final Encryptor PBKDF2_ENCRYPTOR = new Encryptor() {

        @Override
        public SecretKey deriveKey(String password, byte[] salt) {
            return Crypto.deriveKeyPbkdf2(salt, password);
        }

        @Override
        public byte[][] encrypt(byte[] plaintext, String password) {
            byte[] salt = Crypto.generateSalt();
            key = deriveKey(password, salt);


            return Crypto.encrypt(plaintext, key, salt);
        }

        @Override
        public byte[][] decrypt(byte[][] encryptedInfo, String password) {
            return Crypto.decryptPbkdf2(encryptedInfo, password);
        }
    };
    ArrayList<String> imagesPath;
    byte[] paddedEncryptedBytes;
    private ImageView originalImage;
    private Button encryptButton;
    private Button decryptButton;
    private Encryptor encryptor;
    private Bitmap currentBitmap = null;

    public static byte[] fromBase64(String base64) {
        return Base64.decode(base64, Base64.NO_WRAP);
    }

    public static String toBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        deflater.finish();

        byte[] buffer = new byte[1024];

        while (!deflater.finished()) {
            int count = deflater.deflate(buffer); // returns the generated code... index
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        byte[] output = outputStream.toByteArray();
        Log.d(TAG, "Original: " + data.length / 1024 + " Kb");
        Log.d(TAG, "Compressed: " + output.length / 1024 + " Kb");
        return output;
    }

    public static byte[] decompress(byte[] data) throws IOException, DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        byte[] output = outputStream.toByteArray();

        Log.d(TAG, "Original: " + data.length);
        Log.d(TAG, "Compressed: " + output.length);
        return output;
    }

    public static byte[] intToByteArray(int a) {
        byte[] ret = new byte[4];
        ret[3] = (byte) (a & 0xFF);
        ret[2] = (byte) ((a >> 8) & 0xFF);
        ret[1] = (byte) ((a >> 16) & 0xFF);
        ret[0] = (byte) ((a >> 24) & 0xFF);
        return ret;
    }

    public static int byteArrayToInt(byte[] b) {
        return (b[3] & 0xFF) + ((b[2] & 0xFF) << 8) + ((b[1] & 0xFF) << 16) + ((b[0] & 0xFF) << 24);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setProgressBarIndeterminateVisibility(false);

        encryptor = PBKDF2_ENCRYPTOR;

        originalImage = (ImageView) findViewById(R.id.originalImage);
        encryptButton = (Button) findViewById(R.id.encrypt_button);
        decryptButton = (Button) findViewById(R.id.decrypt_button);

        loadImagePaths();

        originalImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                final Random random = new Random();
                final int count = imagesPath.size();
                int number = random.nextInt(count);
                String path = imagesPath.get(number);
                currentBitmap = BitmapFactory.decodeFile(path);
                originalImage.setImageBitmap(currentBitmap);
            }
        });

        encryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                encryptImage();
            }
        });

        decryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                decryptImage();
            }
        });
    }

    public Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 0) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    private void encryptImage() {
//        ImageView originalImage = (ImageView) findViewById(R.id.originalImage);
//        Bitmap bitmap = ((BitmapDrawable) originalImage.getDrawable()).getBitmap();


        final Random random = new Random();
        final int count = imagesPath.size();
        int number = random.nextInt(count);
        String path = imagesPath.get(number);
        currentBitmap = BitmapFactory.decodeFile(path);
//        currentBitmap = getResizedBitmap(currentBitmap, 500);

        ByteBuffer buffer = ByteBuffer.allocate(currentBitmap.getByteCount());
        currentBitmap.copyPixelsToBuffer(buffer);

        final int width = currentBitmap.getWidth();
        final int height = currentBitmap.getHeight();
        currentBitmap.recycle();

        final byte[] originalImageBytes = buffer.array();

        final long[] start = new long[1];
        final long[] finish = new long[1];
        new CryptoTask() {

            @Override
            protected byte[][] doCrypto() {
                start[0] = System.currentTimeMillis();
                return encryptor.encrypt(originalImageBytes, "password1234");
            }

            @Override
            protected void updateUi(final byte[][] encryptedInfo) {
                finish[0] = System.currentTimeMillis();
                Log.d(TAG, "encryption time:" + (finish[0] - start[0]));
                Log.d(TAG, "salt size:" + encryptedInfo[0].length);
                Log.d(TAG, "iv size:" + encryptedInfo[1].length);
                String saltInBase64 = toBase64(encryptedInfo[0]);
                String ivInBase64 = toBase64(encryptedInfo[1]);
                ((TextView) findViewById(R.id.salt)).setText(saltInBase64);
                ((TextView) findViewById(R.id.iv)).setText(ivInBase64);

//                int paddingSize = encryptedInfo[2].length - originalImageBytes.length;
//
//                int newHeight = height;
//
//                if (paddingSize > 0) {
//                    newHeight++;
//                    paddedEncryptedBytes = new byte[width * newHeight * 4];
//                    int i;
//                    // copy all encrypted bytes
//                    for (i = 0; i < encryptedInfo[2].length; i++) {
//                        paddedEncryptedBytes[i] = encryptedInfo[2][i];
//                    }
//                    // fill the last row with zeroes
//                    int newArraySize = encryptedInfo[2].length - paddingSize + width * 4;
//                    for (; i < newArraySize; i++) {
//                        paddedEncryptedBytes[i] = 0;
//                    }
//                } else {
//                    paddedEncryptedBytes = new byte[width * newHeight * 4];
//                    int i;
//                    // copy all encrypted bytes
//                    for (i = 0; i < encryptedInfo[2].length; i++) {
//                        paddedEncryptedBytes[i] = encryptedInfo[2][i];
//                    }
//                }

                // + 8 for storing width and heigt of the original image
//                int widthNumerOfBytes = 4;
//                int heightNumerOfBytes = 4;
//                int saltNumerOfBytes = 8;
//                int ivNumerOfBytes = 16;
//                paddedEncryptedBytes = new byte[encryptedInfo[2].length + widthNumerOfBytes + heightNumerOfBytes + saltNumerOfBytes + ivNumerOfBytes];

//                int i = 0;
//                for (; i < encryptedInfo[2].length; i++) {
//                    paddedEncryptedBytes[i] = encryptedInfo[2][i];
//                }
//
//                // next 4 bytes are width
//                byte[] widthBytes = intToByteArray(width);
//                paddedEncryptedBytes[i++] = widthBytes[0];
//                paddedEncryptedBytes[i++] = widthBytes[1];
//                paddedEncryptedBytes[i++] = widthBytes[2];
//                paddedEncryptedBytes[i++] = widthBytes[3];
//
//                byte[] heightBytes = intToByteArray(height);
//                paddedEncryptedBytes[i++] = heightBytes[0];
//                paddedEncryptedBytes[i++] = heightBytes[1];
//                paddedEncryptedBytes[i++] = heightBytes[2];
//                paddedEncryptedBytes[i++] = heightBytes[3];
//


                ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
                try {
                    bytesStream.write(encryptedInfo[2]); // image bytes
                    Log.d(TAG, "number of encrypted image bytes" + encryptedInfo[2].length);
                    bytesStream.write(encryptedInfo[0]); // salt
                    bytesStream.write(encryptedInfo[1]); // iv
                    bytesStream.write(intToByteArray(width));
                    bytesStream.write(intToByteArray(height));
                } catch (IOException e) {
                    e.printStackTrace();
                }


                paddedEncryptedBytes = bytesStream.toByteArray();

                Log.d(TAG, "paddedEncryptedBytes length:" + paddedEncryptedBytes.length);
//                Log.d(TAG, "original bytes hex:" + bytesToHex(paddedEncryptedBytes));

//                final Bitmap encryptedImage = Bitmap.createBitmap(width, newHeight, Bitmap.Config.ARGB_8888);
//                Log.d(TAG, "WxH:" + width + "x" + newHeight);
//                ByteBuffer buffer = ByteBuffer.wrap(paddedEncryptedBytes);
//                encryptedImage.copyPixelsFromBuffer(buffer);

//                ((ImageView) findViewById(R.id.encryptedImage)).setImageBitmap(encryptedImage);

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                alertDialogBuilder.setMessage("save the image?");
                alertDialogBuilder.setPositiveButton("yes",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {

//                                MediaStore.Images.Media.insertImage(getContentResolver(), encryptedImage, "title.jpg", "asdsad");


                                String file_path = Environment.getExternalStorageDirectory().getAbsolutePath();
                                File dir = new File(file_path);
                                if (!dir.exists())
                                    dir.mkdirs();
                                File file = new File(dir, "meh");
//                                Log.d(TAG, dir.getAbsolutePath());
//                                FileOutputStream fOut;
//                                try {
//                                    fOut = new FileOutputStream(file);
//                                    encryptedImage.compress(Bitmap.CompressFormat.PNG, 100, fOut);
//                                    fOut.flush();
//                                    fOut.close();
//                                } catch (FileNotFoundException e) {
//                                    e.printStackTrace();
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }

                                BufferedOutputStream bos = null;
                                try {
                                    bos = new BufferedOutputStream(new FileOutputStream(file));
                                    bos.write(paddedEncryptedBytes);
                                    bos.flush();
                                    bos.close();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

//                                ImageView imgView = (ImageView) findViewById(R.id.encryptedImage);
//                                imgView.setImageBitmap(encryptedImage);


                            }
                        });
                alertDialogBuilder.setNegativeButton("no",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });

                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();

            }
        }.execute();
    }

    private void decryptImage() {
//        ImageView encryptedImage = (ImageView) findViewById(R.id.encryptedImage);
//        Bitmap bitmap = ((BitmapDrawable) encryptedImage.getDrawable()).getBitmap();
//


        byte[] encryptedImageInfo = null;
        // read file
        File file = new File("/storage/emulated/0/meh");
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            encryptedImageInfo = new byte[(int) file.length()];
            fin.read(encryptedImageInfo);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fin != null) {
                    fin.close();
                }
            } catch (IOException ioe) {
            }
        }

        int totalBytes = encryptedImageInfo.length;
        int heightNumberOfBytes = 4;
        int widthNumberOfBytes = 4;
        int ivNumberOfBytes = 16;
        int saltNumberOfBytes = 8;
        int imageNumberOfBytes = totalBytes - heightNumberOfBytes - widthNumberOfBytes - saltNumberOfBytes - ivNumberOfBytes;
        final byte[] heightBytes = Arrays.copyOfRange(encryptedImageInfo, totalBytes - heightNumberOfBytes, totalBytes);
        totalBytes -= heightNumberOfBytes;
        final byte[] widthBytes = Arrays.copyOfRange(encryptedImageInfo, totalBytes - widthNumberOfBytes, totalBytes);
        totalBytes -= widthNumberOfBytes;
        byte[] ivBytes = Arrays.copyOfRange(encryptedImageInfo, totalBytes - ivNumberOfBytes, totalBytes);
        totalBytes -= ivNumberOfBytes;
        byte[] saltBytes = Arrays.copyOfRange(encryptedImageInfo, totalBytes - saltNumberOfBytes, totalBytes);
        totalBytes -= saltNumberOfBytes;
        byte[] imageBytes = Arrays.copyOfRange(encryptedImageInfo, totalBytes - imageNumberOfBytes, totalBytes);
        encryptedImageInfo = null;

        Log.d(TAG, "height:" + byteArrayToInt(heightBytes));
        Log.d(TAG, "width:" + byteArrayToInt(widthBytes));
        Log.d(TAG, "iv:" + toBase64(ivBytes));
        Log.d(TAG, "salt:" + toBase64(saltBytes));

        Log.d(TAG, "number of decrypted image bytes" + imageBytes.length);
//        String file_path = Environment.getExternalStorageDirectory().getAbsolutePath();
//        Bitmap bitmap = BitmapFactory.decodeFile(file_path + "/" + "meh.png");
//
//        ImageView imgView = (ImageView) findViewById(R.id.decryptedImage);
//        imgView.setImageBitmap(bitmap);
//
//        ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount());
//        bitmap.copyPixelsToBuffer(buffer);
//        byte[] imageBytes = buffer.array();
//        Log.d(TAG, "original bytes hex:" + bytesToHex(imageBytes));
//        Log.d(TAG, "retrieved imageBytes length:" + encryptedImageInfo.length);

        boolean isSamePicture = true;

//        for (int i = 0; i < 20; i++) {
//            if (imageBytes[i] != paddedEncryptedBytes[i]) {
//                isSamePicture = false;
//            }
//            Log.d(TAG, imageBytes[i] + " " + paddedEncryptedBytes[i]);
//        }

//        Log.d(TAG, "isSamePicture:" + isSamePicture);
//
//        int actualLengthOfImage = imageBytes.length;
//
//        for (int i = imageBytes.length - 1; i > 0; i -= 8) {
//            boolean isBlockOfZeros = true;
//            for (int t = i; t > i - 8; t--) {
//                if (imageBytes[t] != 0) {
//                    isBlockOfZeros = false;
//                    break;
//                }
//            }
//            if (isBlockOfZeros) {
//                actualLengthOfImage -= 8;
//            } else {
//                break;
//            }
//        }

//        final int width = 1920;//bitmap.getWidth();
//        final int height = 22;
//        if (actualLengthOfImage < imageBytes.length) {
//            height = 1280;//bitmap.getHeight() - 1;
//        } else {
//            height = 1281;//bitmap.getHeight();
//        }

//        byte[] finalImageBytes = new byte[encryptedImageInfo.length];
//        for (int i = 0; i < encryptedImageInfo.length; i++) {
//            finalImageBytes[i] = encryptedImageInfo[i];
//        }

        final byte[][] imageToBeDecrypted = new byte[][]{saltBytes, ivBytes, imageBytes};


        new CryptoTask() {

            @Override
            protected byte[][] doCrypto() {
                return encryptor.decrypt(imageToBeDecrypted, "password1234");
            }

            protected void updateUi(byte[][] decryptedInfo) {
                byte[] imageBytes = decryptedInfo[0];

                Log.d(TAG, "decrypted bytes" + imageBytes.length + "");


                Bitmap decryptedImage = Bitmap.createBitmap(byteArrayToInt(widthBytes), byteArrayToInt(heightBytes), Bitmap.Config.ARGB_8888);

                ByteBuffer b = ByteBuffer.wrap(decryptedInfo[0]);
                decryptedImage.copyPixelsFromBuffer(b);


                ImageView imgView = (ImageView) findViewById(R.id.decryptedImage);
                imgView.setImageBitmap(decryptedImage);


            }
        }.execute();

    }

    private void loadImagePaths() {
        String[] projection = new String[]{
                MediaStore.Images.Media.DATA,
        };

        Uri images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cur = getContentResolver().query(images, projection, null, null, null);

        imagesPath = new ArrayList<String>();
        if (cur.moveToFirst()) {

            int dataColumn = cur.getColumnIndex(
                    MediaStore.Images.Media.DATA);
            do {
                imagesPath.add(cur.getString(dataColumn));
            } while (cur.moveToNext());
        }
        cur.close();
    }

    private void toggleControls(boolean enable) {
        encryptButton.setEnabled(enable);
        decryptButton.setEnabled(enable);
    }


    abstract class Encryptor {
        SecretKey key;

        abstract SecretKey deriveKey(String passpword, byte[] salt);

        abstract byte[][] encrypt(byte[] plaintext, String password);

        abstract byte[][] decrypt(byte[][] ciphertext, String password);

    }

    abstract class CryptoTask extends AsyncTask<Void, Void, byte[][]> {

        Exception error;

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);
            toggleControls(false);
        }

        @Override
        protected byte[][] doInBackground(Void... params) {
            try {
                return doCrypto();
            } catch (Exception e) {
                error = e;
                Log.e(TAG, "Error: " + e.getMessage(), e);

                return null;
            }
        }

        protected abstract byte[][] doCrypto();

        @Override
        protected void onPostExecute(byte[][] result) {
            setProgressBarIndeterminateVisibility(false);
            toggleControls(true);

            if (error != null) {
                Toast.makeText(MainActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_LONG)
                        .show();

                return;
            }


            updateUi(result);
        }

        protected abstract void updateUi(byte[][] result);
    }
}
