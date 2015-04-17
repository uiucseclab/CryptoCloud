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
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import javax.crypto.SecretKey;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private final Encryptor PBKDF2_ENCRYPTOR = new Encryptor() {

        private SecretKey deriveKey(String password, byte[] salt) {
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

    private void encryptImage() {

        final Random random = new Random();
        final int count = imagesPath.size();
        int number = random.nextInt(count);
        String path = imagesPath.get(number);
        if (currentBitmap != null) {
            currentBitmap.recycle();
        }
        currentBitmap = BitmapFactory.decodeFile(path);
        originalImage.setImageBitmap(currentBitmap);

        File file = new File(path);
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the byteBuffer
        int len;
        try {
            assert fileInputStream != null;
            while ((len = fileInputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // and then we can return your byte array.
        final byte[] originalImageBytes = byteBuffer.toByteArray();

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


                ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
                try {
                    bytesStream.write(encryptedInfo[2]); // image bytes
                    Log.d(TAG, "number of encrypted image bytes" + encryptedInfo[2].length);
                    bytesStream.write(encryptedInfo[0]); // salt
                    bytesStream.write(encryptedInfo[1]); // iv
                } catch (IOException e) {
                    e.printStackTrace();
                }


                paddedEncryptedBytes = bytesStream.toByteArray();

                Log.d(TAG, "paddedEncryptedBytes length:" + paddedEncryptedBytes.length);

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                alertDialogBuilder.setMessage("save the image?");
                alertDialogBuilder.setPositiveButton("yes",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                String file_path = Environment.getExternalStorageDirectory().getAbsolutePath();
                                File dir = new File(file_path);
                                File file = new File(dir, "meh.png");
                                BufferedOutputStream bos;
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

        byte[] encryptedImageInfo = null;
        // read file
        File file = new File("/storage/emulated/0/meh.png");
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
            } catch (IOException ignored) {
            }
        }

        assert encryptedImageInfo != null;
        int totalBytes = encryptedImageInfo.length;
        int ivNumberOfBytes = 16;
        int saltNumberOfBytes = 8;
        int imageNumberOfBytes = totalBytes - saltNumberOfBytes - ivNumberOfBytes;
        byte[] ivBytes = Arrays.copyOfRange(encryptedImageInfo, totalBytes - ivNumberOfBytes, totalBytes);
        totalBytes -= ivNumberOfBytes;
        byte[] saltBytes = Arrays.copyOfRange(encryptedImageInfo, totalBytes - saltNumberOfBytes, totalBytes);
        totalBytes -= saltNumberOfBytes;
        byte[] imageBytes = Arrays.copyOfRange(encryptedImageInfo, totalBytes - imageNumberOfBytes, totalBytes);

        Log.d(TAG, "number of decrypted image bytes" + imageBytes.length);

        final byte[][] imageToBeDecrypted = new byte[][]{saltBytes, ivBytes, imageBytes};


        new CryptoTask() {

            @Override
            protected byte[][] doCrypto() {
                return encryptor.decrypt(imageToBeDecrypted, "password1234");
            }

            protected void updateUi(byte[][] decryptedInfo) {
                byte[] imageBytes = decryptedInfo[0];

                Log.d(TAG, "decrypted bytes" + imageBytes.length + "");

                Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                ImageView imgView = (ImageView) findViewById(R.id.decryptedImage);
                imgView.setImageBitmap(bmp);

                FileOutputStream out = null;
                try {
                    out = new FileOutputStream("/storage/emulated/0/output.jpg");
                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, out); // bmp is your Bitmap instance
                    // PNG is a lossless format, the compression factor (100) is ignored
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


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
