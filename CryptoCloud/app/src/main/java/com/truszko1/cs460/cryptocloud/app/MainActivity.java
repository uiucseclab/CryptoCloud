package com.truszko1.cs460.cryptocloud.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.ByteBuffer;

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
    byte[] imageSaltAndIvBytes;
    String file_path;
    private int PICK_IMAGE_TO_ENCRYPT_REQUEST = 1;
    private Button encryptButton;
    private Button decryptButton;
    private Encryptor encryptor;
    private String password;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setProgressBarIndeterminateVisibility(false);

        Intent intent = getIntent();
        password = intent.getStringExtra("password");

        encryptor = PBKDF2_ENCRYPTOR;

        encryptButton = (Button) findViewById(R.id.encrypt_button);
        decryptButton = (Button) findViewById(R.id.decrypt_button);

        encryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                // Show only images, no videos or anything else
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                // Always show the chooser (if there are multiple options available)
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_TO_ENCRYPT_REQUEST);
            }
        });

        decryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                decryptImage();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_TO_ENCRYPT_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {

            final Uri imageUri = data.getData();

            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            LayoutInflater factory = this.getLayoutInflater();
            final View view = factory.inflate(R.layout.pick_image_dialog, null);
            dialog.setView(view);
            Bitmap myBitmap = BitmapFactory.decodeFile(file_path);
            ImageView myImage = (ImageView) view.findViewById(R.id.image);
            myImage.setImageURI(imageUri);
            dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    encryptImage(imageUri);
                }
            }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
            }).setTitle("Encrypt and store this image?").show();
        }
    }

    private void encryptImage(Uri imageUri) {
        ByteArrayOutputStream byteBuffer = writeFileToBuffer(imageUri);

        // get the bytes corresponding to our imgage
        final byte[] originalImageBytes = byteBuffer.toByteArray();

        Log.d(TAG, "number of original image bytes" + originalImageBytes.length);
        final long[] start = new long[1];
        final long[] finish = new long[1];
        new CryptoTask() {

            @Override
            protected byte[][] doCrypto() {
                start[0] = System.currentTimeMillis();
                // encrypt the image
                return encryptor.encrypt(originalImageBytes, password);
            }

            @Override
            protected void updateUi(final byte[][] encryptedInfo) {
                finish[0] = System.currentTimeMillis();
                Log.d(TAG, "encryption time:" + (finish[0] - start[0]));
                Log.d(TAG, "salt size:" + encryptedInfo[0].length);
                Log.d(TAG, "iv size:" + encryptedInfo[1].length);

                ByteArrayOutputStream bytesStream = UtilityFunctions.writeEncryptedInfoToBuffer(encryptedInfo);

                imageSaltAndIvBytes = bytesStream.toByteArray();

                Log.d(TAG, "imageSaltAndIvBytes length:" + imageSaltAndIvBytes.length);

                int outputImageHeight = (int) Math.ceil(Math.sqrt(imageSaltAndIvBytes.length));
                int outputImageWidth = outputImageHeight;
                Bitmap bitmap = Bitmap.createBitmap(outputImageWidth, outputImageHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                UtilityFunctions.paintBytesOntoCanvas(outputImageHeight, outputImageWidth, canvas, imageSaltAndIvBytes);

                file_path = Environment.getExternalStorageDirectory().getAbsolutePath();
                File dir = new File(file_path);
                File file;
                try {
                    // Create a File Object;
                    file = new File(dir, "encoded.png");
                    // Ensure that the file exists and can be written to;
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    // Create a FileOutputStream Object;
                    FileOutputStream fos = new FileOutputStream(file);
                    // Write the Bitmap to the File, 100 is max quality but
                    //        it is ignored for PNG since that is lossless;
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    // Clear the output stream;
                    fos.flush();
                    // Close the output stream;
                    fos.close();
                } catch (Exception ignored) {
                }
                ImageView imgView = (ImageView) findViewById(R.id.image);
                imgView.setImageBitmap(bitmap);


            }


        }.execute();
    }

    private ByteArrayOutputStream writeFileToBuffer(Uri imageUri) {
        // open the file
        InputStream fileInputStream = null;
        try {
            fileInputStream = getContentResolver().openInputStream(imageUri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // read the file
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
        return byteBuffer;
    }


    private void decryptImage() {

        byte[] encryptedImageInfo = readFile();
        Bitmap bmp = BitmapFactory.decodeByteArray(encryptedImageInfo, 0, encryptedImageInfo.length);

        int numberOfPixels = bmp.getWidth() * bmp.getHeight();
        int[] pixels = new int[numberOfPixels];
        bmp.getPixels(pixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());


        ByteBuffer wrapped = retrieveNumberOfUsefulBytes(numberOfPixels, pixels);

        int totalUsefulBytes = wrapped.getInt();
        int ivNumberOfBytes = 16;
        int saltNumberOfBytes = 8;
        int imageNumberOfBytes = totalUsefulBytes - saltNumberOfBytes - ivNumberOfBytes;

        byte[] ivBytes = retrieveBytesFromBitmap(pixels, totalUsefulBytes - ivNumberOfBytes, totalUsefulBytes);
        totalUsefulBytes -= ivNumberOfBytes;
        byte[] saltBytes = retrieveBytesFromBitmap(pixels, totalUsefulBytes - saltNumberOfBytes, totalUsefulBytes);
        totalUsefulBytes -= saltNumberOfBytes;
        byte[] imageBytes = retrieveBytesFromBitmap(pixels, totalUsefulBytes - imageNumberOfBytes, totalUsefulBytes);

        Log.d(TAG, "number of decrypted image bytes" + imageBytes.length);

        final byte[][] imageToBeDecrypted = new byte[][]{saltBytes, ivBytes, imageBytes};


        new CryptoTask() {

            @Override
            protected byte[][] doCrypto() {
                return encryptor.decrypt(imageToBeDecrypted, password);
            }

            protected void updateUi(byte[][] decryptedInfo) {
                if (decryptedInfo == null) {
                    Toast.makeText(MainActivity.this,
                            "Could not decode the image.", Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                byte[] imageBytes = decryptedInfo[0];

                Log.d(TAG, "decrypted bytes" + imageBytes.length + "");

                Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                file_path = Environment.getExternalStorageDirectory().getAbsolutePath();
                File dir = new File(file_path);
                File file;
                try {
                    // Create a File Object;
                    file = new File(dir, "decoded.png");
                    // Ensure that the file exists and can be written to;
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    // Create a FileOutputStream Object;
                    FileOutputStream fos = new FileOutputStream(file);
                    // Write the Bitmap to the File, 100 is max quality but
                    //        it is ignored for PNG since that is lossless;
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    // Clear the output stream;
                    fos.flush();
                    // Close the output stream;
                    fos.close();
                } catch (Exception ignored) {
                }

                ImageView imgView = (ImageView) findViewById(R.id.image);
                imgView.setImageBitmap(bmp);

            }
        }.execute();

    }

    private ByteBuffer retrieveNumberOfUsefulBytes(int numberOfPixels, int[] pixels) {
        byte[] imageBytesLength = new byte[4];
        imageBytesLength[3] = (byte) (Color.red(pixels[numberOfPixels - 1]) - 128);
        imageBytesLength[2] = (byte) (Color.red(pixels[numberOfPixels - 2]) - 128);
        imageBytesLength[1] = (byte) (Color.red(pixels[numberOfPixels - 3]) - 128);
        imageBytesLength[0] = (byte) (Color.red(pixels[numberOfPixels - 4]) - 128);

        return ByteBuffer.wrap(imageBytesLength);
    }

    private byte[] readFile() {
        byte[] encryptedImageInfo = null;
        file_path = Environment.getExternalStorageDirectory().getAbsolutePath();
        File dir = new File(file_path);
        File file = new File(dir, "encoded.png");
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
        return encryptedImageInfo;
    }

    private byte[] retrieveBytesFromBitmap(int[] pixels, int beginning, int end) {
        byte[] retval = new byte[end - beginning];
        int idx = 0;
        for (int i = beginning; i < end; i++) {
            retval[idx++] = (byte) (Color.red(pixels[i]) - 128);
        }
        return retval;
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

