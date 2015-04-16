package com.truszko1.cs460.cryptocloud.app;

import android.app.Activity;
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

    private void encryptImage() {
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


    private void decryptImage() {
        ImageView encryptedImage = (ImageView) findViewById(R.id.encryptedImage);
        Bitmap bitmap = ((BitmapDrawable) encryptedImage.getDrawable()).getBitmap();

        ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(buffer);
        byte[] imageBytes = buffer.array();

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

        final int width = bitmap.getWidth();
        final int height;
        if (actualLengthOfImage < imageBytes.length) {
            height = bitmap.getHeight() - 1;
        } else {
            height = bitmap.getHeight();
        }

        byte[] finalImageBytes = new byte[actualLengthOfImage];
        for (int i = 0; i < actualLengthOfImage; i++) {
            finalImageBytes[i] = imageBytes[i];
        }

        final byte[][] imageToBeDecrypted = new byte[][]{fromBase64(((TextView) findViewById(R.id.salt)).getText().toString()), fromBase64(((TextView) findViewById(R.id.iv)).getText().toString()), finalImageBytes};


        new CryptoTask() {

            @Override
            protected byte[][] doCrypto() {
                return encryptor.decrypt(imageToBeDecrypted, "password1234");
            }

            protected void updateUi(byte[][] decryptedInfo) {
                byte[] imageBytes = decryptedInfo[0];

                Log.d(TAG, "decrypted bytes" + imageBytes.length + "");


                Bitmap decryptedImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

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
