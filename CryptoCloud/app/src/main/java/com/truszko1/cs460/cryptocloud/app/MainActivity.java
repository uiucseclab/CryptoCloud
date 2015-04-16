package com.truszko1.cs460.cryptocloud.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends Activity {

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
                if (currentBitmap != null)
                    currentBitmap.recycle();
                currentBitmap = BitmapFactory.decodeFile(path);
                originalImage.setImageBitmap(currentBitmap);
            }
        });

        encryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ImageView originalImage = (ImageView) findViewById(R.id.originalImage);
                Bitmap bitmap = ((BitmapDrawable) originalImage.getDrawable()).getBitmap();
                ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount());
                bitmap.copyPixelsToBuffer(buffer);

                final int width = bitmap.getWidth();
                final int height = bitmap.getHeight();
                bitmap.recycle();

                final byte[] originalImageBytes = buffer.array();

                new CryptoTask() {

                    @Override
                    protected byte[][] doCrypto() {
                        return encryptor.encrypt(originalImageBytes, "password1234");
                    }

                    @Override
                    protected void updateUi(final byte[][] encryptedInfo) {
                        String saltInBase64 = toBase64(encryptedInfo[0]);
                        String ivInBase64 = toBase64(encryptedInfo[1]);
                        ((TextView) findViewById(R.id.salt)).setText(saltInBase64);
                        ((TextView) findViewById(R.id.iv)).setText(ivInBase64);

                        int paddingSize = encryptedInfo[2].length - originalImageBytes.length;

                        int newHeight = height;

                        byte[] paddedEncryptedBytes;

                        if (paddingSize > 0) {
                            newHeight++;
                            paddedEncryptedBytes = new byte[width * newHeight * 4];
                            int i;
                            // copy all encrypted bytes
                            for (i = 0; i < encryptedInfo[2].length; i++) {
                                paddedEncryptedBytes[i] = encryptedInfo[2][i];
                            }
                            // fill the last row with zeroes
                            int newArraySize = encryptedInfo[2].length - paddingSize + width * 4;
                            for (; i < newArraySize; i++) {
                                paddedEncryptedBytes[i] = 0;
                            }
                        } else {
                            paddedEncryptedBytes = new byte[width * newHeight * 4];
                            int i;
                            // copy all encrypted bytes
                            for (i = 0; i < encryptedInfo[2].length; i++) {
                                paddedEncryptedBytes[i] = encryptedInfo[2][i];
                            }
                        }

                        Bitmap encryptedImage = Bitmap.createBitmap(width, newHeight, Bitmap.Config.ARGB_8888);
                        ByteBuffer buffer = ByteBuffer.wrap(paddedEncryptedBytes);
                        encryptedImage.copyPixelsFromBuffer(buffer);

                        ((ImageView) findViewById(R.id.encryptedImage)).setImageBitmap(encryptedImage);
                    }
                }.execute();
            }
        });
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

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == 0 && resultCode == RESULT_OK
                    && null != data) {

                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                Cursor cursor = getContentResolver().query(selectedImage,
                        filePathColumn, null, null, null);
                cursor.moveToFirst();

                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                String imgDecodableString = cursor.getString(columnIndex);
                cursor.close();
                final ImageView imgView = (ImageView) findViewById(R.id.encryptedImage);

                final Bitmap[] imageBitmap = {BitmapFactory.decodeFile(imgDecodableString)};

                final ByteBuffer[] buffer = {ByteBuffer.allocate(imageBitmap[0].getByteCount())};
                imageBitmap[0].copyPixelsToBuffer(buffer[0]);

                final int width = imageBitmap[0].getWidth();
                final int height = imageBitmap[0].getHeight();
                Log.d(TAG, width + "x" + height);
                imageBitmap[0].recycle();

                final byte[] originalImageBytes = buffer[0].array();
                Log.d(TAG, "original image bytes:" + originalImageBytes.length + "");

                final long[] start = new long[1];
                final long[] end = new long[1];

                new CryptoTask() {

                    @Override
                    protected byte[][] doCrypto() {
                        start[0] = System.currentTimeMillis();
                        return encryptor.encrypt(originalImageBytes, "password1234");
                    }

                    @Override
                    protected void updateUi(final byte[][] encryptedInfo) {
                        end[0] = System.currentTimeMillis();
                        Log.d(TAG, "encrypted bytes:" + encryptedInfo[2].length + "");
                        Log.d(TAG, "encryption time:" + (end[0] - start[0]) / 1000 + "." + (end[0] - start[0]) % 1000);

                        String saltInBase64 = toBase64(encryptedInfo[0]);
                        String ivInBase64 = toBase64(encryptedInfo[1]);
                        String ciphertextInBase64 = toBase64(encryptedInfo[2]);

                        int diff = encryptedInfo[2].length - originalImageBytes.length;

                        int wi = width, hi = height;
                        byte[] paddedBytes;
                        if (diff > 0) {
                            hi++;
                            paddedBytes = new byte[wi * hi * 4];
                            int i;
                            for (i = 0; i < encryptedInfo[2].length; i++) {
                                paddedBytes[i] = encryptedInfo[2][i];
                            }
                            for (; i < encryptedInfo[2].length - diff + wi * 4; i++) {
                                paddedBytes[i] = 0;
                            }

                            imageBitmap[0] = Bitmap.createBitmap(wi, hi, Bitmap.Config.ARGB_8888);
                            buffer[0].rewind();
                            buffer[0] = ByteBuffer.wrap(paddedBytes);
                            imageBitmap[0].copyPixelsFromBuffer(buffer[0]);
                        }

                        imgView.setImageBitmap(imageBitmap[0]);

                        buffer[0] = ByteBuffer.allocate(imageBitmap[0].getByteCount());
                        imageBitmap[0].copyPixelsToBuffer(buffer[0]);
                        byte[] imageBytes = buffer[0].array();

                        int actualLengthOfImage = imageBytes.length;

                        for (int i = imageBytes.length - 1; i > 0; i -= 8) {
                            boolean isBlockOfZeros = true;
                            for (int t = i; t > i - 8; t--) {
                                if (imageBytes[t] != 0) {
                                    isBlockOfZeros = false;
                                    break;
                                }
                            }
                            if (isBlockOfZeros) {
                                actualLengthOfImage -= 8;
                            } else {
                                break;
                            }
                        }

                        final int w = imageBitmap[0].getWidth();
                        final int h;
                        if (actualLengthOfImage < imageBytes.length) {
                            h = imageBitmap[0].getHeight() - 1;
                        } else {
                            h = imageBitmap[0].getHeight();
                        }

                        byte[] finalImageBytes = new byte[actualLengthOfImage];
                        for (int i = 0; i < actualLengthOfImage; i++) {
                            finalImageBytes[i] = imageBytes[i];
                        }

                        Log.d(TAG, "retrieved bytes from encrypted bytes:" + finalImageBytes.length + "");
                        final byte[][] imageToBeDecrypted = new byte[][]{fromBase64(saltInBase64), fromBase64(ivInBase64), finalImageBytes};


                        new CryptoTask() {

                            @Override
                            protected byte[][] doCrypto() {
                                return encryptor.decrypt(imageToBeDecrypted, "password1234");
                            }

                            protected void updateUi(byte[][] decryptedInfo) {
                                byte[] imageBytes = decryptedInfo[0];

                                Log.d(TAG, "decrypted bytes" + imageBytes.length + "");


                                Bitmap decryptedImage = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

                                ByteBuffer b = ByteBuffer.wrap(decryptedInfo[0]);
                                decryptedImage.copyPixelsFromBuffer(b);


                                ImageView imgView = (ImageView) findViewById(R.id.decryptedImage);
                                imgView.setImageBitmap(decryptedImage);


                            }
                        }.execute();


                    }
                }.execute();


            } else {
                Toast.makeText(this, "You haven't picked Image",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
//            Log.d(TAG, e.getLocalizedMessage());
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_LONG)
                    .show();
        }

    }

    private void encryptImage(byte[] byteArray) {
        for (int i = 0; i < byteArray.length; i += 4) {
            Random rand = new Random();
            int offset = 225;//rand.nextInt(127) - 128;
            byteArray[i] = (byte) (byteArray[i] + offset);
            byteArray[i + 1] = (byte) (byteArray[i + 1] + offset);
            byteArray[i + 2] = (byte) (byteArray[i + 2] + offset);
            byteArray[i + 3] = (byte) (byteArray[i + 3] + offset);
        }
    }

    private void decryptImage(byte[] byteArray) {
        for (int i = 0; i < byteArray.length; i += 4) {
            Random rand = new Random();
            int offset = 225;//rand.nextInt(127) - 128;
            byteArray[i] = (byte) (byteArray[i] - offset);
            byteArray[i + 1] = (byte) (byteArray[i + 1] - offset);
            byteArray[i + 2] = (byte) (byteArray[i + 2] - offset);
            byteArray[i + 3] = (byte) (byteArray[i + 3] - offset);
        }
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
