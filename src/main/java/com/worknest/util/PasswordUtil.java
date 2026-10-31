
package com.worknest.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

public class PasswordUtil {

    public static String hashPassword(String password) {
        Argon2 argon2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id
        );

        char[] passwordChars = password.toCharArray();

        try {
            return argon2.hash(2, 19456, 1, passwordChars);
        } finally {
            argon2.wipeArray(passwordChars);
        }
    }

    public static boolean verifyPassword(String password, String passwordHash) {

        Argon2 argon2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id
        );

        char[] passwordChars = password.toCharArray();

        try {
            return argon2.verify(passwordHash, passwordChars);
        } finally {
            argon2.wipeArray(passwordChars);
        }
    }
}
