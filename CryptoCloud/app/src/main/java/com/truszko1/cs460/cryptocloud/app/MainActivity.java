package com.truszko1.cs460.cryptocloud.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.util.Random;

public class MainActivity extends Activity implements OnClickListener,
        OnItemSelectedListener {

    public static final int COLOR_MIN = 0x00;
    public static final int COLOR_MAX = 0xFF;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String MESSAGE = "Secret message!";
    private static final int PBKDF2_ENC_IDX = 0;
    private static String DELIMITER = "]";
    private final Encryptor PBKDF2_ENCRYPTOR = new Encryptor() {

        @Override
        public SecretKey deriveKey(String password, byte[] salt) {
            return Crypto.deriveKeyPbkdf2(salt, password);
        }

        @Override
        public byte[][] encrypt(byte[] plaintext, String password) {
            byte[] salt = Crypto.generateSalt();
            key = deriveKey(password, salt);
            Log.d(TAG, "Generated key: " + getRawKey());


            return Crypto.encrypt(plaintext, key, salt);
        }

        @Override
        public byte[][] decrypt(byte[][] encryptedInfo, String password) {
            return Crypto.decryptPbkdf2(encryptedInfo, password);
        }
    };
    private Spinner derivationMethodSpinner;
    private EditText passwordText;
    private TextView encryptedText;
    private TextView decryptedText;
    private TextView rawKeyText;
    private Button listAlgorithmsButton;
    private Button encryptButton;
    private Button decryptButton;
    private Button clearButton;
    private Encryptor encryptor;

    public static Bitmap applyFleaEffect(Bitmap source, byte[] byteArray) {
        // get image size
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        // get pixel array from source
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        // a random object
        Random random = new Random();
        int index = 0;
        // iteration through pixels
        Log.d(TAG, "image size:" + width + "x" + height + "=" + width * height);
        Log.d(TAG, "bytes array size:" + byteArray.length);
        Log.d(TAG, "source image byte count:" + source.getByteCount());


        int blocksize = 5;
        for (int y = 0; y < height; y += blocksize) {
            for (int x = 0; x < width; x += blocksize) {
                // get current index in 2D-matrix
                index = y * width + x;
                int delta = byteArray[index] + 128; // change the range of byte (-128..127) to (0..255)

                int color = Color.rgb((255 + delta) % 255, (255 + delta) % 255, (255 + delta) % 255);

                for (int row = 0; row < blocksize; row++) {
                    for (int column = 0; column < blocksize; column++) {
                        int idx = index + column + width * row;
                        if (idx < width * height) {
                            pixels[idx] = color;
                        }
                    }
                }
            }
        }
        // output bitmap
        Bitmap bmOut = Bitmap.createBitmap(width, height, source.getConfig());
        bmOut.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmOut;
    }

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

        derivationMethodSpinner = findById(R.id.derivation_method_spinner);
        derivationMethodSpinner.setOnItemSelectedListener(this);
        encryptor = PBKDF2_ENCRYPTOR;
        derivationMethodSpinner.setSelection(0);

        passwordText = findById(R.id.password_text);

        encryptedText = findById(R.id.encrypted_text);
        decryptedText = findById(R.id.decrypted_text);
        rawKeyText = findById(R.id.raw_key_text);

        listAlgorithmsButton = findById(R.id.list_algs_button);
        listAlgorithmsButton.setOnClickListener(this);
        listAlgorithmsButton.setVisibility(View.GONE);
        encryptButton = findById(R.id.encrypt_button);
//        encryptButton.setOnClickListener(this);

        encryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                // Create intent to Open Image applications like Gallery, Google Photos
                Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                // Start the Intent
                startActivityForResult(galleryIntent, 0);
            }
        });

        decryptButton = findById(R.id.decrypt_button);
        decryptButton.setOnClickListener(this);
        clearButton = findById(R.id.clear_button);
        clearButton.setOnClickListener(this);
    }

    @SuppressWarnings("unchecked")
    private <T> T findById(int id) {
        return (T) findViewById(id);
    }

    private void toggleControls(boolean enable) {
        derivationMethodSpinner.setEnabled(enable);
        derivationMethodSpinner.setEnabled(enable);
        passwordText.setEnabled(enable);
        encryptButton.setEnabled(enable);
        decryptButton.setEnabled(enable);
        clearButton.setEnabled(enable);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            // When an Image is picked
            if (requestCode == 0 && resultCode == RESULT_OK
                    && null != data) {
                // Get the Image from data

                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                // Get the cursor
                Cursor cursor = getContentResolver().query(selectedImage,
                        filePathColumn, null, null, null);
                // Move to first row
                cursor.moveToFirst();

                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                String imgDecodableString = cursor.getString(columnIndex);
                cursor.close();
                final ImageView imgView = (ImageView) findViewById(R.id.imageView);

                // 1. get the original image from disk
                final Bitmap[] imageBitmap = {BitmapFactory.decodeFile(imgDecodableString)};

                // 2. convert the image into a byte array
                final ByteBuffer[] buffer = {ByteBuffer.allocate(imageBitmap[0].getByteCount())};
                imageBitmap[0].copyPixelsToBuffer(buffer[0]);

                final int width = imageBitmap[0].getWidth();
                final int height = imageBitmap[0].getHeight();
                imageBitmap[0].recycle();

                final byte[] originalImageBytes = buffer[0].array();
                Log.d(TAG, "original image bytes:" + originalImageBytes.length + "");
//
//                // 3. encrypt the byte array
//                long starttime = System.currentTimeMillis();
////                encryptImage(originalImageBytes); // add 125 to each byte element
//                long endtime = System.currentTimeMillis();
//                Log.d(TAG, "creating image time:" + (endtime - starttime) / 1000 + "." + (endtime - starttime) % 1000);
//                // 4. create a new Bitmap from the encrypted byte array
//                imageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//                ByteBuffer outputbuffer = ByteBuffer.wrap(originalImageBytes);
//                imageBitmap.copyPixelsFromBuffer(outputbuffer);

//                // now, we read the image from disk and try to recover it!
//                // imageBitmap will contain that image
//
//                // 5. convert the bitmap to byte array
//                buffer = ByteBuffer.allocate(imageBitmap.getByteCount());
//                imageBitmap.copyPixelsToBuffer(buffer);
//                width = imageBitmap.getWidth();
//                height = imageBitmap.getHeight();
//                imageBitmap.recycle();
//                final byte[] finalImageBytes = buffer.array();
//
//                // 6. decrypt image
//                starttime = System.currentTimeMillis();
////                decryptImage(finalImageBytes);
//                endtime = System.currentTimeMillis();
//                Log.d(TAG, "creating image time:" + (endtime - starttime) / 1000 + "." + (endtime - starttime) % 1000);
//
//                // 7. create a new Bitmap from the decrypted byte array
//                imageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//                outputbuffer = ByteBuffer.wrap(finalImageBytes);
//                imageBitmap.copyPixelsFromBuffer(outputbuffer);
//
//                // 8. show the image
//                imgView.setImageBitmap(imageBitmap);

                final long[] start = new long[1];
                final long[] end = new long[1];

                new CryptoTask() {

                    @Override
                    protected byte[][] doCrypto() {
                        start[0] = System.currentTimeMillis();
                        return encryptor.encrypt(originalImageBytes, "password");
                    }

                    @Override
                    protected void updateUi(final byte[][] encryptedInfo) {
                        end[0] = System.currentTimeMillis();
                        Log.d(TAG, "encrypted bytes:" + encryptedInfo[2].length + "");
//                        Log.d(TAG, "encryption time:" + (end[0] - start[0]) / 1000 + "." + (end[0] - start[0]) % 1000);

                        rawKeyText.setText(encryptor.getRawKey());
                        encryptedText.setText("meh");

                        String saltInBase64 = toBase64(encryptedInfo[0]);
                        String ivInBase64 = toBase64(encryptedInfo[1]);
                        String ciphertextInBase64 = toBase64(encryptedInfo[2]);

//                        Log.d(TAG, "salt + iv:" + saltInBase64 + "     " + ivInBase64);
//                        Log.d(TAG, "cipherBytes:" + ciphertextInBase64);

                        // display the encrypted image
                        imageBitmap[0] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        buffer[0].rewind();
                        buffer[0] = ByteBuffer.wrap(encryptedInfo[2]);
                        imageBitmap[0].copyPixelsFromBuffer(buffer[0]);

                        // 8. show the image
                        imgView.setImageBitmap(imageBitmap[0]);

                        // decrypt
                        buffer[0] = ByteBuffer.allocate(imageBitmap[0].getByteCount());
                        imageBitmap[0].copyPixelsToBuffer(buffer[0]);
                        final int w = imageBitmap[0].getWidth();
                        final int h = imageBitmap[0].getHeight();
//                        imageBitmap[0].recycle();
                        final byte[] finalImageBytes = buffer[0].array();
                        Log.d(TAG, "retrieved bytes from encrypted bytes:" + finalImageBytes.length + "");
                        final byte[][] imageToBeDecrypted = new byte[][]{encryptedInfo[0], encryptedInfo[1], encryptedInfo[2]};


                        new CryptoTask() {

                            @Override
                            protected byte[][] doCrypto() {
                                return encryptor.decrypt(imageToBeDecrypted, "password");
                            }

                            protected void updateUi(byte[][] decryptedInfo) {
                                byte[] imageBytes = decryptedInfo[0];
//                                rawKeyText.setText(encryptor.getRawKey());
//                                decryptedText.setText(plaintext);

                                Log.d(TAG, "decrypted bytes" + imageBytes.length + "");


                                Bitmap decryptedImage = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

                                ByteBuffer b = ByteBuffer.wrap(decryptedInfo[0]);
                                decryptedImage.copyPixelsFromBuffer(b);


                                ImageView imgView = (ImageView) findViewById(R.id.imageView);
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

    @Override
    public void onClick(View v) {
//        if (v.getId() == listAlgorithmsButton.getId()) {
//            Crypto.listAlgorithms("PB");
//        } else if (v.getId() == encryptButton.getId()) {
//            final String password = passwordText.getText().toString().trim();
//            if (password.length() == 0) {
//                Toast.makeText(this, "Please enter a password.",
//                        Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            final String plaintext = String.format("%s %s",
//                    derivationMethodSpinner.getSelectedItem().toString(),
//                    MESSAGE);
//
//            new CryptoTask() {
//
//                @Override
//                protected byte[] doCrypto() {
//                    String retval = null;
//                    try {
//                        retval = encryptor.encrypt(plaintext.getBytes("UTF-8"), password);
//                    } catch (UnsupportedEncodingException e) {
//                        e.printStackTrace();
//                    }
//                    return retval;
//                }
//
//                @Override
//                protected void updateUi(String ciphertext) {
//                    rawKeyText.setText(encryptor.getRawKey());
//                    encryptedText.setText(ciphertext);
//                }
//            }.execute();
//        } else if (v.getId() == decryptButton.getId()) {
//            final String password = passwordText.getText().toString().trim();
//            if (password.length() == 0) {
//                Toast.makeText(this, "Please enter a password.",
//                        Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            final String ciphertext = encryptedText.getText().toString().trim();
//            if (ciphertext.length() == 0) {
//                Toast.makeText(this, "No text to decrypt.", Toast.LENGTH_SHORT)
//                        .show();
//                return;
//            }
//
//            new CryptoTask() {
//
//                @Override
//                protected byte[] doCrypto() {
//                    return encryptor.decrypt(ciphertext, password);
//                }
//
//                protected void updateUi(String plaintext) {
//                    rawKeyText.setText(encryptor.getRawKey());
//                    decryptedText.setText(plaintext);
//
//                    Bitmap imageBitmap = null;
//                    try {
//                        byte[] bytes = plaintext.getBytes("UTF-8");
//                        imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//                    } catch (UnsupportedEncodingException e) {
//                        e.printStackTrace();
//                    }
//                    ImageView imgView = (ImageView) findViewById(R.id.imageView);
//                    imgView.setImageBitmap(imageBitmap);
//
//
//                }
//            }.execute();
//        } else if (v.getId() == clearButton.getId()) {
//            clear();
//        }
    }

    private void clear() {
        encryptedText.setText("");
        decryptedText.setText("");
        rawKeyText.setText("");
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos,
                               long id) {
        clear();

        switch (pos) {
            case PBKDF2_ENC_IDX:
                encryptor = PBKDF2_ENCRYPTOR;
                break;
            default:
                throw new IllegalArgumentException("Invalid option selected");
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    abstract class Encryptor {
        SecretKey key;

        abstract SecretKey deriveKey(String passpword, byte[] salt);

        abstract byte[][] encrypt(byte[] plaintext, String password);

        abstract byte[][] decrypt(byte[][] ciphertext, String password);

        String getRawKey() {
            if (key == null) {
                return null;
            }

            return Crypto.toHex(key.getEncoded());
        }
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
