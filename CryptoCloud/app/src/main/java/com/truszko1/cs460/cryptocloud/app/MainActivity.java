package com.truszko1.cs460.cryptocloud.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import java.util.ArrayList;
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
    String file_path;
    private int PICK_IMAGE_REQUEST = 1;
    private ImageView originalImage;
    private Button encryptButton;
    private Button decryptButton;
    private Encryptor encryptor;
    private Bitmap currentBitmap = null;
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
                Intent intent = new Intent();
// Show only images, no videos or anything else
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
// Always show the chooser (if there are multiple options available)
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
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

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {

            final Uri imageUri = data.getData();

            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            LayoutInflater factory = this.getLayoutInflater();
            final View view = factory.inflate(R.layout.image_dialog, null);
            dialog.setView(view);
            Bitmap myBitmap = BitmapFactory.decodeFile(file_path);
            ImageView myImage = (ImageView) view.findViewById(R.id.originalImage);
            myImage.setImageURI(imageUri);
            dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // continue with delete
                    encryptImage(imageUri);
                }
            }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // do nothing
                }
            }).setTitle("Encrypt this image and store it in Dropbox?").show();
        }
    }

    private void encryptImage(Uri imageUri) {
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

        // get the bytes corresponding to our imgage
        final byte[] originalImageBytes = byteBuffer.toByteArray();

        final long[] start = new long[1];
        final long[] finish = new long[1];
        new CryptoTask() {

            @Override
            protected byte[][] doCrypto() {
                start[0] = System.currentTimeMillis();
                // encrypt the image
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
                    Log.d(TAG, "number of encrypted image bytes:" + encryptedInfo[2].length);
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

                                int height = (int) Math.ceil(Math.sqrt(paddedEncryptedBytes.length));
                                int width = height;
                                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                Canvas c = new Canvas(bitmap);
                                int bI = 0;
                                Paint p = new Paint();
                                int i = 0, j = 0;
                                for (i = 0; i < height; i++) {
                                    for (j = 0; j < width; j++) {
                                        // store
                                        int colorInt;
                                        if (bI >= paddedEncryptedBytes.length) {
                                            Random random = new Random();
                                            int randomInt = random.nextInt(255);
                                            colorInt = Color.rgb(randomInt, randomInt, randomInt);
                                        } else {
                                            int color = paddedEncryptedBytes[bI++] + 128;
                                            colorInt = Color.rgb(color, color, color);
                                        }
                                        // Set the colour on the Paint;
                                        p.setColor(colorInt);
                                        // Draw the pixel;
                                        c.drawPoint(j, i, p);
                                    }
                                }

                                ByteBuffer b = ByteBuffer.allocate(4);
                                b.putInt(paddedEncryptedBytes.length);

                                Log.d(TAG, paddedEncryptedBytes.length + "");

                                byte[] result = b.array();
                                p.setColor(Color.rgb(result[3] + 128, result[3] + 128, result[3] + 128));
                                c.drawPoint(--j, i - 1, p);
                                p.setColor(Color.rgb(result[2] + 128, result[2] + 128, result[2] + 128));
                                c.drawPoint(--j, i - 1, p);
                                p.setColor(Color.rgb(result[1] + 128, result[1] + 128, result[1] + 128));
                                c.drawPoint(--j, i - 1, p);
                                p.setColor(Color.rgb(result[0] + 128, result[0] + 128, result[0] + 128));
                                c.drawPoint(--j, i - 1, p);


                                file_path = Environment.getExternalStorageDirectory().getAbsolutePath();
                                File dir = new File(file_path);
                                File file;
                                try {
                                    // Create a File Object;
                                    file = new File(dir, "meh.png");
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
                                } catch (Exception e) {
                                }

                                originalImage.setImageBitmap(bitmap);

                            }
                        }).setNegativeButton("no",
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
        file_path = Environment.getExternalStorageDirectory().getAbsolutePath();
        File dir = new File(file_path);
        File file = new File(dir, "meh.png");
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
        Bitmap bmp = BitmapFactory.decodeByteArray(encryptedImageInfo, 0, encryptedImageInfo.length);

        int numberofpixels = bmp.getWidth() * bmp.getHeight();
        int[] pixels = new int[numberofpixels];
        bmp.getPixels(pixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());


        byte[] imageBytesLength = new byte[4];
        imageBytesLength[3] = (byte) (Color.red(pixels[numberofpixels - 1]) - 128);
        imageBytesLength[2] = (byte) (Color.red(pixels[numberofpixels - 2]) - 128);
        imageBytesLength[1] = (byte) (Color.red(pixels[numberofpixels - 3]) - 128);
        imageBytesLength[0] = (byte) (Color.red(pixels[numberofpixels - 4]) - 128);

        ByteBuffer wrapped = ByteBuffer.wrap(imageBytesLength); // big-endian by default

        assert encryptedImageInfo != null;
        int totalUsefulBytes = wrapped.getInt();
        int ivNumberOfBytes = 16;
        int saltNumberOfBytes = 8;
        int imageNumberOfBytes = totalUsefulBytes - saltNumberOfBytes - ivNumberOfBytes;


//        byte[] ivBytes = Arrays.copyOfRange(encryptedImageInfo, totalUsefulBytes - ivNumberOfBytes, totalUsefulBytes);
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

    private byte[] retrieveBytesFromBitmap(int[] pixels, int beginning, int end) {
        byte[] retval = new byte[end - beginning];
        int idx = 0;
        for (int i = beginning; i < end; i++) {
            retval[idx++] = (byte) (Color.red(pixels[i]) - 128);
        }
        return retval;
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
