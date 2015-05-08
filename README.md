author: Rafal Truszkowski
netID: truszko1
date: May 7, 2015


In this project, I evaluated different methods of data encryption and key management. The goal of the project was to find the optimum way to encrypt and store files online. Since a user would use an already existing service for online storage, authentication would be guaranteed by that service. Checking the integrity of the files would have to be done separately. To do that, I used the Advanced Encryption Standard (AES), also referenced as Rijndael, algorithm, Cipher Block Chaining (CBC) and PKCS#5 key derivation technique.

In my online research, I came across two other methods of key derivations: padded password and SHA1PRNG. However, both are very insecure 