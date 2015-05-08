author: Rafal Truszkowski
netID: truszko1
date: May 7, 2015


In this project, I evaluated different methods of data encryption and key management. The goal of the project was to find the optimum way to encrypt and store files online. Since a user would use an already existing service for online storage, authentication would be guaranteed by that service. Checking the integrity of the files would have to be done separately. To do that, I used the Advanced Encryption Standard (AES), also referenced as Rijndael, algorithm, Cipher Block Chaining (CBC) and PKCS#5 key derivation technique.

In my online research, I came across two other methods of key derivations: padded password and SHA1PRNG. However, both are very insecure and heavily. For example, "padded password technique limits the range of bytes used for the key to those encoding printable characters, thus effectively reducing the key size (out of 256 possible values for a byte, only 95 are printable ASCII characters)." SHA1PRNG is also insecure as "generating a key is cheap and thus keys based on a password list can be readily generated, facilitating a brute force attack."

In my project, I PKCS#5 key derivation with a user password because this technique makes guessing the encryption key quite hard. "The standard is based on two main ideas: using a salt to protect from table-assisted (pre-computed) dictionary attacks (salting) and using a large iteration count to make the key derivation computationally expensive (key stretching)."

For the data, I used images, as they are easier to present in both encrypted and decrypted forms. Originally, I simply read the file from memory, converted it to a byte array, and encrypted that byte array with AES. However, the resulting encrypted byte array was no longer a valid image and therefore I could not save it as such. Additionally, I needed a way to save the initialization vector and the generated salt alongside the encrypted bytes. I ended up doing as follows:
1)	Encrypt the image bytes
2)	Add IV and salt bytes to the end of the image bytes.
3)	Calculate square root of the total length and take the ceiling of it.
4)	Generate a square bitmap NxN, where N is the result from previous calculation.
5)	Each ith pixel corresponds to ith byte in the byte array. Each pixel’s RBG value is the same, i.e. Color.rgb(bytes[i], bytes[i], bytes[i])
6)	Fill the remaining bytes with random colors. The remaining bytes come from the fact that NxN maybe be less than the length of the byte array
7)	In the last four pixels encode the length of the byte array. Since an integer is 32 bits, that means 4 bytes, ergo four pixels (colored as in the previous steps).
The resulting bitmap was a proper image and therefore could be saved as such.



The encrypted image can only be successfully decrypted if the user provides correct password. Because of PKCS#5, guessing such a password is extremely difficult. Additionally, the way the images are created (by filling them with image bytes, IV, salt and the byte array length) makes it more difficult to decode the appropriate image.

The times of encryption/decryption always took less than two seconds for large-sized images (1900x1200), which means the user would not have to wait a long time. 

![GitHub Logo](/screenshots/1.png)
user enters password

![GitHub Logo](/screenshots/2.png)
main screen

![GitHub Logo](/screenshots/3.png)
user chooses an image to encrypt

![GitHub Logo](/screenshots/4.png)
user confirms the selection

![GitHub Logo](/screenshots/5.png)
the resulting encrypted image

![GitHub Logo](/screenshots/6.png)
the resulting decrypted image

![GitHub Logo](/screenshots/7.png)
when password is wrong

resources:
http://nelenkov.blogspot.com/2012/04/using-password-based-encryption-on.html
https://code.google.com/p/simple-android-instant-messaging-application/
http://www.developer.com/ws/android/encrypting-with-android-cryptography-api.html
https://software.intel.com/en-us/android/articles/sample-code-data-encryption-application
http://www.java2s.com/Code/Java/Security/UsingtheKeyGeneratorclassandshowinghowtocreateaSecretKeySpecfromanencodedkey.htm
