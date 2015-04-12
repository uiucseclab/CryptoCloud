package com.truszko1.cs460.cryptobox;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

public class MainActivity extends Activity implements OnClickListener,
        OnItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String MESSAGE = "Secret message!";

    private static final int PADDING_ENC_IDX = 0;
    private static final int SHA1PRNG_ENC_IDX = 1;
    private static final int PBKDF2_ENC_IDX = 2;
    private static final int PKCS12_ENC_IDX = 3;
    private final Encryptor PADDING_ENCRYPTOR = new Encryptor() {

        @Override
        public SecretKey deriveKey(String password, byte[] salt) {
            return Crypto.deriveKeyPad(password);
        }

        @Override
        public String encrypt(byte[] plaintext, String password) {
            key = deriveKey(password, null);
            Log.d(TAG, "Generated key: " + getRawKey());

            return Crypto.encrypt(plaintext, key, null);
        }

        @Override
        public String decrypt(String ciphertext, String password) {
            SecretKey key = deriveKey(password, null);

            return Crypto.decryptNoSalt(ciphertext, key);
        }
    };
    private final Encryptor SHA1PRNG_ENCRYPTOR = new Encryptor() {

        @Override
        public SecretKey deriveKey(String password, byte[] salt) {
            return Crypto.deriveKeySha1prng(password);
        }

        @Override
        public String encrypt(byte[] plaintext, String password) {
            key = deriveKey(password, null);
            Log.d(TAG, "Generated key: " + getRawKey());

            return Crypto.encrypt(plaintext, key, null);
        }

        @Override
        public String decrypt(String ciphertext, String password) {
            SecretKey key = deriveKey(password, null);

            return Crypto.decryptNoSalt(ciphertext, key);
        }
    };
    private final Encryptor PKCS12_ENCRYPTOR = new Encryptor() {

        @Override
        public SecretKey deriveKey(String password, byte[] salt) {
            return Crypto.deriveKeyPkcs12(salt, password);
        }

        @Override
        public String encrypt(byte[] plaintext, String password) {
            byte[] salt = Crypto.generateSalt();
            key = deriveKey(password, salt);
            Log.d(TAG, "Generated key: " + getRawKey());

            return Crypto.encryptPkcs12(plaintext, key, salt);
        }

        @Override
        public String decrypt(String ciphertext, String password) {
            return Crypto.decryptPkcs12(ciphertext, password);
        }
    };
    private final Encryptor PBKDF2_ENCRYPTOR = new Encryptor() {

        @Override
        public SecretKey deriveKey(String password, byte[] salt) {
            return Crypto.deriveKeyPbkdf2(salt, password);
        }

        @Override
        public String encrypt(byte[] plaintext, String password) {
            byte[] salt = Crypto.generateSalt();
            key = deriveKey(password, salt);
            Log.d(TAG, "Generated key: " + getRawKey());


            return Crypto.encrypt(plaintext, key, salt);
        }

        @Override
        public String decrypt(String ciphertext, String password) {
            return Crypto.decryptPbkdf2(ciphertext, password);
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
        encryptor = PADDING_ENCRYPTOR;
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
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
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
                ImageView imgView = (ImageView) findViewById(R.id.imageView);
                // Set the Image in ImageView after decoding the String
                Bitmap imageBitmap = BitmapFactory
                        .decodeFile(imgDecodableString);

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                final byte[] byteArray = stream.toByteArray();


                // save the file
                MediaStore.Images.Media.insertImage(getContentResolver(), BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length), "meh", "nlah");

                //TODO: change the encryption method so that it handles the byte arrays, and not strings
                //TODO: save the encrypted byte array to file
                //TODO: read the byte array from file, decrypt it and...pray for the best!
                imgView.setImageBitmap(imageBitmap);


                new CryptoTask() {

                    @Override
                    protected String doCrypto() {
                        return encryptor.encrypt(byteArray, "password");
                    }

                    @Override
                    protected void updateUi(String ciphertext) {
                        rawKeyText.setText(encryptor.getRawKey());
                        Log.d("TAG", ciphertext);


                        // try to decrypt and show the image!


                        encryptedText.setText(ciphertext);
                    }
                }.execute();


            } else {
                Toast.makeText(this, "You haven't picked Image",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_LONG)
                    .show();
        }

    }


    @Override
    public void onClick(View v) {
        if (v.getId() == listAlgorithmsButton.getId()) {
            Crypto.listAlgorithms("PB");
        } else if (v.getId() == encryptButton.getId()) {
            final String password = passwordText.getText().toString().trim();
            if (password.length() == 0) {
                Toast.makeText(this, "Please enter a password.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            final String plaintext = String.format("%s %s",
                    derivationMethodSpinner.getSelectedItem().toString(),
                    MESSAGE);

            new CryptoTask() {

                @Override
                protected String doCrypto() {
                    String retval = null;
                    try {
                        retval = encryptor.encrypt(plaintext.getBytes("UTF-8"), password);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    return retval;
                }

                @Override
                protected void updateUi(String ciphertext) {
                    rawKeyText.setText(encryptor.getRawKey());
                    encryptedText.setText(ciphertext);
                }
            }.execute();
        } else if (v.getId() == decryptButton.getId()) {
            final String password = passwordText.getText().toString().trim();
            if (password.length() == 0) {
                Toast.makeText(this, "Please enter a password.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            final String ciphertext = encryptedText.getText().toString().trim();
            if (ciphertext.length() == 0) {
                Toast.makeText(this, "No text to decrypt.", Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            new CryptoTask() {

                @Override
                protected String doCrypto() {
                    return encryptor.decrypt(ciphertext, password);
                }

                protected void updateUi(String plaintext) {
                    rawKeyText.setText(encryptor.getRawKey());
                    decryptedText.setText(plaintext);

                    Bitmap imageBitmap = null;
                    try {
                        byte[] bytes = plaintext.getBytes("UTF-8");
                        imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    ImageView imgView = (ImageView) findViewById(R.id.imageView);
                    imgView.setImageBitmap(imageBitmap);


                }
            }.execute();
        } else if (v.getId() == clearButton.getId()) {
            clear();
        }
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
            case PADDING_ENC_IDX:
                encryptor = PADDING_ENCRYPTOR;
                break;
            case SHA1PRNG_ENC_IDX:
                encryptor = SHA1PRNG_ENCRYPTOR;
                break;
            case PBKDF2_ENC_IDX:
                encryptor = PBKDF2_ENCRYPTOR;
                break;
            case PKCS12_ENC_IDX:
                encryptor = PKCS12_ENCRYPTOR;
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

        abstract String encrypt(byte[] plaintext, String password);

        abstract String decrypt(String ciphertext, String password);

        String getRawKey() {
            if (key == null) {
                return null;
            }

            return Crypto.toHex(key.getEncoded());
        }
    }

    abstract class CryptoTask extends AsyncTask<Void, Void, String> {

        Exception error;

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);
            toggleControls(false);
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                return doCrypto();
            } catch (Exception e) {
                error = e;
                Log.e(TAG, "Error: " + e.getMessage(), e);

                return null;
            }
        }

        protected abstract String doCrypto();

        @Override
        protected void onPostExecute(String result) {
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

        protected abstract void updateUi(String result);
    }
}
